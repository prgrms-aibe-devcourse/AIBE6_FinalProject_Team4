"use client";
import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { ApiError, getAddresses, UserAddress } from "@/lib/api";
import { embedAddressSearch } from "@/lib/daumPostcode";

export interface AddressFields {
  receiverName: string;
  receiverPhone: string;
  zipCode: string;
  address: string;
  addressDetail: string;
}

export const EMPTY_ADDRESS_FIELDS: AddressFields = {
  receiverName: "",
  receiverPhone: "",
  zipCode: "",
  address: "",
  addressDetail: "",
};

// 백엔드 UserAddress 검증(하이픈 없이 010/011 + 숫자 7~8자리)과 동일한 규칙.
// Order/ExchangeOrder는 이 형식을 서버에서 강제하지 않으므로, 자유 입력 경로에서
// 잘못된 형식이 그대로 저장되지 않도록 프론트에서 검증한다.
const PHONE_PATTERN = /^(010|011)\d{7,8}$/;

export function isValidPhone(phone: string): boolean {
  return PHONE_PATTERN.test(phone);
}

export function isCompleteAddress(fields: AddressFields): boolean {
  return (
    fields.receiverName.trim() !== "" &&
    isValidPhone(fields.receiverPhone) &&
    fields.zipCode.trim() !== "" &&
    fields.address.trim() !== ""
  );
}

// 하이픈 없이 저장된 번호를 화면 표시용으로만 010-1234-5678 / 010-123-4567 형태로 나눈다.
// 패턴에 안 맞는 값(레거시 데이터 등)은 원본 그대로 보여준다.
export function formatPhone(phone: string): string {
  const digits = phone.replace(/[^0-9]/g, "");
  if (digits.length === 11)
    return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`;
  if (digits.length === 10)
    return `${digits.slice(0, 3)}-${digits.slice(3, 6)}-${digits.slice(6)}`;
  return phone;
}

const FIELD =
  "w-full rounded-xl border-[1.5px] border-line px-[13px] py-3 outline-none";
const LABEL = "text-[13px] font-bold text-[#6d7a68]";

/**
 * 교환/주문 양쪽에서 공용으로 쓰는 배송지 입력.
 * 저장된 배송지 목록을 불러와 선택하면 필드에 채워주고, 그 필드를 자유롭게 다시 고칠 수 있다.
 * 여기서 고친 값은 이번 주문/교환의 스냅샷일 뿐 마이페이지 배송지북에는 반영되지 않는다.
 */
export default function AddressForm({
  accessToken,
  value,
  onChange,
}: {
  accessToken: string | null;
  value: AddressFields;
  onChange: (fields: AddressFields) => void;
}) {
  const [addresses, setAddresses] = useState<UserAddress[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [postcodeOpen, setPostcodeOpen] = useState(false);
  const [postcodeError, setPostcodeError] = useState("");
  const postcodeContainerRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!postcodeOpen || !postcodeContainerRef.current) return;
    embedAddressSearch(postcodeContainerRef.current, ({ zipCode, address }) => {
      onChange({ ...value, zipCode, address });
      setPostcodeOpen(false);
    }).catch(() =>
      setPostcodeError(
        "주소 검색을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.",
      ),
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [postcodeOpen]);

  const searchAddress = () => {
    setPostcodeError("");
    setPostcodeOpen(true);
  };

  // accessToken이 재발급 등으로 다시 바뀌어도(예: 응답 대기 중 토큰 리이슈) 목록을 다시
  // 불러오는 것 자체는 괜찮지만, 그때마다 기본 배송지로 덮어쓰면 사용자가 이미 고르거나
  // 입력 중인 값이 통째로 사라진다. 최초 1회만 자동으로 채운다.
  const appliedDefaultRef = useRef(false);

  useEffect(() => {
    if (!accessToken) return;
    setLoading(true);
    setLoadError("");
    getAddresses()
      .then((list) => {
        setAddresses(list);
        if (appliedDefaultRef.current) return;
        appliedDefaultRef.current = true;
        const defaultAddress = list.find((a) => a.isDefault) || list[0];
        if (defaultAddress) {
          setSelectedId(defaultAddress.id);
          onChange({
            receiverName: defaultAddress.receiverName,
            receiverPhone: defaultAddress.receiverPhone,
            zipCode: defaultAddress.zipCode,
            address: defaultAddress.address,
            addressDetail: defaultAddress.addressDetail || "",
          });
        }
      })
      .catch((requestError) => {
        setLoadError(
          requestError instanceof ApiError
            ? requestError.message
            : "배송지를 불러오지 못했어요.",
        );
      })
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accessToken]);

  const selectSaved = (a: UserAddress) => {
    setSelectedId(a.id);
    onChange({
      receiverName: a.receiverName,
      receiverPhone: a.receiverPhone,
      zipCode: a.zipCode,
      address: a.address,
      addressDetail: a.addressDetail || "",
    });
  };

  const startNew = () => {
    setSelectedId(null);
    onChange(EMPTY_ADDRESS_FIELDS);
  };

  return (
    <div>
      {loading && (
        <div className="mb-3 text-sm text-sub">배송지를 불러오고 있어요…</div>
      )}
      {loadError && <div className="mb-3 text-sm text-danger">{loadError}</div>}

      {!loading && addresses.length === 0 && (
        <div className="mb-3.5 rounded-[14px] border-[1.5px] border-dashed border-[#cfe0b6] bg-white p-3.5 text-center text-sm text-sub">
          등록된 배송지가 없어요. 아래에 새 배송지를 입력해 주세요.{" "}
          <Link href="/my" className="font-bold text-brand-dark underline">
            마이페이지에서 등록하기
          </Link>
        </div>
      )}

      {addresses.length > 0 && (
        <div className="mb-3.5 flex flex-col gap-2">
          {addresses.map((a) => (
            <button
              key={a.id}
              type="button"
              onClick={() => selectSaved(a)}
              className={`cursor-pointer rounded-[14px] border-2 bg-white px-4 py-[13px] text-left ${
                selectedId === a.id ? "border-brand" : "border-[#eceee5]"
              }`}
            >
              <div className="flex items-center gap-2 font-bold">
                {a.receiverName}
                {a.isDefault && (
                  <span className="rounded-full bg-brand-soft px-2 py-0.5 text-[11px] text-brand-dark">
                    기본
                  </span>
                )}
              </div>
              <div className="mt-1 text-[13.5px] text-sub">
                {formatPhone(a.receiverPhone)} · [{a.zipCode}] {a.address}{" "}
                {a.addressDetail}
              </div>
            </button>
          ))}
          <button
            type="button"
            onClick={startNew}
            className={`cursor-pointer rounded-[14px] border-2 border-dashed bg-white px-4 py-[13px] text-left text-sm font-bold text-sub ${
              selectedId === null
                ? "border-brand text-brand-dark"
                : "border-[#eceee5]"
            }`}
          >
            + 새 배송지로 입력
          </button>
        </div>
      )}

      <p className="mb-2 text-[12.5px] text-sub">
        {selectedId === null
          ? "이번에만 사용할 배송지를 입력해 주세요."
          : "받는 분/연락처/상세 주소는 이번 배송에만 자유롭게 고칠 수 있고, 주소를 바꾸려면 다시 검색해 주세요."}
      </p>

      <label className={LABEL}>
        받는 분 <span className="text-[#e5533b]">*</span>
      </label>
      <input
        value={value.receiverName}
        onChange={(e) => onChange({ ...value, receiverName: e.target.value })}
        maxLength={50}
        placeholder="이름"
        className={`${FIELD} mb-3.5 mt-1.5`}
      />
      <label className={LABEL}>
        연락처 <span className="text-[#e5533b]">*</span>
      </label>
      <input
        value={value.receiverPhone}
        onChange={(e) =>
          onChange({
            ...value,
            receiverPhone: e.target.value.replace(/[^0-9]/g, ""),
          })
        }
        maxLength={11}
        inputMode="numeric"
        placeholder="01000000000 (하이픈 없이 숫자만)"
        className={`${FIELD} mt-1.5 ${
          value.receiverPhone !== "" && !isValidPhone(value.receiverPhone)
            ? "mb-1.5 border-danger"
            : "mb-3.5"
        }`}
      />
      {value.receiverPhone !== "" && !isValidPhone(value.receiverPhone) && (
        <p className="mb-3.5 text-[12.5px] text-danger">
          010 또는 011로 시작하는 숫자 9~11자리로 입력해 주세요.
        </p>
      )}
      <label className={LABEL}>
        우편번호 <span className="text-[#e5533b]">*</span>
      </label>
      <div className="mb-3.5 mt-1.5 flex gap-2">
        <input
          value={value.zipCode}
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
      <label className={LABEL}>
        주소 <span className="text-[#e5533b]">*</span>
      </label>
      <input
        value={value.address}
        readOnly
        placeholder="주소 검색 버튼으로 입력해 주세요"
        className={`${FIELD} mb-3.5 mt-1.5 bg-[#F8FAF3]`}
      />
      <label className={LABEL}>상세 주소</label>
      <input
        value={value.addressDetail}
        onChange={(e) => onChange({ ...value, addressDetail: e.target.value })}
        maxLength={100}
        placeholder="동/호수 등"
        className={`${FIELD} mb-3.5 mt-1.5`}
      />

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
    </div>
  );
}
