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
  const { state } = useStore();
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
        orderName: pendingPayment.chargeProductName,
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

  return (
    <div className="container max-w-[900px]">
      <Link href="/my/points" className="text-sm font-semibold text-sub">
        ← 포인트
      </Link>
      <h1 className="mb-2 mt-3.5 text-2xl font-extrabold">포인트 충전</h1>
      <div className="mb-[22px] rounded-[13px] bg-gold-soft px-4 py-[13px] text-sm font-bold text-gold-text">
        <span className="material-symbols-outlined text-base">light_mode</span>{" "}
        Toss Payments 테스트 결제창을 사용해요. 실제 결제가 발생하지 않아요.
      </div>

      {paymentError && (
        <p
          role="alert"
          className="mb-4 rounded-xl bg-[#fff4ef] px-3 py-2.5 text-center text-sm font-bold text-danger"
        >
          {paymentError}
        </p>
      )}

      <form
        noValidate
        onSubmit={(event) => void startPayment(event)}
        className="rounded-[18px] bg-white px-6 py-7 shadow-card"
      >
        <label htmlFor="charge-amount" className="block text-base font-extrabold">
          충전할 포인트
        </label>
        <div className="relative mt-3">
          <input
            id="charge-amount"
            type="number"
            inputMode="numeric"
            min={MIN_CHARGE_AMOUNT}
            max={MAX_CHARGE_AMOUNT}
            step={CHARGE_AMOUNT_UNIT}
            value={amountInput}
            disabled={processing}
            onChange={(event) => {
              setAmountInput(event.target.value);
              setAmountError("");
            }}
            aria-describedby="charge-amount-policy"
            aria-invalid={Boolean(amountError)}
            className="w-full rounded-xl border border-line px-4 py-3 pr-10 text-right text-xl font-extrabold outline-none focus:border-brand disabled:bg-gray-50"
          />
          <span className="absolute right-4 top-1/2 -translate-y-1/2 font-bold text-sub">P</span>
        </div>
        <p id="charge-amount-policy" className="mt-2 text-sm text-sub">
          1원 = 1P · 최소 1,000P · 최대 300,000P · 10P 단위
        </p>
        {amountError && (
          <p role="alert" className="mt-2 text-sm font-bold text-danger">
            {amountError}
          </p>
        )}

        <div className="mt-6 rounded-xl bg-[#f8faf8] px-4 py-4 text-sm">
          <div className="flex items-center justify-between">
            <span className="text-sub">결제 금액</span>
            <strong>{fmt(previewAmount)}원</strong>
          </div>
          <div className="mt-2 flex items-center justify-between">
            <span className="text-sub">충전 포인트</span>
            <strong>{fmt(previewAmount)}P</strong>
          </div>
        </div>

        <button
          type="submit"
          disabled={processing}
          className="mt-6 w-full cursor-pointer rounded-xl bg-brand px-5 py-3.5 font-extrabold text-white disabled:cursor-wait disabled:opacity-60"
        >
          {processing
            ? "결제창을 여는 중..."
            : `${fmt(previewAmount)}P 충전하기`}
        </button>
      </form>
    </div>
  );
}
