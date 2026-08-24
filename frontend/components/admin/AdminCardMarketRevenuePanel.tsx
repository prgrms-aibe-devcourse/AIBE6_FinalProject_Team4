"use client";

import { ApiError } from "@/lib/api";
import {
  AdminCardMarketFilters,
  AdminCardMarketRevenue,
  downloadAdminCardMarketRevenueCsv,
  getAdminCardMarketRevenue,
} from "@/lib/admin-card-market-api";
import { useCallback, useEffect, useRef, useState } from "react";
import AdminPagination from "./AdminPagination";
import { useScrollOnPageLoad } from "./use-scroll-on-page-load";

const PAGE_SIZE = 10;
const GRID_COLS = "grid-cols-[.6fr_1.2fr_1fr_1fr_1fr_1fr]";
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
  const sectionRef = useRef<HTMLElement>(null);
  const [revenue, setRevenue] = useState<AdminCardMarketRevenue | null>(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [filters, setFilters] = useState<AdminCardMarketFilters>({});
  const [appliedFilters, setAppliedFilters] = useState<AdminCardMarketFilters>(
    {},
  );
  const [exporting, setExporting] = useState(false);

  useScrollOnPageLoad(page, loading, sectionRef);

  const load = useCallback(
    (signal?: AbortSignal) => {
      setLoading(true);
      setError("");
      return getAdminCardMarketRevenue(accessToken, {
        page,
        size: PAGE_SIZE,
        signal,
        filters: appliedFilters,
      })
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
    [accessToken, appliedFilters, page],
  );

  const setPeriod = (days?: number) => {
    if (!days) {
      setFilters((current) => ({ ...current, from: "", to: "" }));
      return;
    }
    const to = new Date();
    const from = new Date();
    from.setDate(to.getDate() - days + 1);
    const localDate = (value: Date) => {
      const offset = value.getTimezoneOffset() * 60_000;
      return new Date(value.getTime() - offset).toISOString().slice(0, 10);
    };
    setFilters((current) => ({
      ...current,
      from: localDate(from),
      to: localDate(to),
    }));
  };

  const exportCsv = async () => {
    setExporting(true);
    setError("");
    try {
      const blob = await downloadAdminCardMarketRevenueCsv(
        accessToken,
        appliedFilters,
      );
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `card-market-revenue-${new Date().toISOString().slice(0, 10)}.csv`;
      anchor.click();
      URL.revokeObjectURL(url);
    } catch (requestError) {
      setError(
        requestError instanceof ApiError
          ? requestError.message
          : "CSV 파일을 만들지 못했어요.",
      );
    } finally {
      setExporting(false);
    }
  };

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

      <section className="rounded-[18px] border border-line bg-white p-5 shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h3 className="text-sm font-extrabold">거래 내역 필터</h3>
            <p className="mt-1 text-xs text-sub">
              선택한 조건 기준으로 요약 금액과 거래 목록을 함께 집계합니다.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            {([1, 7, 30] as const).map((days) => (
              <button
                key={days}
                type="button"
                onClick={() => setPeriod(days)}
                className="rounded-xl border border-line px-3 py-2 text-xs font-bold"
              >
                {days === 1 ? "오늘" : `최근 ${days}일`}
              </button>
            ))}
            <button
              type="button"
              onClick={() => setPeriod()}
              className="rounded-xl border border-line px-3 py-2 text-xs font-bold"
            >
              전체 기간
            </button>
          </div>
        </div>
        <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-4">
          <input
            type="date"
            value={filters.from ?? ""}
            onChange={(event) =>
              setFilters((current) => ({
                ...current,
                from: event.target.value,
              }))
            }
            className="rounded-xl border border-line px-3 py-2.5 text-sm"
            aria-label="조회 시작일"
          />
          <input
            type="date"
            value={filters.to ?? ""}
            onChange={(event) =>
              setFilters((current) => ({ ...current, to: event.target.value }))
            }
            className="rounded-xl border border-line px-3 py-2.5 text-sm"
            aria-label="조회 종료일"
          />
          <input
            type="number"
            min={1}
            value={filters.userId ?? ""}
            onChange={(event) =>
              setFilters((current) => ({
                ...current,
                userId: event.target.value,
              }))
            }
            placeholder="사용자 ID"
            className="rounded-xl border border-line px-3 py-2.5 text-sm"
          />
          <input
            type="number"
            min={1}
            value={filters.cardId ?? ""}
            onChange={(event) =>
              setFilters((current) => ({
                ...current,
                cardId: event.target.value,
              }))
            }
            placeholder="카드 ID"
            className="rounded-xl border border-line px-3 py-2.5 text-sm"
          />
          <input
            value={filters.keyword ?? ""}
            maxLength={50}
            onChange={(event) =>
              setFilters((current) => ({
                ...current,
                keyword: event.target.value,
              }))
            }
            placeholder="카드 이름"
            className="rounded-xl border border-line px-3 py-2.5 text-sm md:col-span-2"
          />
          <select
            value={filters.tradeType ?? ""}
            onChange={(event) =>
              setFilters((current) => ({
                ...current,
                tradeType: event.target
                  .value as AdminCardMarketFilters["tradeType"],
              }))
            }
            className="rounded-xl border border-line px-3 py-2.5 text-sm"
          >
            <option value="">전체 거래 유형</option>
            <option value="BUY_NOW">바로 구매</option>
            <option value="NEGOTIATED">가격 협상</option>
          </select>
          <button
            type="button"
            onClick={() => {
              setPage(0);
              setAppliedFilters({ ...filters });
            }}
            className="rounded-xl bg-brand px-4 py-2.5 text-sm font-extrabold text-white"
          >
            조건 적용
          </button>
        </div>
        <div className="mt-4 flex justify-end">
          <button
            type="button"
            disabled={exporting}
            onClick={() => void exportCsv()}
            className="inline-flex items-center gap-2 rounded-xl border border-brand px-4 py-2.5 text-sm font-extrabold text-brand-dark disabled:opacity-50"
          >
            <span className="material-symbols-outlined text-lg">download</span>
            {exporting ? "파일 생성 중..." : "조회 결과 CSV"}
          </button>
        </div>
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

      <section ref={sectionRef} className="overflow-x-auto rounded-[18px] border border-line bg-white shadow-sm">
        <div className="min-w-[1000px]">
          <div className={`grid gap-3 border-b border-line bg-[#fafbf7] px-4 py-3 text-xs font-extrabold text-sub ${GRID_COLS}`}>
            <div>거래</div>
            <div>카드</div>
            <div>판매자 → 구매자</div>
            <div className="text-right">거래액</div>
            <div className="text-right">플랫폼 수익</div>
            <div className="text-right">완료 시각</div>
          </div>
          {loading && !revenue?.content.length ? (
            <div className="p-12 text-center text-sub">
              거래 내역을 불러오고 있어요.
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
          <div className="border-t border-line px-4 pt-4 pb-6">
            <AdminPagination page={page} totalPages={revenue.totalPages} onChange={setPage} />
          </div>
        ) : null}
      </section>
    </div>
  );
}
