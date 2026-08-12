"use client";

import { ApiError } from "@/lib/api";
import {
  AdminCardMarketRevenue,
  getAdminCardMarketRevenue,
} from "@/lib/admin-card-market-api";
import { useCallback, useEffect, useState } from "react";

const POINT = new Intl.NumberFormat("ko-KR");

function point(value: number) {
  return `${POINT.format(value)}P`;
}

function dateTime(value: string) {
  return new Date(value).toLocaleString("ko-KR");
}

export default function AdminCardMarketRevenuePanel({
  accessToken,
}: {
  accessToken: string;
}) {
  const [revenue, setRevenue] = useState<AdminCardMarketRevenue | null>(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = useCallback(
    (signal?: AbortSignal) => {
      setLoading(true);
      setError("");
      return getAdminCardMarketRevenue(accessToken, { page, signal })
        .then(setRevenue)
        .catch((requestError) => {
          if (
            requestError instanceof DOMException &&
            requestError.name === "AbortError"
          )
            return;
          setError(
            requestError instanceof ApiError
              ? requestError.message
              : "거래소 수익 내역을 불러오지 못했어요.",
          );
        })
        .finally(() => {
          if (!signal?.aborted) setLoading(false);
        });
    },
    [accessToken, page],
  );

  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, [load]);

  const summaries = [
    ["누적 플랫폼 수익", point(revenue?.totalFeePoint ?? 0), "payments"],
    ["누적 거래액", point(revenue?.totalTradePoint ?? 0), "swap_horiz"],
    [
      "판매자 정산액",
      point(revenue?.totalSellerReceivedPoint ?? 0),
      "account_balance_wallet",
    ],
    [
      "완료 거래",
      `${POINT.format(revenue?.totalTradeCount ?? 0)}건`,
      "receipt_long",
    ],
  ] as const;

  return (
    <div className="flex flex-col gap-5">
      <section className="rounded-[18px] border border-line bg-white p-5 shadow-sm">
        <div className="mb-1 text-sm font-extrabold">카드 거래소 수수료</div>
        <p className="text-xs leading-5 text-sub">
          완료된 거래에서 차감된 20% 수수료를 플랫폼 수익으로 집계합니다. 거래
          원장은 변경하지 않고 조회만 제공합니다.
        </p>
      </section>

      <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {summaries.map(([label, value, icon]) => (
          <div
            key={label}
            className="rounded-[18px] border border-line bg-white p-5 shadow-sm"
          >
            <div className="flex items-center gap-2 text-xs font-bold text-sub">
              <span className="material-symbols-outlined text-lg text-brand-dark">
                {icon}
              </span>
              {label}
            </div>
            <p className="mt-3 text-2xl font-extrabold text-ink">{value}</p>
          </div>
        ))}
      </section>

      <section className="overflow-x-auto rounded-[18px] border border-line bg-white shadow-sm">
        <div className="min-w-[1000px]">
          <div className="grid grid-cols-[.6fr_1.2fr_1fr_1fr_1fr_1fr] gap-3 border-b border-line bg-[#fafbf7] px-4 py-3 text-xs font-extrabold text-sub">
            <div>거래</div>
            <div>카드</div>
            <div>판매자 → 구매자</div>
            <div className="text-right">거래액</div>
            <div className="text-right">플랫폼 수익</div>
            <div className="text-right">완료 시각</div>
          </div>
          {loading ? (
            <div className="p-12 text-center text-sub">
              수익 내역을 불러오는 중...
            </div>
          ) : error ? (
            <div className="p-12 text-center text-danger">{error}</div>
          ) : !revenue?.content.length ? (
            <div className="p-12 text-center text-sub">
              완료된 거래가 없습니다.
            </div>
          ) : (
            revenue.content.map((trade) => (
              <div
                key={trade.tradeId}
                className="grid grid-cols-[.6fr_1.2fr_1fr_1fr_1fr_1fr] items-center gap-3 border-b border-line px-4 py-3 text-xs last:border-b-0"
              >
                <div>
                  <b>#{trade.tradeId}</b>
                  <div className="text-sub">
                    {trade.tradeType === "BUY_NOW" ? "바로 구매" : "가격 협상"}
                  </div>
                </div>
                <b>{trade.cardName}</b>
                <div>
                  <b>{trade.sellerNickname}</b>
                  <span className="mx-1 text-sub">→</span>
                  <b>{trade.buyerNickname}</b>
                </div>
                <b className="text-right">{point(trade.tradePoint)}</b>
                <b className="text-right text-[#9a6b13]">
                  {point(trade.feePoint)}
                </b>
                <div className="text-right text-sub">
                  {dateTime(trade.completedAt)}
                </div>
              </div>
            ))
          )}
        </div>
        {revenue && revenue.totalPages > 1 ? (
          <div className="flex items-center justify-center gap-3 border-t border-line px-4 py-4">
            <button
              type="button"
              disabled={page <= 0}
              onClick={() => setPage((current) => current - 1)}
              className="rounded-xl border border-line px-4 py-2 text-sm font-bold disabled:opacity-40"
            >
              이전
            </button>
            <span className="text-sm font-bold text-sub">
              {page + 1} / {revenue.totalPages}
            </span>
            <button
              type="button"
              disabled={page + 1 >= revenue.totalPages}
              onClick={() => setPage((current) => current + 1)}
              className="rounded-xl border border-line px-4 py-2 text-sm font-bold disabled:opacity-40"
            >
              다음
            </button>
          </div>
        ) : null}
      </section>
    </div>
  );
}
