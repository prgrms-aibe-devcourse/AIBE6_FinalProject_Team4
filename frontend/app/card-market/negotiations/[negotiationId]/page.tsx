"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import CommerceCardImage from "@/features/commerce/CommerceCardImage";
import {
  commerceErrorMessage,
  formatCommerceDateTime,
  formatPoint,
  isAbortError,
} from "@/features/commerce/presentation";
import {
  MarketNegotiation,
  getMyMarketNegotiation,
} from "@/features/card-market/api";
import { useStore } from "@/lib/store";

export default function MarketNegotiationDetailPage() {
  const params = useParams<{ negotiationId: string }>();
  const negotiationId = Number(params.negotiationId);
  const { state, hydrated } = useStore();
  const [negotiation, setNegotiation] = useState<MarketNegotiation | null>(
    null,
  );
  const [error, setError] = useState("");

  useEffect(() => {
    if (!hydrated) return;
    setError("");
    if (!state.accessToken) {
      setError("로그인 후 가격 제안을 확인할 수 있어요.");
      return;
    }
    const controller = new AbortController();
    void getMyMarketNegotiation(
      negotiationId,
      state.accessToken,
      controller.signal,
    )
      .then(setNegotiation)
      .catch((requestError) => {
        if (isAbortError(requestError)) return;
        setError(
          commerceErrorMessage(requestError, "가격 제안을 불러오지 못했어요."),
        );
      });
    return () => controller.abort();
  }, [hydrated, negotiationId, state.accessToken]);

  return (
    <main className="min-h-screen bg-[#f4f6f1] px-4 py-14 text-[#263023]">
      <div className="mx-auto max-w-3xl">
        <Link
          href="/card-market?view=sent"
          className="inline-flex items-center gap-2 text-sm font-black text-[#5d6e57]"
        >
          <span className="material-symbols-outlined">arrow_back</span>
          가격 제안 목록
        </Link>
        {error ? (
          <div className="mt-8 rounded-3xl bg-white p-14 text-center font-bold text-[#a64d35]">
            {error}
          </div>
        ) : !negotiation ? (
          <div className="mt-8 rounded-3xl bg-white p-14 text-center font-bold text-[#7a8476]">
            가격 제안을 불러오는 중...
          </div>
        ) : (
          <section className="mt-8 rounded-[30px] border border-[#dce4d7] bg-white p-6 shadow-xl md:p-9">
            <div className="flex gap-5">
              <div className="h-36 w-24 overflow-hidden rounded-xl bg-[#edf0e9]">
                <CommerceCardImage
                  src={negotiation.imageUrl}
                  name={negotiation.cardName}
                />
              </div>
              <div>
                <p className="text-xs font-black text-[#7a8476]">
                  가격 제안 #{negotiation.id}
                </p>
                <h1 className="mt-1 text-3xl font-black">
                  {negotiation.cardName}
                </h1>
                <p className="mt-3 text-2xl font-black text-[#765b12]">
                  {formatPoint(negotiation.currentPrice)}
                </p>
                <p className="mt-2 text-sm font-bold text-[#667260]">
                  상태 {negotiation.status}
                </p>
              </div>
            </div>
            <h2 className="mt-9 text-lg font-black">제안 기록</h2>
            <ol className="mt-4 space-y-3">
              {negotiation.proposals.map((proposal) => (
                <li
                  key={proposal.id}
                  className="flex items-center justify-between rounded-2xl bg-[#f4f6f1] px-5 py-4"
                >
                  <div>
                    <p className="text-sm font-black">
                      {proposal.proposerType === "BUYER" ? "구매자" : "판매자"}{" "}
                      제안
                    </p>
                    <p className="mt-1 text-xs text-[#7a8476]">
                      {formatCommerceDateTime(proposal.createdAt)}
                    </p>
                  </div>
                  <b className="text-[#765b12]">
                    {formatPoint(proposal.proposedPrice)}
                  </b>
                </li>
              ))}
            </ol>
            <Link
              href={
                negotiation.buyerUserId === state.user?.id
                  ? "/card-market?view=sent"
                  : "/card-market?view=received"
              }
              className="mt-7 block rounded-2xl bg-[#344b32] px-5 py-4 text-center font-black text-white"
            >
              제안 관리하기
            </Link>
          </section>
        )}
      </div>
    </main>
  );
}
