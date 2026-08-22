"use client";

import { useState } from "react";
import type { FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { reportPaymentFailure, requestCharge } from "@/features/payment/api";
import {
  getTossPaymentErrorCode,
  requestTossPayment,
  TOSS_PENDING_ORDER_STORAGE_KEY,
} from "@/features/payment/toss-payment";
import { ApiError } from "@/lib/api";
import { fmt, useStore } from "@/lib/store";

const tossClientKey = process.env.NEXT_PUBLIC_TOSS_CLIENT_KEY ?? "";
const MIN_CHARGE_AMOUNT = 1_000;
const MAX_CHARGE_AMOUNT = 300_000;
const CHARGE_AMOUNT_UNIT = 10;
const QUICK_CHARGE_AMOUNTS = [1_000, 5_000, 10_000, 50_000];

function createIdempotencyKey(operation: string): string {
  return `${operation}-${crypto.randomUUID()}`;
}

function validateChargeAmount(value: string): string {
  if (!/^\d+$/.test(value.trim())) return "충전할 포인트를 숫자로 입력해 주세요.";

  const amount = Number(value);
  if (!Number.isSafeInteger(amount) || amount < MIN_CHARGE_AMOUNT)
    return "최소 충전 금액은 1,000원이에요.";
  if (amount > MAX_CHARGE_AMOUNT)
    return "최대 충전 금액은 300,000원이에요.";
  if (amount % CHARGE_AMOUNT_UNIT !== 0)
    return "충전 금액은 10P 단위로 입력해 주세요.";
  return "";
}

export default function Charge() {
  const router = useRouter();
  const { state, balance, walletLoaded } = useStore();
  const [amountInput, setAmountInput] = useState("1000");
  const [amountError, setAmountError] = useState("");
  const [processing, setProcessing] = useState(false);
  const [paymentError, setPaymentError] = useState("");

  const startPayment = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!state.accessToken || processing) return;

    const validationMessage = validateChargeAmount(amountInput);
    if (validationMessage) {
      setAmountError(validationMessage);
      return;
    }

    const pointAmount = Number(amountInput);
    setAmountError("");
    setProcessing(true);
    setPaymentError("");
    let providerOrderId: string | null = null;
    try {
      const pendingPayment = await requestCharge(
        state.accessToken,
        pointAmount,
        createIdempotencyKey("charge"),
      );
      providerOrderId = pendingPayment.providerOrderId;
      sessionStorage.setItem(
        TOSS_PENDING_ORDER_STORAGE_KEY,
        pendingPayment.providerOrderId,
      );
      if (
        pendingPayment.cashAmount !== pointAmount ||
        pendingPayment.pointAmount !== pointAmount
      ) {
        try {
          await reportPaymentFailure(
            state.accessToken,
            pendingPayment.providerOrderId,
            "PAYMENT_AMOUNT_MISMATCH",
            `failure-${pendingPayment.providerOrderId}`,
          );
          sessionStorage.removeItem(TOSS_PENDING_ORDER_STORAGE_KEY);
        } catch {
          const query = new URLSearchParams({
            code: "PAYMENT_AMOUNT_MISMATCH",
            message: "요청한 충전 금액과 결제 금액이 달라 결제를 중단했어요.",
          });
          router.push(`/my/points/charge/fail?${query.toString()}`);
          return;
        }
        setPaymentError(
          "요청한 충전 금액과 결제 금액이 달라 결제를 진행하지 않았어요. 다시 시도해 주세요.",
        );
        return;
      }
      await requestTossPayment({
        clientKey: tossClientKey,
        orderId: pendingPayment.providerOrderId,
        orderName: `${fmt(pendingPayment.pointAmount)}P 충전`,
        amount: pendingPayment.cashAmount,
        customerEmail: state.user?.email,
        customerName: state.user?.nickname,
      });
    } catch (requestError) {
      const failureCode = getTossPaymentErrorCode(requestError);
      const canceled = failureCode === "PAY_PROCESS_CANCELED";
      if (providerOrderId) {
        try {
          await reportPaymentFailure(
            state.accessToken,
            providerOrderId,
            failureCode,
            `failure-${providerOrderId}`,
          );
          sessionStorage.removeItem(TOSS_PENDING_ORDER_STORAGE_KEY);
        } catch {
          const query = new URLSearchParams({
            code: failureCode,
            message: canceled
              ? "결제창을 닫아 결제가 취소됐어요."
              : "결제를 완료하지 못했어요.",
          });
          router.push(`/my/points/charge/fail?${query.toString()}`);
          return;
        }
      }
      setPaymentError(
        canceled
          ? "결제를 취소했어요. 결제 내역에도 실패로 반영했어요."
          : requestError instanceof ApiError || requestError instanceof Error
            ? requestError.message
            : "결제창을 여는 중 문제가 발생했어요. 잠시 후 다시 시도해 주세요.",
      );
    } finally {
      setProcessing(false);
    }
  };

  const validAmount = validateChargeAmount(amountInput) === "";
  const previewAmount = validAmount ? Number(amountInput) : 0;

  // 빠른 선택 칩은 현재 입력값에 더한다 — 최대 충전 한도를 넘지 않게 자른다.
  const addQuickAmount = (delta: number) => {
    const current = /^\d+$/.test(amountInput.trim()) ? Number(amountInput) : 0;
    setAmountInput(String(Math.min(MAX_CHARGE_AMOUNT, current + delta)));
    setAmountError("");
  };

  return (
    <div className="container max-w-[900px]">
      <Link href="/my/points" className="text-sm font-semibold text-sub">
        ← 포인트
      </Link>

      <div className="mb-5 mt-3.5 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-3.5">
          <span className="grid h-12 w-12 shrink-0 place-items-center rounded-2xl bg-gradient-to-br from-[#FFE9A6] to-[#FFD54F] text-gold-text shadow-[0_6px_16px_rgba(255,213,79,.35)]">
            <span aria-hidden className="material-symbols-outlined text-[26px]">
              add_card
            </span>
          </span>
          <div>
            <h1 className="text-2xl font-extrabold leading-tight">포인트 충전</h1>
            <p className="mt-0.5 text-[13.5px] text-sub">
              충전한 포인트로 상점 작물과 카드를 바로 구매할 수 있어요.
            </p>
          </div>
        </div>
        {walletLoaded && (
          <div className="flex w-full items-center justify-between gap-3 rounded-2xl border border-line bg-white px-4 py-2.5 sm:w-auto sm:flex-col sm:items-end sm:gap-0">
            <div className="text-[11.5px] font-bold text-sub">보유 포인트</div>
            <div className="text-lg font-extrabold leading-tight text-ink">
              {fmt(balance)}P
            </div>
          </div>
        )}
      </div>

      <div className="mb-[22px] flex items-start gap-3 rounded-[15px] border border-[#f4e3ac] bg-gold-soft px-4 py-3.5 text-gold-text">
        <span className="mt-px grid h-6 w-6 shrink-0 place-items-center rounded-full bg-gold/70">
          <span aria-hidden className="material-symbols-outlined text-base">
            science
          </span>
        </span>
        <p className="text-sm font-bold leading-[1.55]">
          Toss Payments 테스트 결제창을 사용해요. 실제 결제가 발생하지 않아요.
        </p>
      </div>

      {paymentError && (
        <p
          role="alert"
          className="mb-4 rounded-xl border border-[#f3d9cd] bg-danger-soft px-3 py-2.5 text-center text-sm font-bold text-danger"
        >
          {paymentError}
        </p>
      )}

      <form
        noValidate
        onSubmit={(event) => void startPayment(event)}
        className="grid animate-upIn items-start gap-5 lg:grid-cols-[1.32fr_1fr]"
      >
        <section className="rounded-[20px] border border-line bg-white px-6 py-6 shadow-card">
          <label htmlFor="charge-amount" className="block text-base font-extrabold">
            충전할 포인트
          </label>

          <div
            className={`relative mt-3 rounded-2xl border-[1.5px] bg-[#fbfdf9] transition ${
              amountError
                ? "border-danger ring-4 ring-danger/10"
                : "border-line focus-within:border-brand focus-within:bg-white focus-within:ring-4 focus-within:ring-brand/15"
            } ${processing ? "opacity-60" : ""}`}
          >
            <input
              id="charge-amount"
              type="text"
              inputMode="numeric"
              autoComplete="off"
              value={amountInput === "" ? "" : fmt(Number(amountInput))}
              disabled={processing}
              placeholder="0"
              onChange={(event) => {
                setAmountInput(event.target.value.replace(/[^\d]/g, ""));
                setAmountError("");
              }}
              aria-describedby="charge-amount-policy"
              aria-invalid={Boolean(amountError)}
              className="w-full bg-transparent px-5 py-4 pr-12 text-right text-[28px] font-extrabold tracking-tight outline-none placeholder:text-faint disabled:cursor-wait"
            />
            <span className="pointer-events-none absolute right-5 top-1/2 -translate-y-1/2 text-lg font-extrabold text-faint">
              P
            </span>
          </div>

          <div className="mt-3 flex flex-wrap gap-2">
            {QUICK_CHARGE_AMOUNTS.map((quickAmount) => (
              <button
                key={quickAmount}
                type="button"
                disabled={processing}
                onClick={() => addQuickAmount(quickAmount)}
                className="cursor-pointer rounded-full border-[1.5px] border-line bg-white px-3.5 py-[7px] text-[13px] font-bold text-[#6d7a68] transition-colors hover:border-brand hover:bg-brand-soft hover:text-brand-text disabled:cursor-wait disabled:opacity-50"
              >
                +{fmt(quickAmount)}
              </button>
            ))}
            <button
              type="button"
              disabled={processing}
              onClick={() => {
                setAmountInput("");
                setAmountError("");
              }}
              className="cursor-pointer rounded-full px-3 py-[7px] text-[13px] font-bold text-sub transition-colors hover:text-ink disabled:cursor-wait disabled:opacity-50"
            >
              초기화
            </button>
          </div>

          <p id="charge-amount-policy" className="mt-3 text-[13px] text-sub">
            1원 = 1P · 최소 1,000P · 최대 300,000P · 10P 단위
          </p>
          {amountError && (
            <p role="alert" className="mt-2 text-sm font-bold text-danger">
              {amountError}
            </p>
          )}

          <div className="mt-5 flex items-center gap-3 rounded-xl border border-dashed border-line bg-[#fafcf7] px-4 py-3">
            <span
              aria-hidden
              className="material-symbols-outlined text-[22px] text-brand"
            >
              credit_card
            </span>
            <div className="text-[13px] leading-snug">
              <b className="font-extrabold">Toss Payments</b>
              <p className="text-sub">카드·간편결제 창에서 결제를 진행해요.</p>
            </div>
          </div>
        </section>

        <aside className="overflow-hidden rounded-[20px] border border-line bg-white shadow-card lg:sticky lg:top-[78px]">
          <div className="bg-gradient-to-br from-[#FFF6D6] to-[#FFE9A6] px-5 py-[18px]">
            <div className="text-[12.5px] font-bold text-gold-text">
              충전될 포인트
            </div>
            <div className="mt-1.5 flex items-center gap-2 text-[#6b5500]">
              <span className="grid h-7 w-7 shrink-0 place-items-center rounded-full bg-gold text-[13px] font-extrabold text-gold-text">
                P
              </span>
              <span className="text-[30px] font-extrabold leading-none tracking-tight">
                {fmt(previewAmount)}P
              </span>
            </div>
          </div>

          <div className="px-5 py-[18px]">
            <div className="flex items-center justify-between text-sm">
              <span className="text-sub">결제 금액</span>
              <strong className="text-[15px]">{fmt(previewAmount)}원</strong>
            </div>
            {walletLoaded && (
              <>
                <div className="mt-2.5 flex items-center justify-between text-sm">
                  <span className="text-sub">현재 잔액</span>
                  <span className="font-bold">{fmt(balance)}P</span>
                </div>
                <div className="mt-2.5 flex items-center justify-between border-t border-dashed border-line pt-2.5 text-sm">
                  <span className="text-sub">충전 후 잔액</span>
                  <strong className="text-[15px] text-brand-text">
                    {fmt(balance + previewAmount)}P
                  </strong>
                </div>
              </>
            )}

            <button
              type="submit"
              disabled={processing}
              className="mt-5 flex w-full cursor-pointer items-center justify-center gap-1.5 rounded-2xl bg-gradient-to-r from-brand to-brand-dark px-5 py-3.5 font-extrabold text-white shadow-[0_8px_20px_rgba(124,179,66,.28)] disabled:cursor-wait disabled:opacity-60"
            >
              <span
                aria-hidden
                className={`material-symbols-outlined text-[19px] ${processing ? "animate-spin" : ""}`}
              >
                {processing ? "progress_activity" : "bolt"}
              </span>
              {processing
                ? "결제창을 여는 중..."
                : `${fmt(previewAmount)}P 충전하기`}
            </button>
            <p className="mt-2.5 text-center text-[12px] text-faint">
              버튼을 누르면 Toss 결제창이 열려요.
            </p>
          </div>
        </aside>
      </form>
    </div>
  );
}
