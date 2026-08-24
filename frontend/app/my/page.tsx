"use client";
import {
  ApiError,
  changePassword,
  checkNicknameAvailability,
  createAddress,
  deleteAddress,
  getAddresses,
  getMe,
  setDefaultAddress,
  updateAddress,
  updateProfile,
  verifyPassword,
  withdraw,
  type UserAddress,
  type UserResponse,
} from "@/lib/api";
import { formatPhone } from "@/components/AddressForm";
import { embedAddressSearch } from "@/lib/daumPostcode";
import { useStore } from "@/lib/store";
import { useUI } from "@/lib/ui";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { useGachaCosmetics } from "@/features/gacha/use-gacha-cosmetics";
import GachaTitleBadge from "@/components/gacha/GachaTitleBadge";
import ProfileCosmeticFrame from "@/components/gacha/ProfileCosmeticFrame";

const LINKS = [
  { icon: "receipt_long", label: "주문 내역", href: "/my/orders" },
  { icon: "redeem", label: "교환 내역", href: "/my/exchanges" },
  { icon: "paid", label: "포인트 내역", href: "/my/points" },
  { icon: "style", label: "쿠폰 목록", href: "/cards" },
  { icon: "gallery_thumbnail", label: "내 카드", href: "/gacha?tab=mine" },
  {
    icon: "award_star",
    label: "내 칭호·테두리",
    href: "/gacha?tab=workshop&section=cosmetics",
  },
  { icon: "menu_book", label: "내 일지", href: "/journals" },
  { icon: "forum", label: "내가 쓴 글/댓글", href: "/my/board" },
  { icon: "mail", label: "1:1 문의", href: "/my/inquiries" },
];

const FIELD =
  "w-full rounded-xl border-[1.5px] border-line px-3.5 py-[11px] text-[15px] outline-none";
const LABEL = "text-[13px] font-bold text-[#6d7a68]";
const PHONE_MAX_LENGTH = 11;
const PHONE_REGEX = /^(010|011)\d{7,8}$/;
const NICKNAME_MAX_LENGTH = 12;
const NAME_MAX_LENGTH = 10;
const PASSWORD_MIN_LENGTH = 8;
const MAX_ADDRESSES = 5;

export default function MyPage() {
  const { showToast } = useUI();
  const { state, set, logout } = useStore();
  const { title: equippedTitle, border: equippedBorder } =
    useGachaCosmetics();
  const router = useRouter();
  const [addresses, setAddresses] = useState<UserAddress[]>([]);
  const [addressesLoading, setAddressesLoading] = useState(true);

  const [profile, setProfile] = useState<UserResponse | null>(null);
  const [editing, setEditing] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState("");
  const [nickname, setNickname] = useState("");
  const [originalNickname, setOriginalNickname] = useState("");
  const [name, setName] = useState("");
  const [phoneNumber, setPhoneNumber] = useState("");

  // 닉네임 중복확인 상태 — 프로필에 원래 있던 닉네임 그대로면 확인이 필요 없다.
  const [nicknameChecked, setNicknameChecked] = useState(true);
  const [nicknameAvailable, setNicknameAvailable] = useState(true);
  const [checkingNickname, setCheckingNickname] = useState(false);
  const [nicknameError, setNicknameError] = useState("");

  useEffect(() => {
    getMe()
      .then((res) => {
        setProfile(res);
        setNickname(res.nickname);
        setOriginalNickname(res.nickname);
        setName(res.name);
        setPhoneNumber(res.phoneNumber ?? "");
      })
      .catch(() => {
        // 헤더의 캐시된 정보로도 화면은 그릴 수 있으니 토스트 정도만 남김
        showToast("프로필을 불러오지 못했어요.", "err");
      });

    getAddresses()
      .then(setAddresses)
      .catch(() => showToast("배송지 목록을 불러오지 못했어요.", "err"))
      .finally(() => setAddressesLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 프로필 수정/비밀번호 변경 폼은 항상 둘 중 하나만 열려 있어야 한다.
  const [passwordGateOpen, setPasswordGateOpen] = useState(false);
  const [gatePassword, setGatePassword] = useState("");
  const [gateError, setGateError] = useState("");
  const [verifyingGate, setVerifyingGate] = useState(false);

  const openEdit = () => {
    if (!profile) return;
    setNickname(profile.nickname);
    setOriginalNickname(profile.nickname);
    setName(profile.name);
    setPhoneNumber(profile.phoneNumber ?? "");
    setFormError("");
    setNicknameChecked(true);
    setNicknameAvailable(true);
    setNicknameError("");
    setChangingPassword(false);
    setWithdrawing(false);
    setEditing(true);
  };

  const openPasswordGate = () => {
    if (!profile) return;
    // 소셜 로그인 계정은 비밀번호 자체가 없어서 이 확인 절차를 통과할 방법이 없다.
    // 이미 유효한 로그인 세션(액세스 토큰)이 본인 확인을 대신하므로 게이트를 건너뛴다.
    if (profile.provider !== "LOCAL") {
      openEdit();
      return;
    }
    setGatePassword("");
    setGateError("");
    setChangingPassword(false);
    setEditing(false);
    setWithdrawing(false);
    setPasswordGateOpen(true);
  };

  const submitPasswordGate = async () => {
    setGateError("");
    setVerifyingGate(true);
    try {
      await verifyPassword(gatePassword);
      setPasswordGateOpen(false);
      openEdit();
    } catch (e) {
      setGateError(
        e instanceof ApiError ? e.message : "비밀번호 확인에 실패했어요.",
      );
    } finally {
      setVerifyingGate(false);
    }
  };

  const changeNickname = (value: string) => {
    const next = value.slice(0, NICKNAME_MAX_LENGTH);
    setNickname(next);
    if (next === originalNickname) {
      setNicknameChecked(true);
      setNicknameAvailable(true);
      setNicknameError("");
    } else {
      setNicknameChecked(false);
      setNicknameAvailable(false);
      setNicknameError("");
    }
  };

  const checkNickname = async () => {
    setNicknameError("");
    setCheckingNickname(true);
    try {
      const res = await checkNicknameAvailability(nickname);
      setNicknameChecked(true);
      setNicknameAvailable(res.available);
      if (!res.available) {
        setNicknameError("이미 사용 중인 닉네임이에요.");
      }
    } catch (e) {
      setNicknameChecked(false);
      setNicknameError(
        e instanceof ApiError ? e.message : "중복확인에 실패했어요.",
      );
    } finally {
      setCheckingNickname(false);
    }
  };

  const changePhoneNumber = (value: string) => {
    setPhoneNumber(value.replace(/\D/g, "").slice(0, PHONE_MAX_LENGTH));
  };

  const submitEdit = async () => {
    setFormError("");
    if (
      nickname !== originalNickname &&
      (!nicknameChecked || !nicknameAvailable)
    ) {
      setFormError("닉네임 중복확인을 먼저 완료해 주세요.");
      return;
    }
    if (phoneNumber && !PHONE_REGEX.test(phoneNumber)) {
      setFormError(
        "전화번호는 010 또는 011로 시작하는 숫자 10~11자리여야 해요.",
      );
      return;
    }
    setSubmitting(true);
    try {
      const updated = await updateProfile({
        nickname,
        name,
        phoneNumber: phoneNumber || undefined,
      });
      setProfile(updated);
      // 헤더/네브바가 참조하는 store의 nickname도 같이 갱신
      set((s) =>
        s.user
          ? {
              user: {
                ...s.user,
                nickname: updated.nickname,
              },
            }
          : {},
      );
      setEditing(false);
      showToast("프로필을 수정했어요.");
    } catch (e) {
      setFormError(
        e instanceof ApiError
          ? e.message
          : "프로필 수정에 실패했어요. 다시 시도해 주세요.",
      );
    } finally {
      setSubmitting(false);
    }
  };

  const [changingPassword, setChangingPassword] = useState(false);
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [newPasswordConfirm, setNewPasswordConfirm] = useState("");
  const [passwordSubmitting, setPasswordSubmitting] = useState(false);
  const [passwordError, setPasswordError] = useState("");

  const openPasswordChange = () => {
    setCurrentPassword("");
    setNewPassword("");
    setNewPasswordConfirm("");
    setPasswordError("");
    setEditing(false);
    setPasswordGateOpen(false);
    setWithdrawing(false);
    setChangingPassword(true);
  };

  const submitPasswordChange = async () => {
    setPasswordError("");
    if (newPassword.length < PASSWORD_MIN_LENGTH) {
      setPasswordError(
        `새 비밀번호는 ${PASSWORD_MIN_LENGTH}자 이상이어야 해요.`,
      );
      return;
    }
    if (newPassword !== newPasswordConfirm) {
      setPasswordError("새 비밀번호가 서로 달라요.");
      return;
    }
    setPasswordSubmitting(true);
    try {
      await changePassword(currentPassword, newPassword);
      setChangingPassword(false);
      showToast("비밀번호를 변경했어요.");
    } catch (e) {
      setPasswordError(
        e instanceof ApiError
          ? e.message
          : "비밀번호 변경에 실패했어요. 다시 시도해 주세요.",
      );
    } finally {
      setPasswordSubmitting(false);
    }
  };

  const [withdrawing, setWithdrawing] = useState(false);
  const [withdrawPassword, setWithdrawPassword] = useState("");
  const [withdrawError, setWithdrawError] = useState("");
  const [withdrawSubmitting, setWithdrawSubmitting] = useState(false);

  const openWithdraw = () => {
    setWithdrawPassword("");
    setWithdrawError("");
    setEditing(false);
    setPasswordGateOpen(false);
    setChangingPassword(false);
    setWithdrawing(true);
  };

  const submitWithdraw = async () => {
    setWithdrawError("");
    if (profile?.provider === "LOCAL" && !withdrawPassword) {
      setWithdrawError("비밀번호를 입력해 주세요.");
      return;
    }
    setWithdrawSubmitting(true);
    try {
      await withdraw(withdrawPassword || undefined);
      logout();
      showToast("탈퇴가 완료됐어요. 그동안 이용해 주셔서 감사합니다.");
      router.push("/");
    } catch (e) {
      setWithdrawError(
        e instanceof ApiError
          ? e.message
          : "탈퇴 처리에 실패했어요. 다시 시도해 주세요.",
      );
    } finally {
      setWithdrawSubmitting(false);
    }
  };

  const [addressFormOpen, setAddressFormOpen] = useState(false);
  const [editingAddressId, setEditingAddressId] = useState<number | null>(null);
  const [addressReceiverName, setAddressReceiverName] = useState("");
  const [addressReceiverPhone, setAddressReceiverPhone] = useState("");
  const [addressZipCode, setAddressZipCode] = useState("");
  const [addressLine, setAddressLine] = useState("");
  const [addressDetail, setAddressDetail] = useState("");
  const [addressIsDefault, setAddressIsDefault] = useState(false);
  const [addressSubmitting, setAddressSubmitting] = useState(false);
  const [addressFormError, setAddressFormError] = useState("");
  const [addressActionId, setAddressActionId] = useState<number | null>(null);

  const openAddressCreate = () => {
    setEditingAddressId(null);
    // 배송지 등록은 보통 본인 앞으로 받는 경우가 많아, 프로필의 이름·번호가 있으면 미리 채워준다.
    setAddressReceiverName(profile?.name ?? "");
    setAddressReceiverPhone(profile?.phoneNumber ?? "");
    setAddressZipCode("");
    setAddressLine("");
    setAddressDetail("");
    setAddressIsDefault(addresses.length === 0);
    setAddressFormError("");
    setAddressFormOpen(true);
  };

  const openAddressEdit = (a: UserAddress) => {
    setEditingAddressId(a.id);
    setAddressReceiverName(a.receiverName);
    setAddressReceiverPhone(a.receiverPhone.replace(/\D/g, ""));
    setAddressZipCode(a.zipCode);
    setAddressLine(a.address);
    setAddressDetail(a.addressDetail ?? "");
    setAddressIsDefault(a.isDefault);
    setAddressFormError("");
    setAddressFormOpen(true);
  };

  const closeAddressForm = () => {
    setAddressFormOpen(false);
    setEditingAddressId(null);
  };

  const [postcodeOpen, setPostcodeOpen] = useState(false);
  const [postcodeError, setPostcodeError] = useState("");
  const postcodeContainerRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!postcodeOpen || !postcodeContainerRef.current) return;
    embedAddressSearch(postcodeContainerRef.current, ({ zipCode, address }) => {
      setAddressZipCode(zipCode);
      setAddressLine(address);
      setPostcodeOpen(false);
    }).catch(() => setPostcodeError("주소 검색을 불러오지 못했어요. 잠시 후 다시 시도해 주세요."));
  }, [postcodeOpen]);

  const searchAddress = () => {
    setPostcodeError("");
    setPostcodeOpen(true);
  };

  const changeAddressReceiverPhone = (value: string) => {
    setAddressReceiverPhone(value.replace(/\D/g, "").slice(0, PHONE_MAX_LENGTH));
  };

  const submitAddressForm = async () => {
    setAddressFormError("");
    if (!addressReceiverName || !addressReceiverPhone || !addressZipCode || !addressLine) {
      setAddressFormError("필수 항목을 모두 입력해 주세요.");
      return;
    }
    if (!PHONE_REGEX.test(addressReceiverPhone)) {
      setAddressFormError("연락처는 010 또는 011로 시작하는 숫자 10~11자리여야 해요.");
      return;
    }
    const payload = {
      receiverName: addressReceiverName,
      receiverPhone: addressReceiverPhone,
      zipCode: addressZipCode,
      address: addressLine,
      addressDetail: addressDetail || undefined,
      isDefault: addressIsDefault,
    };
    setAddressSubmitting(true);
    try {
      if (editingAddressId) {
        const updated = await updateAddress(editingAddressId, payload);
        setAddresses((prev) =>
          prev
            .map((a) => (a.id === updated.id ? updated : updated.isDefault ? { ...a, isDefault: false } : a))
            .sort((a, b) => Number(b.isDefault) - Number(a.isDefault)),
        );
        showToast("배송지를 수정했어요.");
      } else {
        const created = await createAddress(payload);
        setAddresses((prev) =>
          [created, ...(created.isDefault ? prev.map((a) => ({ ...a, isDefault: false })) : prev)].sort(
            (a, b) => Number(b.isDefault) - Number(a.isDefault),
          ),
        );
        showToast("배송지를 등록했어요.");
      }
      closeAddressForm();
    } catch (e) {
      setAddressFormError(
        e instanceof ApiError ? e.message : "배송지 저장에 실패했어요. 다시 시도해 주세요.",
      );
    } finally {
      setAddressSubmitting(false);
    }
  };

  const removeAddress = async (id: number) => {
    setAddressActionId(id);
    try {
      await deleteAddress(id);
      setAddresses((prev) => prev.filter((a) => a.id !== id));
      showToast("배송지를 삭제했어요.");
    } catch (e) {
      showToast(
        e instanceof ApiError ? e.message : "배송지 삭제에 실패했어요.",
        "err",
      );
    } finally {
      setAddressActionId(null);
    }
  };

  const setDefault = async (id: number) => {
    setAddressActionId(id);
    try {
      await setDefaultAddress(id);
      setAddresses((prev) =>
        prev
          .map((a) => ({ ...a, isDefault: a.id === id }))
          .sort((a, b) => Number(b.isDefault) - Number(a.isDefault)),
      );
      showToast("기본 배송지를 변경했어요.");
    } catch (e) {
      showToast(
        e instanceof ApiError ? e.message : "기본 배송지 변경에 실패했어요.",
        "err",
      );
    } finally {
      setAddressActionId(null);
    }
  };

  const doLogout = () => {
    logout();
    showToast("로그아웃했어요.");
    router.push("/");
  };

  const displayNickname = profile?.nickname ?? state.user?.nickname ?? "게스트";
  const displayEmail = profile?.email ?? state.user?.email ?? "";

  return (
    <div className="container">
      <div className="mb-6 flex flex-wrap items-center gap-[18px] rounded-[20px] bg-white p-6 shadow-card">
        <ProfileCosmeticFrame
          borderCode={equippedBorder?.code}
          className="h-[78px] w-[78px]"
        >
          <div className="flex h-full w-full items-center justify-center rounded-full bg-gradient-to-br from-[#AED581] to-[#7CB342] text-3xl font-extrabold text-white">
            {displayNickname.charAt(0)}
          </div>
        </ProfileCosmeticFrame>
        <div className="min-w-[180px] flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-xl font-extrabold">{displayNickname}</span>
          </div>
          <div className="mt-1 text-sm text-sub">{displayEmail}</div>
          {equippedTitle ? (
            <GachaTitleBadge
              code={equippedTitle.code}
              name={equippedTitle.name}
              className="mt-2"
            />
          ) : null}
        </div>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={openPasswordGate}
            disabled={!profile}
            className="cursor-pointer rounded-[11px] bg-brand-soft px-[18px] py-[11px] font-bold text-brand-dark disabled:opacity-60"
          >
            프로필 수정
          </button>
          {profile?.provider === "LOCAL" && (
            <button
              type="button"
              onClick={openPasswordChange}
              disabled={!profile}
              className="cursor-pointer rounded-[11px] border-[1.5px] border-line bg-white px-[18px] py-[11px] font-bold text-sub disabled:opacity-60"
            >
              비밀번호 변경
            </button>
          )}
        </div>
      </div>

      {passwordGateOpen && (
        <div className="mb-6 rounded-[20px] bg-white p-6 shadow-card">
          <h2 className="mb-4 text-lg font-extrabold">본인 확인</h2>
          <p className="mb-3.5 text-sm text-sub">
            프로필 수정을 위해 현재 비밀번호를 입력해 주세요.
          </p>
          <label className={LABEL}>비밀번호</label>
          <input
            type="password"
            value={gatePassword}
            onChange={(e) => setGatePassword(e.target.value)}
            className={`${FIELD} mt-1.5`}
          />

          {gateError && (
            <div className="mt-3.5 rounded-[11px] bg-danger-soft px-[13px] py-[11px] text-[13px] font-semibold text-danger">
              {gateError}
            </div>
          )}

          <div className="mt-5 flex gap-2.5">
            <button
              type="button"
              disabled={verifyingGate || !gatePassword}
              onClick={submitPasswordGate}
              className="flex-1 cursor-pointer rounded-xl bg-brand p-3 font-bold text-white transition-colors duration-150 hover:bg-brand-dark disabled:opacity-60"
            >
              확인
            </button>
            <button
              type="button"
              onClick={() => setPasswordGateOpen(false)}
              className="flex-1 cursor-pointer rounded-xl border-[1.5px] border-line bg-white p-3 font-bold text-sub"
            >
              취소
            </button>
          </div>
        </div>
      )}

      {editing && (
        <div className="mb-6 rounded-[20px] bg-white p-6 shadow-card">
          <h2 className="mb-4 text-lg font-extrabold">프로필 수정</h2>
          <div className="flex flex-col gap-3.5">
            <div>
              <label className={LABEL}>닉네임</label>
              <div className="mt-1.5 flex gap-2">
                <input
                  value={nickname}
                  onChange={(e) => changeNickname(e.target.value)}
                  maxLength={NICKNAME_MAX_LENGTH}
                  className={FIELD}
                />
                <button
                  type="button"
                  disabled={
                    !nickname ||
                    nickname === originalNickname ||
                    checkingNickname
                  }
                  onClick={checkNickname}
                  className="w-[104px] shrink-0 cursor-pointer whitespace-nowrap rounded-xl border-[1.5px] border-line bg-white text-[13px] font-bold text-[#5b6a54] transition-colors duration-150 hover:bg-brand-soft hover:text-brand-dark disabled:cursor-default disabled:opacity-60"
                >
                  {nickname !== originalNickname &&
                  nicknameChecked &&
                  nicknameAvailable
                    ? "사용가능"
                    : "중복확인"}
                </button>
              </div>
              <div className="mt-1 flex items-center justify-between">
                <span
                  className={`text-xs ${nicknameError ? "font-semibold text-danger" : "text-[#a9b3a0]"}`}
                >
                  {nicknameError ||
                    (nickname !== originalNickname &&
                    nicknameChecked &&
                    nicknameAvailable
                      ? "사용할 수 있는 닉네임이에요."
                      : "")}
                </span>
                <span className="text-xs text-[#a9b3a0]">
                  {nickname.length}/{NICKNAME_MAX_LENGTH}
                </span>
              </div>
            </div>
            <div>
              <label className={LABEL}>이름</label>
              <input
                value={name}
                onChange={(e) => setName(e.target.value)}
                maxLength={NAME_MAX_LENGTH}
                className={`${FIELD} mt-1.5`}
              />
            </div>
            <div>
              <label className={LABEL}>전화번호</label>
              <input
                value={phoneNumber}
                onChange={(e) => changePhoneNumber(e.target.value)}
                maxLength={PHONE_MAX_LENGTH}
                inputMode="numeric"
                placeholder="01012345678"
                className={`${FIELD} mt-1.5`}
              />
            </div>
          </div>

          {formError && (
            <div className="mt-3.5 rounded-[11px] bg-danger-soft px-[13px] py-[11px] text-[13px] font-semibold text-danger">
              {formError}
            </div>
          )}

          <div className="mt-5 flex gap-2.5">
            <button
              type="button"
              disabled={
                submitting ||
                (nickname !== originalNickname &&
                  (!nicknameChecked || !nicknameAvailable))
              }
              onClick={submitEdit}
              className="flex-1 cursor-pointer rounded-xl bg-brand p-3 font-bold text-white transition-colors duration-150 hover:bg-brand-dark disabled:opacity-60"
            >
              저장
            </button>
            <button
              type="button"
              onClick={() => setEditing(false)}
              className="flex-1 cursor-pointer rounded-xl border-[1.5px] border-line bg-white p-3 font-bold text-sub"
            >
              취소
            </button>
          </div>
        </div>
      )}

      {changingPassword && (
        <div className="mb-6 rounded-[20px] bg-white p-6 shadow-card">
          <h2 className="mb-4 text-lg font-extrabold">비밀번호 변경</h2>
          <div className="flex flex-col gap-3.5">
            <div>
              <label className={LABEL}>현재 비밀번호</label>
              <input
                type="password"
                value={currentPassword}
                onChange={(e) => setCurrentPassword(e.target.value)}
                className={`${FIELD} mt-1.5`}
              />
            </div>
            <div>
              <label className={LABEL}>새 비밀번호</label>
              <input
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                placeholder={`${PASSWORD_MIN_LENGTH}자 이상`}
                className={`${FIELD} mt-1.5`}
              />
            </div>
            <div>
              <label className={LABEL}>새 비밀번호 확인</label>
              <input
                type="password"
                value={newPasswordConfirm}
                onChange={(e) => setNewPasswordConfirm(e.target.value)}
                placeholder="다시 입력"
                className={`${FIELD} mt-1.5`}
              />
            </div>
          </div>

          {passwordError && (
            <div className="mt-3.5 rounded-[11px] bg-danger-soft px-[13px] py-[11px] text-[13px] font-semibold text-danger">
              {passwordError}
            </div>
          )}

          <div className="mt-5 flex gap-2.5">
            <button
              type="button"
              disabled={passwordSubmitting}
              onClick={submitPasswordChange}
              className="flex-1 cursor-pointer rounded-xl bg-brand p-3 font-bold text-white transition-colors duration-150 hover:bg-brand-dark disabled:opacity-60"
            >
              변경
            </button>
            <button
              type="button"
              onClick={() => setChangingPassword(false)}
              className="flex-1 cursor-pointer rounded-xl border-[1.5px] border-line bg-white p-3 font-bold text-sub"
            >
              취소
            </button>
          </div>
        </div>
      )}

      <h2 className="mb-3.5 text-lg font-extrabold">바로가기</h2>
      <div className="mb-7 grid gap-3.5 [grid-template-columns:repeat(auto-fill,minmax(150px,1fr))]">
        {LINKS.map((l) => (
          <Link
            key={l.label}
            href={l.href}
            className="rounded-2xl bg-white px-[18px] py-5 text-ink shadow-card hover:text-ink"
          >
            <div>
              <span className="material-symbols-outlined text-[28px] text-brand">
                {l.icon}
              </span>
            </div>
            <div className="mt-2 font-extrabold">{l.label}</div>
          </Link>
        ))}
      </div>

      <h2 className="mb-3.5 text-lg font-extrabold">배송지 관리</h2>
      <div className="flex flex-col gap-3">
        {addressesLoading && (
          <div className="rounded-[14px] bg-white px-[18px] py-4 text-sm text-sub shadow-card">
            배송지를 불러오는 중이에요...
          </div>
        )}
        {!addressesLoading && addresses.length === 0 && (
          <div className="rounded-[14px] bg-white px-[18px] py-4 text-sm text-sub shadow-card">
            등록된 배송지가 없어요.
          </div>
        )}
        {addresses.map((a) => (
          <div
            key={a.id}
            className="flex flex-wrap items-center gap-3 rounded-[14px] bg-white px-[18px] py-4 shadow-card"
          >
            <div className="min-w-[180px] flex-1">
              <div className="flex items-center gap-2 font-bold">
                {a.receiverName}
                {a.isDefault && (
                  <span className="rounded-full bg-brand-soft px-2 py-0.5 text-[11px] text-brand-dark">
                    기본
                  </span>
                )}
              </div>
              <div className="mt-1 text-[13.5px] text-sub">
                {formatPhone(a.receiverPhone)} · {a.address}
                {a.addressDetail ? ` ${a.addressDetail}` : ""}
              </div>
            </div>
            {!a.isDefault && (
              <button
                type="button"
                disabled={addressActionId === a.id}
                onClick={() => setDefault(a.id)}
                className="cursor-pointer rounded-[10px] border-[1.5px] border-[#cfe0b6] bg-white px-3.5 py-2 text-[13px] font-bold text-brand-dark disabled:opacity-60"
              >
                기본으로
              </button>
            )}
            <button
              type="button"
              onClick={() => openAddressEdit(a)}
              className="cursor-pointer rounded-[10px] border-[1.5px] border-line bg-white px-3 py-2 text-[13px] font-bold text-sub"
            >
              수정
            </button>
            <button
              type="button"
              disabled={addressActionId === a.id}
              onClick={() => removeAddress(a.id)}
              className="cursor-pointer rounded-[10px] border-[1.5px] border-line bg-white px-3 py-2 text-[13px] font-bold text-danger disabled:opacity-60"
            >
              삭제
            </button>
          </div>
        ))}
        {!addressFormOpen && addresses.length < MAX_ADDRESSES && (
          <button
            type="button"
            onClick={openAddressCreate}
            className="cursor-pointer rounded-[14px] border-[1.5px] border-dashed border-[#cfe0b6] bg-white p-3.5 text-center font-bold text-brand-dark"
          >
            + 새 배송지 추가
          </button>
        )}
        {!addressFormOpen && addresses.length >= MAX_ADDRESSES && (
          <div className="rounded-[14px] bg-[#F8FAF3] px-[18px] py-3.5 text-center text-sm text-sub">
            배송지는 최대 {MAX_ADDRESSES}개까지 등록할 수 있어요.
          </div>
        )}
      </div>

      {addressFormOpen && (
        <div className="mt-3 rounded-[20px] bg-white p-6 shadow-card">
          <h2 className="mb-4 text-lg font-extrabold">
            {editingAddressId ? "배송지 수정" : "배송지 등록"}
          </h2>
          <div className="flex flex-col gap-3.5">
            <div>
              <label className={LABEL}>받는 사람</label>
              <input
                value={addressReceiverName}
                onChange={(e) => setAddressReceiverName(e.target.value)}
                maxLength={50}
                className={`${FIELD} mt-1.5`}
              />
            </div>
            <div>
              <label className={LABEL}>연락처</label>
              <input
                value={addressReceiverPhone}
                onChange={(e) => changeAddressReceiverPhone(e.target.value)}
                maxLength={PHONE_MAX_LENGTH}
                inputMode="numeric"
                placeholder="01012345678"
                className={`${FIELD} mt-1.5`}
              />
            </div>
            <div>
              <label className={LABEL}>우편번호</label>
              <div className="mt-1.5 flex gap-2">
                <input
                  value={addressZipCode}
                  readOnly
                  placeholder="주소 검색으로 입력돼요"
                  className={`${FIELD} bg-[#F8FAF3]`}
                />
                <button
                  type="button"
                  onClick={searchAddress}
                  className="w-[104px] shrink-0 cursor-pointer whitespace-nowrap rounded-xl border-[1.5px] border-line bg-white text-[13px] font-bold text-[#5b6a54] transition-colors duration-150 hover:bg-brand-soft hover:text-brand-dark"
                >
                  주소 검색
                </button>
              </div>
            </div>
            <div>
              <label className={LABEL}>주소</label>
              <input
                value={addressLine}
                readOnly
                placeholder="주소 검색 버튼으로 입력해 주세요"
                className={`${FIELD} mt-1.5 bg-[#F8FAF3]`}
              />
            </div>
            <div>
              <label className={LABEL}>상세 주소</label>
              <input
                value={addressDetail}
                onChange={(e) => setAddressDetail(e.target.value)}
                maxLength={100}
                className={`${FIELD} mt-1.5`}
              />
            </div>
            <label className="flex items-center gap-2 text-sm text-sub">
              <input
                type="checkbox"
                checked={addressIsDefault}
                onChange={(e) => setAddressIsDefault(e.target.checked)}
              />
              기본 배송지로 설정
            </label>
          </div>

          {addressFormError && (
            <div className="mt-3.5 rounded-[11px] bg-danger-soft px-[13px] py-[11px] text-[13px] font-semibold text-danger">
              {addressFormError}
            </div>
          )}

          <div className="mt-5 flex gap-2.5">
            <button
              type="button"
              disabled={addressSubmitting}
              onClick={submitAddressForm}
              className="flex-1 cursor-pointer rounded-xl bg-brand p-3 font-bold text-white transition-colors duration-150 hover:bg-brand-dark disabled:opacity-60"
            >
              저장
            </button>
            <button
              type="button"
              onClick={closeAddressForm}
              className="flex-1 cursor-pointer rounded-xl border-[1.5px] border-line bg-white p-3 font-bold text-sub"
            >
              취소
            </button>
          </div>
        </div>
      )}

      {postcodeOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="flex max-h-[80vh] w-full max-w-[480px] flex-col overflow-hidden rounded-[20px] bg-white shadow-card">
            <div className="flex items-center justify-between border-b border-line px-5 py-3.5">
              <h2 className="text-base font-extrabold">주소 검색</h2>
              <button
                type="button"
                onClick={() => setPostcodeOpen(false)}
                className="cursor-pointer text-sm font-bold text-sub"
              >
                닫기
              </button>
            </div>
            {postcodeError ? (
              <div className="p-5 text-sm text-danger">{postcodeError}</div>
            ) : (
              <div ref={postcodeContainerRef} className="h-[450px] w-full" />
            )}
          </div>
        </div>
      )}

      <button
        type="button"
        onClick={doLogout}
        className="mt-7 w-full cursor-pointer rounded-[14px] border-[1.5px] border-line bg-white p-3.5 text-center font-bold text-danger"
      >
        로그아웃
      </button>

      <div className="mt-3.5 text-center">
        <button
          type="button"
          onClick={openWithdraw}
          disabled={!profile}
          className="cursor-pointer text-xs text-[#a9b3a0] underline-offset-2 hover:underline disabled:opacity-60"
        >
          회원탈퇴
        </button>
      </div>

      {withdrawing && (
        <div className="mt-6 rounded-[20px] border-[1.5px] border-danger-soft bg-white p-6 shadow-card">
          <h2 className="mb-2 text-lg font-extrabold text-danger">회원탈퇴</h2>
          <p className="mb-3.5 text-sm text-sub">
            탈퇴하면 즉시 로그아웃되고 다시 로그인할 수 없어요.
          </p>
          {profile?.provider === "LOCAL" && (
            <>
              <label className={LABEL}>비밀번호</label>
              <input
                type="password"
                value={withdrawPassword}
                onChange={(e) => setWithdrawPassword(e.target.value)}
                className={`${FIELD} mt-1.5`}
              />
            </>
          )}

          {withdrawError && (
            <div className="mt-3.5 rounded-[11px] bg-danger-soft px-[13px] py-[11px] text-[13px] font-semibold text-danger">
              {withdrawError}
            </div>
          )}

          <div className="mt-5 flex gap-2.5">
            <button
              type="button"
              disabled={withdrawSubmitting}
              onClick={submitWithdraw}
              className="flex-1 cursor-pointer rounded-xl bg-danger p-3 font-bold text-white transition-colors duration-150 hover:brightness-95 disabled:opacity-60"
            >
              탈퇴하기
            </button>
            <button
              type="button"
              onClick={() => setWithdrawing(false)}
              className="flex-1 cursor-pointer rounded-xl border-[1.5px] border-line bg-white p-3 font-bold text-sub"
            >
              취소
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
