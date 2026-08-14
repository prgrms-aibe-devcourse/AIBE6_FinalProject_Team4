"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import {
  commerceErrorMessage,
  formatCommerceDateTime,
  formatPoint,
  isAbortError,
} from "@/features/commerce/presentation";
import { MarketListing, getMarketListing } from "@/features/card-market/api";

export default function MarketListingDetailPage() {
  const params = useParams<{ listingId: string }>();
  const listingId = Number(params.listingId);
  const [listing, setListing] = useState<MarketListing | null>(null);
  const [error, setError] = useState("");
  const [isImageOpen, setIsImageOpen] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    setError("");
    if (!Number.isInteger(listingId) || listingId < 1) {
      setError("매물 정보를 찾을 수 없어요.");
      return () => controller.abort();
    }
    void getMarketListing(listingId, controller.signal)
      .then(setListing)
      .catch((requestError) => {
        if (isAbortError(requestError)) return;
        setError(
          commerceErrorMessage(requestError, "매물 정보를 불러오지 못했어요."),
        );
      });
    return () => controller.abort();
  }, [listingId]);

  useEffect(() => {
    if (!isImageOpen) return;
    const closeWithEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") setIsImageOpen(false);
    };
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", closeWithEscape);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", closeWithEscape);
    };
  }, [isImageOpen]);

  return (
    <main className="min-h-screen bg-[#f4f6f1] px-4 py-14 text-[#263023]">
      <div className="mx-auto max-w-4xl">
        <Link
          href="/card-market"
          className="inline-flex items-center gap-2 text-sm font-black text-[#5d6e57]"
        >
          <span className="material-symbols-outlined">arrow_back</span>
          카드 거래소
        </Link>
        {error ? (
          <div className="mt-8 rounded-3xl bg-white p-14 text-center font-bold text-[#a64d35]">
            {error}
          </div>
        ) : !listing ? (
          <div className="mt-8 rounded-3xl bg-white p-14 text-center font-bold text-[#7a8476]">
            매물 정보를 불러오는 중...
          </div>
        ) : (
          <section className="mt-8 grid gap-8 rounded-[32px] border border-[#dce4d7] bg-white p-6 shadow-xl md:grid-cols-[320px_1fr] md:p-10">
            <div className="relative aspect-[3/4] overflow-hidden rounded-2xl bg-[#edf0e9] p-3">
              {listing.imageUrl ? (
                <button
                  type="button"
                  onClick={() => setIsImageOpen(true)}
                  aria-label={`${listing.cardName} 일러스트 크게 보기`}
                  className="group relative h-full w-full cursor-zoom-in"
                >
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img
                    src={listing.imageUrl}
                    alt={listing.cardName}
                    className="h-full w-full object-contain transition duration-300 group-hover:scale-[1.02]"
                  />
                  <span className="absolute bottom-3 right-3 inline-flex items-center gap-1 rounded-full bg-black/65 px-3 py-2 text-xs font-black text-white opacity-90 backdrop-blur-sm transition group-hover:bg-black/80">
                    <span className="material-symbols-outlined text-base">
                      zoom_in
                    </span>
                    크게 보기
                  </span>
                </button>
              ) : null}
            </div>
            <div className="flex flex-col justify-center">
              <p className="text-xs font-black tracking-[0.18em] text-[#96751d]">
                {listing.assetType === "GOLDEN_RARE"
                  ? "GOLDEN RARE"
                  : "HYPER RARE"}
              </p>
              <h1 className="mt-2 text-4xl font-black">{listing.cardName}</h1>
              <p className="mt-4 text-sm text-[#74806f]">
                판매자 {listing.sellerNickname} · 가격 제안{" "}
                {listing.activeOfferCount}개
              </p>
              <p className="mt-8 text-3xl font-black text-[#765b12]">
                {formatPoint(listing.askingPrice)}
              </p>
              <p className="mt-3 text-sm font-bold text-[#74806f]">
                {formatCommerceDateTime(listing.expiresAt)}까지 판매
              </p>
              <Link
                href={`/card-market?keyword=${encodeURIComponent(listing.cardName)}`}
                className="mt-8 rounded-2xl bg-[#344b32] px-5 py-4 text-center font-black text-white"
              >
                거래소에서 구매·가격 제안하기
              </Link>
            </div>
          </section>
        )}
      </div>
      {isImageOpen && listing?.imageUrl ? (
        <div
          role="dialog"
          aria-modal="true"
          aria-label={`${listing.cardName} 원본 일러스트`}
          className="fixed inset-0 z-[100] flex items-center justify-center bg-[#10150f]/90 p-4 backdrop-blur-md md:p-10"
          onMouseDown={() => setIsImageOpen(false)}
        >
          <div
            className="relative flex h-full w-full max-w-5xl items-center justify-center"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <button
              type="button"
              onClick={() => setIsImageOpen(false)}
              aria-label="확대 이미지 닫기"
              className="absolute right-0 top-0 z-10 grid h-12 w-12 place-items-center rounded-full bg-white/15 text-white backdrop-blur-md transition hover:bg-white/25"
            >
              <span className="material-symbols-outlined text-3xl">close</span>
            </button>
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src={listing.imageUrl}
              alt={`${listing.cardName} 원본 일러스트`}
              className="max-h-[calc(100vh-5rem)] max-w-full select-none object-contain drop-shadow-[0_24px_50px_rgba(0,0,0,.55)]"
            />
            <p className="absolute bottom-0 left-1/2 -translate-x-1/2 whitespace-nowrap rounded-full bg-black/45 px-4 py-2 text-sm font-black text-white/90 backdrop-blur-md">
              {listing.cardName}
            </p>
          </div>
        </div>
      ) : null}
    </main>
  );
}
