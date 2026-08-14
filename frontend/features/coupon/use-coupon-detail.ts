"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { isAbortError } from "@/features/commerce/presentation";
import { CardData, getCard, purchaseCard } from "@/features/coupon/api";
import { ApiError } from "@/lib/api";
import { couponName } from "@/lib/coupon-label";
import { fmt, useStore } from "@/lib/store";
import { useUI } from "@/lib/ui";

export function useCouponDetail({
  id,
  requestedReturnTo,
}: {
  id: string;
  requestedReturnTo?: string | string[];
}) {
  const router = useRouter();
  const { state, hydrated, walletLoading, walletLoaded, refreshWallet, set } =
    useStore();
  const { showToast, askConfirm } = useUI();
  const cardId = Number(id);
  const returnValue = Array.isArray(requestedReturnTo)
    ? requestedReturnTo[0]
    : requestedReturnTo;
  const returnTo = returnValue?.startsWith("/cards") ? returnValue : "/cards";
  const [card, setCard] = useState<CardData | null>(null);
  const [owned, setOwned] = useState<number | null>(null);
  const [qty, setQty] = useState(1);
  const [celebrate, setCelebrate] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [purchasing, setPurchasing] = useState(false);

  useEffect(() => {
    if (!Number.isInteger(cardId) || cardId < 1) {
      setError("잘못된 쿠폰 주소예요.");
      setLoading(false);
      return;
    }
    if (!hydrated) return;
    const controller = new AbortController();
    setLoading(true);
    setError("");
    getCard(cardId, state.accessToken, controller.signal)
      .then((response) => {
        setCard(response);
        setOwned(response.ownedCount);
      })
      .catch((requestError) => {
        if (isAbortError(requestError)) return;
        setCard(null);
        setError(
          requestError instanceof ApiError
            ? requestError.message
            : "쿠폰을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.",
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [cardId, hydrated, state.accessToken]);

  const total = (card?.pointPrice ?? 0) * qty;
  const availableFreePoint = Math.max(state.wallet.free, 0);
  const availablePaidPoint = Math.max(state.wallet.paid, 0);
  const usedFreePoint = Math.min(availableFreePoint, total);
  const usedPaidPoint = total - usedFreePoint;
  const pointShortage = Math.max(0, usedPaidPoint - availablePaidPoint);
  const ring = `conic-gradient(#7CB342 ${Math.min(
    360,
    ((owned ?? 0) / (card?.requiredCountForExchange ?? 1)) * 360,
  )}deg,#eef0e6 0)`;

  const buy = () => {
    if (!card || !hydrated || !state.accessToken || owned === null) {
      showToast("쿠폰 구매는 로그인 후 이용할 수 있어요.", "err");
      return;
    }
    if (!walletLoaded) {
      showToast(
        walletLoading
          ? "포인트 잔액을 확인하고 있어요."
          : "포인트 잔액을 확인하지 못했어요. 잠시 후 다시 시도해 주세요.",
        "err",
      );
      return;
    }
    if (pointShortage > 0) {
      showToast(`사용 가능한 포인트가 ${fmt(pointShortage)}P 부족해요.`, "err");
      return;
    }
    askConfirm({
      icon: "eco",
      title: "쿠폰을 구매할까요?",
      ok: "구매하기",
      body: `${couponName(card.name)} ${qty}장 · 보너스 포인트 ${fmt(usedFreePoint)}P${usedPaidPoint > 0 ? `와 충전 포인트 ${fmt(usedPaidPoint)}P` : ""}를 사용해요.`,
      onOk: async () => {
        const currentOwned = owned;
        setPurchasing(true);
        try {
          const response = await purchaseCard(
            card.id,
            qty,
            state.accessToken!,
            crypto.randomUUID(),
          );
          await refreshWallet();
          setOwned(response.ownedCount);
          setQty(1);
          const reached =
            response.ownedCount >= card.requiredCountForExchange &&
            currentOwned < card.requiredCountForExchange;
          if (reached) {
            set((current) => ({ readyCards: current.readyCards + 1 }));
            setCelebrate(true);
          } else {
            showToast("쿠폰을 구매했어요! 🎟️");
          }
        } catch (purchaseError) {
          showToast(
            purchaseError instanceof ApiError
              ? purchaseError.message
              : "쿠폰을 구매하지 못했어요. 잠시 후 다시 시도해 주세요.",
            "err",
          );
        } finally {
          setPurchasing(false);
        }
      },
    });
  };

  return {
    card,
    owned,
    qty,
    setQty,
    celebrate,
    setCelebrate,
    loading,
    error,
    purchasing,
    total,
    usedFreePoint,
    usedPaidPoint,
    pointShortage,
    ring,
    returnTo,
    wallet: state.wallet,
    walletLoading,
    walletLoaded,
    buy,
    goToExchange: () => card && router.push(`/exchange/new?cardId=${card.id}`),
  };
}
