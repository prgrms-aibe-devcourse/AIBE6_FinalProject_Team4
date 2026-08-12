"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { ApiError } from "@/lib/api";
import { MarketListing, getMarketListing } from "@/lib/card-market-api";

const POINT = new Intl.NumberFormat("ko-KR");

export default function MarketListingDetailPage() {
  const params = useParams<{ listingId: string }>();
  const listingId = Number(params.listingId);
  const [listing, setListing] = useState<MarketListing | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    const controller = new AbortController();
    if (!Number.isInteger(listingId) || listingId < 1) {
      setError("매물 정보를 찾을 수 없어요.");
      return () => controller.abort();
    }
    void getMarketListing(listingId, controller.signal)
      .then(setListing)
      .catch((requestError) =>
        setError(
          requestError instanceof ApiError
            ? requestError.message
            : "매물 정보를 불러오지 못했어요.",
        ),
      );
    return () => controller.abort();
  }, [listingId]);

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
            <div className="aspect-[3/4] overflow-hidden rounded-2xl bg-[#edf0e9] p-3">
              {listing.imageUrl ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img
                  src={listing.imageUrl}
                  alt={listing.cardName}
                  className="h-full w-full object-contain"
                />
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
                {POINT.format(listing.askingPrice)}P
              </p>
              <p className="mt-3 text-sm font-bold text-[#74806f]">
                {new Date(listing.expiresAt).toLocaleString("ko-KR")}까지 판매
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
    </main>
  );
}
