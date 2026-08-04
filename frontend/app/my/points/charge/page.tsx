"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { getChargeProducts, reportPaymentFailure, requestCharge } from "@/features/payment/api";
import type { ChargeProduct } from "@/features/payment/api";
import {
  getTossPaymentErrorCode,
  requestTossPayment,
  TOSS_PENDING_ORDER_STORAGE_KEY,
} from "@/features/payment/toss-payment";
import { ApiError } from "@/lib/api";
import { fmt, useStore } from "@/lib/store";

const tossClientKey = process.env.NEXT_PUBLIC_TOSS_CLIENT_KEY ?? "";

function createIdempotencyKey(operation: string): string {
  return `${operation}-${crypto.randomUUID()}`;
}

export default function Charge() {
  const router = useRouter();
  const { state } = useStore();
  const [products, setProducts] = useState<ChargeProduct[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [reloadKey, setReloadKey] = useState(0);
  const [processingProductId, setProcessingProductId] = useState<number | null>(
    null,
  );
  const [paymentError, setPaymentError] = useState("");

  useEffect(() => {
    if (!state.accessToken) return;

    const controller = new AbortController();
    setLoading(true);
    setError("");

    getChargeProducts(state.accessToken, controller.signal)
      .then(setProducts)
      .catch((requestError) => {
        if (
          requestError instanceof DOMException &&
          requestError.name === "AbortError"
        )
          return;
        setProducts([]);
        setError(
          requestError instanceof ApiError
            ? requestError.message
            : "충전 상품을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.",
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [reloadKey, state.accessToken]);

  const startPayment = async (product: ChargeProduct) => {
    if (!state.accessToken || processingProductId !== null) return;

    setProcessingProductId(product.id);
    setPaymentError("");
    let providerOrderId: string | null = null;
    try {
      const pendingPayment = await requestCharge(
        state.accessToken,
        product.id,
        createIdempotencyKey("charge"),
      );
      providerOrderId = pendingPayment.providerOrderId;
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
      setProcessingProductId(null);
    }
  };

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
        <p className="mb-4 rounded-xl bg-[#fff4ef] px-3 py-2.5 text-center text-sm font-bold text-danger">
          {paymentError}
        </p>
      )}

      {loading ? (
        <div className="rounded-[18px] bg-white py-14 text-center text-sm text-sub shadow-card">
          충전 상품을 불러오고 있어요.
        </div>
      ) : error ? (
        <div className="rounded-[18px] bg-white px-5 py-14 text-center text-sm text-sub shadow-card">
          <p>{error}</p>
          <button
            type="button"
            onClick={() => setReloadKey((current) => current + 1)}
            className="mt-4 cursor-pointer rounded-xl bg-brand px-5 py-2.5 font-bold text-white"
          >
            다시 시도
          </button>
        </div>
      ) : products.length === 0 ? (
        <div className="rounded-[18px] bg-white py-14 text-center text-sm text-sub shadow-card">
          지금은 구매할 수 있는 충전 상품이 없어요.
        </div>
      ) : (
        <div className="grid gap-4 [grid-template-columns:repeat(auto-fill,minmax(200px,1fr))]">
          {products.map((product) => (
            <button
              key={product.id}
              type="button"
              disabled={processingProductId !== null}
              onClick={() => void startPayment(product)}
              className="cursor-pointer rounded-[18px] border-2 border-transparent bg-white px-5 py-6 text-center shadow-card disabled:cursor-wait disabled:opacity-60"
            >
              <div>
                <span className="material-symbols-outlined text-[34px] text-gold-text">
                  monetization_on
                </span>
              </div>
              <div className="mb-0.5 mt-2 text-[22px] font-extrabold">
                {fmt(product.pointAmount)}P
              </div>
              <div className="font-bold text-sub">{fmt(product.price)}원</div>
              <div className="mt-1 text-xs text-faint">{product.name}</div>
              {processingProductId === product.id && (
                <div className="mt-2 text-xs font-bold text-brand">
                  결제창을 여는 중...
                </div>
              )}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
