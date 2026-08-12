"use client";

import { FormEvent, useRef, useState } from "react";
import AdminUserPicker from "@/features/admin/AdminUserPicker";
import { AdminUserSummary } from "@/features/admin/user-api";
import AdminPointAdjustmentHistory from "@/features/point/AdminPointAdjustmentHistory";
import {
  AdminPointAdjustmentReason,
  AdminPointAdjustmentData,
  AdminPointAdjustmentInput,
  adjustPointByAdmin,
  getWalletByAdmin,
  PointCurrencyType,
  WalletData,
} from "@/features/point/api";
import {
  ADMIN_POINT_ADJUSTMENT_REASON_LABELS,
  ADMIN_POINT_DEDUCT_REASONS,
  ADMIN_POINT_GRANT_REASONS,
  getAdminPointAdjustmentReasonLabel,
} from "@/features/point/admin-adjustment-reasons";
import { ApiError } from "@/lib/api";
import { useUI } from "@/lib/ui";

type AdjustmentMode = "GRANT" | "DEDUCT";

interface AdminPointAdjustmentPanelProps {
  accessToken: string | null;
  adminUserId: number;
}

function createIdempotencyKey(): string {
  return (
    globalThis.crypto?.randomUUID?.() ??
    `point-adjust-${Date.now()}-${Math.random().toString(16).slice(2)}`
  );
}

const ADJUSTMENT_ATTEMPT_STORAGE_PREFIX = "kwb:admin-point-adjustment:";

function normalizeAdjustmentPayload(
  payload: AdminPointAdjustmentInput,
): string {
  return `userId=${payload.userId}&currencyType=${payload.currencyType}&amount=${payload.amount}&adjustmentReason=${payload.adjustmentReason}`;
}

function adjustmentAttemptStorageKey(
  adminUserId: number,
  signature: string,
): string {
  return `${ADJUSTMENT_ATTEMPT_STORAGE_PREFIX}${adminUserId}:${signature}`;
}

function getStoredAdjustmentAttempt(
  adminUserId: number,
  signature: string,
): string | null {
  if (typeof window === "undefined") return null;
  try {
    return sessionStorage.getItem(
      adjustmentAttemptStorageKey(adminUserId, signature),
    );
  } catch {
    return null;
  }
}

function storeAdjustmentAttempt(
  adminUserId: number,
  signature: string,
  idempotencyKey: string,
): void {
  if (typeof window === "undefined") return;
  try {
    sessionStorage.setItem(
      adjustmentAttemptStorageKey(adminUserId, signature),
      idempotencyKey,
    );
  } catch {
    // 저장소가 차단된 환경에서도 현재 컴포넌트의 메모리 키로 재시도한다.
  }
}

function removeStoredAdjustmentAttempt(
  adminUserId: number,
  signature: string,
): void {
  if (typeof window === "undefined") return;
  try {
    sessionStorage.removeItem(
      adjustmentAttemptStorageKey(adminUserId, signature),
    );
  } catch {
    // 완료된 서버 요청 결과에는 영향을 주지 않는다.
  }
}

function formatPoint(value: number): string {
  return value.toLocaleString("ko-KR");
}

export default function AdminPointAdjustmentPanel({
  accessToken,
  adminUserId,
}: AdminPointAdjustmentPanelProps) {
  const { askConfirm, showToast } = useUI();
  const [selectedUser, setSelectedUser] = useState<AdminUserSummary | null>(
    null,
  );
  const [wallet, setWallet] = useState<WalletData | null>(null);
  const [walletLoading, setWalletLoading] = useState(false);
  const [walletError, setWalletError] = useState("");
  const [currencyType, setCurrencyType] = useState<PointCurrencyType>("FREE");
  const [mode, setMode] = useState<AdjustmentMode>("GRANT");
  const [adjustmentReason, setAdjustmentReason] = useState<
    AdminPointAdjustmentReason | ""
  >("");
  const [amount, setAmount] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [result, setResult] = useState<AdminPointAdjustmentData | null>(null);
  const [historyRefreshKey, setHistoryRefreshKey] = useState(0);
  const pendingAttempts = useRef(new Map<string, string>());
  const selectedUserId = useRef<number | null>(null);
  const walletRequestId = useRef(0);

  const selectUser = async (user: AdminUserSummary) => {
    const requestId = ++walletRequestId.current;
    selectedUserId.current = user.id;
    setSelectedUser(user);
    setWallet(null);
    setWalletError("");
    setWalletLoading(true);
    setResult(null);
    setErrorMessage("");
    setAmount("");
    setAdjustmentReason("");
    if (!accessToken) {
      setWalletLoading(false);
      setWalletError("관리자 로그인이 필요합니다.");
      return;
    }

    try {
      const selectedWallet = await getWalletByAdmin(accessToken, user.id);
      if (requestId === walletRequestId.current) setWallet(selectedWallet);
    } catch (requestError) {
      if (requestId !== walletRequestId.current) return;
      setWalletError(
        requestError instanceof ApiError
          ? requestError.message
          : "선택한 회원의 포인트 잔액을 불러오지 못했어요.",
      );
    } finally {
      if (requestId === walletRequestId.current) setWalletLoading(false);
    }
  };

  const executeAdjustment = async (payload: AdminPointAdjustmentInput) => {
    if (!accessToken || !selectedUser) {
      setErrorMessage("포인트를 조정할 회원을 먼저 선택해 주세요.");
      return;
    }

    const targetUser = selectedUser;
    const signature = normalizeAdjustmentPayload(payload);
    const attemptIdentity = adjustmentAttemptStorageKey(adminUserId, signature);
    const idempotencyKey =
      pendingAttempts.current.get(attemptIdentity) ??
      getStoredAdjustmentAttempt(adminUserId, signature) ??
      createIdempotencyKey();
    pendingAttempts.current.set(attemptIdentity, idempotencyKey);
    storeAdjustmentAttempt(adminUserId, signature, idempotencyKey);

    setSubmitting(true);
    setErrorMessage("");
    try {
      const adjusted = await adjustPointByAdmin(
        accessToken,
        payload,
        idempotencyKey,
      );
      pendingAttempts.current.delete(attemptIdentity);
      removeStoredAdjustmentAttempt(adminUserId, signature);
      setHistoryRefreshKey((current) => current + 1);
      if (selectedUserId.current !== payload.userId) {
        showToast(`${targetUser.nickname} 회원의 포인트를 조정했어요.`);
        return;
      }
      setResult(adjusted);
      setWallet({
        userId: adjusted.userId,
        paidPoint: adjusted.paidPoint,
        freePoint: adjusted.freePoint,
        balance: adjusted.balance,
        updatedAt: new Date().toISOString(),
      });
      setAmount("");
      setAdjustmentReason("");
      showToast(`${targetUser.nickname} 회원의 포인트를 조정했어요.`);
    } catch (requestError) {
      if (
        requestError instanceof ApiError &&
        requestError.code === "COMMON_IDEMPOTENCY_CONFLICT"
      ) {
        pendingAttempts.current.delete(attemptIdentity);
        removeStoredAdjustmentAttempt(adminUserId, signature);
      }
      const message =
        requestError instanceof ApiError
          ? requestError.message
          : "포인트 조정에 실패했어요. 같은 내용으로 다시 시도해 주세요.";
      if (selectedUserId.current === payload.userId) setErrorMessage(message);
      showToast(message, "err");
    } finally {
      setSubmitting(false);
    }
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setErrorMessage("");
    setResult(null);

    if (!selectedUser || !wallet) {
      setErrorMessage("포인트를 조정할 회원을 먼저 선택해 주세요.");
      return;
    }

    const parsedAmount = Number(amount);
    if (!Number.isSafeInteger(parsedAmount) || parsedAmount < 1) {
      setErrorMessage("조정 포인트를 1 이상의 정수로 입력해 주세요.");
      return;
    }

    if (!adjustmentReason) {
      setErrorMessage("포인트 조정 사유를 선택해 주세요.");
      return;
    }

    const signedAmount = mode === "DEDUCT" ? -parsedAmount : parsedAmount;
    const payload: AdminPointAdjustmentInput = {
      userId: selectedUser.id,
      currencyType,
      amount: signedAmount,
      adjustmentReason,
    };
    const currentBalance =
      currencyType === "FREE" ? wallet.freePoint : wallet.paidPoint;
    const action = mode === "DEDUCT" ? "차감" : "지급";
    askConfirm({
      icon: mode === "DEDUCT" ? "remove_circle" : "add_circle",
      title: `포인트를 ${action}할까요?`,
      body: `${selectedUser.nickname}(${selectedUser.email}) 회원의 ${currencyType === "FREE" ? "무상" : "유상"} 포인트 ${formatPoint(parsedAmount)}P를 ${action}합니다. 사유: ${getAdminPointAdjustmentReasonLabel(adjustmentReason)}. 현재 잔액은 ${formatPoint(currentBalance)}P입니다.`,
      ok: `${action}하기`,
      danger: mode === "DEDUCT",
      onOk: () => void executeAdjustment(payload),
    });
  };

  const formDisabled =
    submitting || walletLoading || !accessToken || !selectedUser || !wallet;

  return (
    <div className="space-y-4">
      <AdminUserPicker
        accessToken={accessToken}
        selectedUserId={selectedUser?.id}
        onSelect={(user) => void selectUser(user)}
        disabled={submitting}
      />

      <div className="grid gap-4 lg:grid-cols-[minmax(0,1.1fr)_minmax(280px,.9fr)]">
        <form
          noValidate
          onSubmit={handleSubmit}
          className="rounded-[18px] bg-white p-6 shadow-card"
        >
          <div className="mb-5">
            <h2 className="text-lg font-extrabold">회원 포인트 조정</h2>
            <p className="mt-1 text-sm leading-6 text-sub">
              지급과 차감은 모두 원장에 기록되며 잔액은 음수가 될 수 없습니다.
            </p>
          </div>

          {selectedUser ? (
            <div className="mb-5 rounded-xl bg-[#f6f7f1] p-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <div className="font-extrabold">
                    {selectedUser.nickname}{" "}
                    <span className="text-sm text-sub">#{selectedUser.id}</span>
                  </div>
                  <div className="mt-1 text-sm text-sub">
                    {selectedUser.name} · {selectedUser.email}
                  </div>
                </div>
                {wallet && (
                  <div className="text-right">
                    <div className="text-xs font-bold text-sub">총 포인트</div>
                    <div className="mt-0.5 text-xl font-extrabold text-brand-text">
                      {formatPoint(wallet.balance)}P
                    </div>
                  </div>
                )}
              </div>
              {walletLoading && (
                <p className="mt-3 text-sm text-sub">잔액을 불러오고 있어요.</p>
              )}
              {walletError && (
                <p role="alert" className="mt-3 text-sm font-bold text-danger">
                  {walletError}
                </p>
              )}
              {wallet && (
                <div className="mt-3 grid grid-cols-2 gap-2 text-sm">
                  <div className="rounded-lg bg-white px-3 py-2">
                    <span className="text-sub">유상</span>{" "}
                    <strong className="float-right">
                      {formatPoint(wallet.paidPoint)}P
                    </strong>
                  </div>
                  <div className="rounded-lg bg-white px-3 py-2">
                    <span className="text-sub">무상</span>{" "}
                    <strong className="float-right">
                      {formatPoint(wallet.freePoint)}P
                    </strong>
                  </div>
                </div>
              )}
            </div>
          ) : (
            <div className="mb-5 rounded-xl bg-[#f6f7f1] px-4 py-6 text-center text-sm text-sub">
              위 목록에서 포인트를 조정할 회원을 선택해 주세요.
            </div>
          )}

          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <label
              htmlFor="admin-point-currency"
              className="text-sm font-bold text-sub"
            >
              포인트 종류
              <select
                id="admin-point-currency"
                value={currencyType}
                onChange={(event) =>
                  setCurrencyType(event.target.value as PointCurrencyType)
                }
                className="mt-2 w-full rounded-xl border border-line bg-white px-3.5 py-3 text-ink outline-none focus:border-brand"
                disabled={formDisabled}
              >
                <option value="FREE">무상 포인트</option>
                <option value="PAID">유상 포인트</option>
              </select>
            </label>

            <label
              htmlFor="admin-point-mode"
              className="text-sm font-bold text-sub"
            >
              조정 방식
              <select
                id="admin-point-mode"
                value={mode}
                onChange={(event) => {
                  setMode(event.target.value as AdjustmentMode);
                  setAdjustmentReason("");
                }}
                className="mt-2 w-full rounded-xl border border-line bg-white px-3.5 py-3 text-ink outline-none focus:border-brand"
                disabled={formDisabled}
              >
                <option value="GRANT">지급</option>
                <option value="DEDUCT">차감</option>
              </select>
            </label>

            <label
              htmlFor="admin-point-reason"
              className="text-sm font-bold text-sub"
            >
              조정 사유
              <select
                id="admin-point-reason"
                value={adjustmentReason}
                onChange={(event) =>
                  setAdjustmentReason(
                    event.target.value as AdminPointAdjustmentReason | "",
                  )
                }
                className="mt-2 w-full rounded-xl border border-line bg-white px-3.5 py-3 text-ink outline-none focus:border-brand"
                disabled={formDisabled}
                required
              >
                <option value="">사유 선택</option>
                {(mode === "GRANT"
                  ? ADMIN_POINT_GRANT_REASONS
                  : ADMIN_POINT_DEDUCT_REASONS
                ).map((reason) => (
                  <option key={reason} value={reason}>
                    {ADMIN_POINT_ADJUSTMENT_REASON_LABELS[reason]}
                  </option>
                ))}
              </select>
            </label>

            <label
              htmlFor="admin-point-amount"
              className="text-sm font-bold text-sub"
            >
              조정 포인트
              <div className="relative mt-2">
                <input
                  id="admin-point-amount"
                  type="number"
                  min="1"
                  step="1"
                  inputMode="numeric"
                  value={amount}
                  onChange={(event) => setAmount(event.target.value)}
                  placeholder="예: 1000"
                  className="w-full rounded-xl border border-line bg-white px-3.5 py-3 pr-10 text-ink outline-none focus:border-brand"
                  disabled={formDisabled}
                  required
                />
                <span className="absolute right-3.5 top-1/2 -translate-y-1/2 text-sm font-bold text-sub">
                  P
                </span>
              </div>
            </label>
          </div>

          {errorMessage && (
            <p
              role="alert"
              className="mt-4 rounded-xl bg-danger-soft px-4 py-3 text-sm font-bold text-danger"
            >
              {errorMessage}
            </p>
          )}

          <button
            type="submit"
            disabled={formDisabled}
            className={`mt-5 w-full rounded-xl px-5 py-3.5 font-extrabold text-white transition-colors ${
              formDisabled
                ? "cursor-not-allowed bg-[#b9c1b4]"
                : mode === "DEDUCT"
                  ? "cursor-pointer bg-danger hover:bg-[#a64729]"
                  : "cursor-pointer bg-brand hover:bg-brand-dark"
            }`}
          >
            {submitting
              ? "처리 중..."
              : mode === "DEDUCT"
                ? "포인트 차감"
                : "포인트 지급"}
          </button>
        </form>

        <section
          className="rounded-[18px] bg-white p-6 shadow-card"
          aria-live="polite"
        >
          <h2 className="text-lg font-extrabold">최근 조정 결과</h2>
          {result ? (
            <div className="mt-5 space-y-3 text-sm">
              <div className="flex justify-between gap-3 border-b border-line pb-3">
                <span className="text-sub">회원</span>
                <strong>
                  {selectedUser?.nickname} #{result.userId}
                </strong>
              </div>
              <div className="flex justify-between gap-3 border-b border-line pb-3">
                <span className="text-sub">조정 내용</span>
                <strong
                  className={
                    result.amount < 0 ? "text-danger" : "text-brand-text"
                  }
                >
                  {result.currencyType === "FREE" ? "무상" : "유상"}{" "}
                  {result.amount > 0 ? "+" : ""}
                  {formatPoint(result.amount)}P
                </strong>
              </div>
              <div className="flex justify-between gap-3 border-b border-line pb-3">
                <span className="text-sub">조정 사유</span>
                <strong>
                  {getAdminPointAdjustmentReasonLabel(result.adjustmentReason)}
                </strong>
              </div>
              <div className="flex justify-between gap-3 border-b border-line pb-3">
                <span className="text-sub">유상 잔액</span>
                <strong>{formatPoint(result.paidPoint)}P</strong>
              </div>
              <div className="flex justify-between gap-3 border-b border-line pb-3">
                <span className="text-sub">무상 잔액</span>
                <strong>{formatPoint(result.freePoint)}P</strong>
              </div>
              <div className="flex justify-between gap-3">
                <span className="text-sub">총 잔액</span>
                <strong className="text-base text-brand-text">
                  {formatPoint(result.balance)}P
                </strong>
              </div>
              <p className="pt-2 text-xs text-sub">
                원장 번호 #{result.transactionId}
              </p>
            </div>
          ) : (
            <div className="mt-5 rounded-xl bg-[#f6f7f1] px-4 py-8 text-center text-sm leading-6 text-sub">
              포인트를 조정하면 처리 결과와
              <br />
              회원의 최신 잔액이 표시됩니다.
            </div>
          )}
        </section>
      </div>

      <AdminPointAdjustmentHistory
        accessToken={accessToken}
        selectedUserId={selectedUser?.id}
        refreshKey={historyRefreshKey}
      />
    </div>
  );
}
