"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useRef, useState } from "react";
import { confirmPayment, PaymentData } from "@/features/payment/api";
import { TOSS_PENDING_ORDER_STORAGE_KEY } from "@/features/payment/toss-payment";
import { ApiError } from "@/lib/api";
import { fmt, useStore } from "@/lib/store";

function getConfirmIdempotencyKey(orderId: string): string {
  const storageKey = `kwb_toss_confirm_idempotency:${orderId}`;
  const savedKey = sessionStorage.getItem(storageKey);
  if (savedKey) return savedKey;

  const idempotencyKey = `confirm-${crypto.randomUUID()}`;
  sessionStorage.setItem(storageKey, idempotencyKey);
  return idempotencyKey;
}

function TossPaymentSuccessContent() {
  const searchParams = useSearchParams();
  const { state, hydrated, refreshWallet } = useStore();
  const startedRequest = useRef("");
  const [payment, setPayment] = useState<PaymentData | null>(null);
  const [error, setError] = useState("");

  const paymentKey = searchParams.get("paymentKey") ?? "";
  const orderId = searchParams.get("orderId") ?? "";
  const amountValue = searchParams.get("amount") ?? "";

  useEffect(() => {
    if (!hydrated) return;
    if (!state.accessToken) {
      setError(
        "로그인 정보를 확인할 수 없어요. 다시 로그인한 뒤 결제 내역을 확인해 주세요.",
      );
      return;
    }

    const amount = Number(amountValue);
    if (
      !paymentKey ||
      !orderId ||
      !Number.isSafeInteger(amount) ||
      amount <= 0
    ) {
      setError("결제 승인 정보가 올바르지 않아요. 결제 내역을 확인해 주세요.");
      return;
    }

    const requestIdentity = `${paymentKey}:${orderId}:${amount}`;
    if (startedRequest.current === requestIdentity) return;
    startedRequest.current = requestIdentity;

    const finalizePayment = async () => {
      try {
        const result = await confirmPayment(
          state.accessToken!,
          { providerOrderId: orderId, paymentKey, amount },
          getConfirmIdempotencyKey(orderId),
        );
        setPayment(result);
        sessionStorage.removeItem(TOSS_PENDING_ORDER_STORAGE_KEY);
        if (result.status === "COMPLETED") {
          await refreshWallet();
          return;
        }
        setError(
          result.message ||
            "결제가 승인되지 않았어요. 결제 내역을 확인해 주세요.",
        );
      } catch (requestError) {
        startedRequest.current = "";
        setError(
          requestError instanceof ApiError
            ? requestError.message
            : "결제 승인 중 문제가 발생했어요. 이 페이지에서 다시 시도해 주세요.",
        );
      }
    };

    void finalizePayment();
  }, [
    amountValue,
    hydrated,
    orderId,
    paymentKey,
    refreshWallet,
    state.accessToken,
  ]);

  if (!error && !payment) {
    return (
      <div className="container max-w-[560px] py-16 text-center">
        <div className="rounded-[18px] bg-white px-6 py-14 shadow-card">
          <span className="material-symbols-outlined animate-pulse text-5xl text-brand">
            payments
          </span>
          <h1 className="mt-4 text-xl font-extrabold">
            결제를 승인하고 있어요
          </h1>
          <p className="mt-2 text-sm text-sub">
            창을 닫지 말고 잠시만 기다려 주세요.
          </p>
        </div>
      </div>
    );
  }

  const succeeded = payment?.status === "COMPLETED";

  return (
    <div className="container max-w-[560px] py-16 text-center">
      <div className="rounded-[18px] bg-white px-6 py-12 shadow-card">
        <span
          className={`material-symbols-outlined text-5xl ${succeeded ? "text-brand" : "text-danger"}`}
        >
          {succeeded ? "check_circle" : "error"}
        </span>
        <h1 className="mt-4 text-2xl font-extrabold">
          {succeeded ? "포인트 충전 완료" : "결제 승인을 완료하지 못했어요"}
        </h1>
        {succeeded && payment ? (
          <p className="mt-3 text-sub">
            {fmt(payment.cashAmount)}원 결제로{" "}
            <strong className="text-brand">{fmt(payment.pointAmount)}P</strong>
            가 충전됐어요.
          </p>
        ) : (
          <p className="mt-3 text-sm text-sub">{error}</p>
        )}
        <div className="mt-7 flex justify-center gap-2">
          {!succeeded && (
            <button
              type="button"
              onClick={() => window.location.reload()}
              className="cursor-pointer rounded-xl border border-line px-5 py-3 font-bold text-sub"
            >
              다시 확인
            </button>
          )}
          <Link
            href={succeeded ? "/my/points" : "/my/points/payments"}
            className="rounded-xl bg-brand px-5 py-3 font-bold text-white"
          >
            {succeeded ? "포인트 내역 보기" : "결제 내역 보기"}
          </Link>
        </div>
      </div>
    </div>
  );
}

export default function TossPaymentSuccessPage() {
  return (
    <Suspense fallback={null}>
      <TossPaymentSuccessContent />
    </Suspense>
  );
}
