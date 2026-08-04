"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useRef, useState } from "react";
import { reportPaymentFailure } from "@/features/payment/api";
import { TOSS_PENDING_ORDER_STORAGE_KEY } from "@/features/payment/toss-payment";
import { ApiError } from "@/lib/api";
import { useStore } from "@/lib/store";

function TossPaymentFailContent() {
  const searchParams = useSearchParams();
  const code = searchParams.get("code") ?? "";
  const providerMessage = searchParams.get("message") ?? "";
  const canceled = code === "PAY_PROCESS_CANCELED";
  const { state, hydrated } = useStore();
  const started = useRef(false);
  const [syncStatus, setSyncStatus] = useState<
    "idle" | "syncing" | "completed" | "failed"
  >("idle");
  const [syncError, setSyncError] = useState("");

  useEffect(() => {
    if (!hydrated || started.current || !code) return;
    const providerOrderId = sessionStorage.getItem(
      TOSS_PENDING_ORDER_STORAGE_KEY,
    );
    if (!providerOrderId || !state.accessToken) {
      setSyncStatus("failed");
      setSyncError("결제 내역을 자동으로 정리하지 못했어요.");
      return;
    }

    started.current = true;
    setSyncStatus("syncing");
    reportPaymentFailure(
      state.accessToken,
      providerOrderId,
      code,
      `failure-${providerOrderId}`,
    )
      .then(() => {
        sessionStorage.removeItem(TOSS_PENDING_ORDER_STORAGE_KEY);
        setSyncStatus("completed");
      })
      .catch((requestError) => {
        setSyncStatus("failed");
        setSyncError(
          requestError instanceof ApiError
            ? requestError.message
            : "결제 내역을 자동으로 정리하지 못했어요.",
        );
      });
  }, [code, hydrated, state.accessToken]);

  return (
    <div className="container max-w-[560px] py-16 text-center">
      <div className="rounded-[18px] bg-white px-6 py-12 shadow-card">
        <span className="material-symbols-outlined text-5xl text-danger">
          {canceled ? "cancel" : "error"}
        </span>
        <h1 className="mt-4 text-2xl font-extrabold">
          {canceled ? "결제를 취소했어요" : "결제를 완료하지 못했어요"}
        </h1>
        <p className="mt-3 text-sm text-sub">
          {providerMessage ||
            (canceled
              ? "결제 승인이 진행되지 않아 포인트가 충전되지 않았어요."
              : "잠시 후 다시 시도해 주세요.")}
        </p>
        {code && <p className="mt-2 text-xs text-faint">오류 코드: {code}</p>}
        {syncStatus === "syncing" && (
          <p className="mt-3 text-xs font-bold text-sub">
            결제 내역을 정리하고 있어요.
          </p>
        )}
        {syncStatus === "completed" && (
          <p className="mt-3 text-xs font-bold text-brand">
            결제 내역에도 {canceled ? "취소" : "실패"}로 반영됐어요.
          </p>
        )}
        {syncStatus === "failed" && (
          <p className="mt-3 text-xs font-bold text-danger">{syncError}</p>
        )}
        <div className="mt-7 flex justify-center gap-2">
          <Link
            href="/my/points/payments"
            className="rounded-xl border border-line px-5 py-3 font-bold text-sub"
          >
            결제 내역
          </Link>
          <Link
            href="/my/points/charge"
            className="rounded-xl bg-brand px-5 py-3 font-bold text-white"
          >
            다시 충전하기
          </Link>
        </div>
      </div>
    </div>
  );
}

export default function TossPaymentFailPage() {
  return (
    <Suspense fallback={null}>
      <TossPaymentFailContent />
    </Suspense>
  );
}
