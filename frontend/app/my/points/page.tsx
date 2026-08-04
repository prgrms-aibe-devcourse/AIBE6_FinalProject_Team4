'use client';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { ApiError } from '@/lib/api';
import { useStore, fmt } from '@/lib/store';
import {
  getPointActivities,
  PointActivity,
  PointTransactionType,
} from '@/features/point/api';
import {
  getPointActivityDescription,
  getPointActivityLink,
  getPointActivityTitle,
  POINT_HISTORY_FILTERS,
  PointHistoryFilterKey,
} from '@/features/point/activity-presentation';

const ICON: Record<PointTransactionType, [string, string]> = {
  CHARGE: ['light_mode', 'bg-gold-soft'],
  JOURNAL_REWARD: ['auto_awesome', 'bg-gold-soft'],
  PURCHASE: ['shopping_cart', 'bg-brand-soft'],
  RESTORE: ['undo', 'bg-brand-soft'],
  REFUND: ['currency_exchange', 'bg-[#fff1eb]'],
  ADMIN_ADJUST: ['tune', 'bg-[#f4f0eb]'],
};

function toExclusiveEndDate(date: string): string | undefined {
  if (!date) return undefined;
  const [year, month, day] = date.split('-').map(Number);
  const nextDate = new Date(year, month - 1, day + 1);
  const nextYear = nextDate.getFullYear();
  const nextMonth = String(nextDate.getMonth() + 1).padStart(2, '0');
  const nextDay = String(nextDate.getDate()).padStart(2, '0');
  return `${nextYear}-${nextMonth}-${nextDay}T00:00:00`;
}

function formatTransactionDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date);
}

export default function PointsHome() {
  const {
    state,
    balance,
    walletLoading,
    walletLoaded,
    walletError,
    refreshWallet,
  } = useStore();
  const [filter, setFilter] = useState<PointHistoryFilterKey>('all');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [page, setPage] = useState(0);
  const [activities, setActivities] = useState<PointActivity[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [transactionLoading, setTransactionLoading] = useState(true);
  const [transactionError, setTransactionError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    if (!state.accessToken) return;

    const controller = new AbortController();
    setTransactionLoading(true);
    setTransactionError('');

    const selectedFilter = POINT_HISTORY_FILTERS.find(({ key }) => key === filter);
    getPointActivities({
      accessToken: state.accessToken,
      type: selectedFilter?.type,
      refType: selectedFilter?.refType,
      from: fromDate ? `${fromDate}T00:00:00` : undefined,
      to: toExclusiveEndDate(toDate),
      page,
      signal: controller.signal,
    })
      .then((response) => {
        setActivities(response.content);
        setTotalPages(response.totalPages);
      })
      .catch((error) => {
        if (error instanceof DOMException && error.name === 'AbortError') return;
        setActivities([]);
        setTotalPages(0);
        setTransactionError(
          error instanceof ApiError
            ? error.message
            : '포인트 내역을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.',
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setTransactionLoading(false);
      });

    return () => controller.abort();
  }, [filter, fromDate, page, reloadKey, state.accessToken, toDate]);

  const changeFilter = (nextFilter: PointHistoryFilterKey) => {
    setFilter(nextFilter);
    setPage(0);
  };

  const changeFromDate = (value: string) => {
    setFromDate(value);
    setPage(0);
  };

  const changeToDate = (value: string) => {
    setToDate(value);
    setPage(0);
  };

  if (!walletLoaded && (walletLoading || !walletError)) {
    return (
      <div className="container max-w-[900px]">
        <div className="rounded-[22px] bg-white py-14 text-center text-sub">
          포인트 잔액을 불러오고 있어요.
        </div>
      </div>
    );
  }

  if (walletError && !walletLoaded) {
    return (
      <div className="container max-w-[900px]">
        <div className="rounded-[22px] bg-white px-5 py-14 text-center text-sub">
          <p>{walletError}</p>
          <button
            type="button"
            onClick={() => void refreshWallet()}
            className="mt-4 cursor-pointer rounded-xl bg-brand px-5 py-2.5 font-bold text-white"
          >
            다시 시도
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="container max-w-[900px]">
      <div className="mb-6 rounded-[22px] bg-gradient-to-br from-[#FFE9A6] to-[#FFD54F] p-7 shadow-[0_8px_24px_rgba(255,213,79,.3)]">
        <div className="text-sm font-bold text-gold-text">내 포인트</div>
        <div className="mb-1 mt-1.5 flex items-center gap-2 text-[42px] font-extrabold text-[#6b5500]">
          <span className="grid h-8 w-8 place-items-center rounded-full bg-gold text-base text-gold-text">P</span>
          {fmt(balance)}<span className="text-xl">P</span>
        </div>
        <div className="text-[13.5px] text-gold-text opacity-85">
          충전 포인트(유상) {fmt(state.wallet.paid)}P · 보너스 포인트(무상){' '}
          {fmt(state.wallet.free)}P
        </div>
        <Link href="/my/points/charge" className="mt-4 inline-block rounded-xl bg-ink px-[22px] py-[11px] font-bold text-white hover:text-white">충전하기</Link>
      </div>

      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-lg font-extrabold">포인트 내역</h2>
        <Link href="/my/points/payments" className="text-sm font-bold text-brand-dark">결제·환불 →</Link>
      </div>

      <div className="mb-4 flex flex-wrap gap-[7px]">
        {POINT_HISTORY_FILTERS.map(({ key, label }) => (
          <button
            key={key}
            type="button"
            onClick={() => changeFilter(key)}
            className={`cursor-pointer rounded-full border-[1.5px] px-3.5 py-[7px] text-[13px] font-bold ${
              filter === key ? 'border-brand bg-brand text-white' : 'border-line bg-white text-[#6d7a68]'
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      <div className="mb-4 flex flex-wrap items-end gap-3 rounded-[16px] border border-line bg-white px-4 py-3">
        <label className="text-xs font-bold text-sub">
          시작일
          <input
            type="date"
            value={fromDate}
            max={toDate || undefined}
            onChange={(event) => changeFromDate(event.target.value)}
            className="mt-1 block rounded-lg border border-line px-3 py-2 text-sm font-normal text-ink"
          />
        </label>
        <label className="text-xs font-bold text-sub">
          종료일
          <input
            type="date"
            value={toDate}
            min={fromDate || undefined}
            onChange={(event) => changeToDate(event.target.value)}
            className="mt-1 block rounded-lg border border-line px-3 py-2 text-sm font-normal text-ink"
          />
        </label>
        {(fromDate || toDate) && (
          <button
            type="button"
            onClick={() => {
              setFromDate('');
              setToDate('');
              setPage(0);
            }}
            className="cursor-pointer rounded-lg border border-line px-3 py-2 text-sm font-bold text-sub"
          >
            기간 초기화
          </button>
        )}
      </div>

      {transactionLoading ? (
        <div className="rounded-[18px] bg-white py-12 text-center text-sm text-sub shadow-card">
          포인트 내역을 불러오고 있어요.
        </div>
      ) : transactionError ? (
        <div className="rounded-[18px] bg-white px-5 py-12 text-center text-sm text-sub shadow-card">
          <p>{transactionError}</p>
          <button
            type="button"
            onClick={() => setReloadKey((current) => current + 1)}
            className="mt-4 cursor-pointer rounded-xl bg-brand px-5 py-2.5 font-bold text-white"
          >
            다시 시도
          </button>
        </div>
      ) : activities.length === 0 ? (
        <div className="rounded-[18px] bg-white py-12 text-center text-sm text-sub shadow-card">
          조건에 맞는 포인트 내역이 없어요.
        </div>
      ) : (
        <>
          <div className="overflow-hidden rounded-[18px] bg-white shadow-card">
            {activities.map((activity) => {
              const relatedLink = getPointActivityLink(activity);
              const hasPaidPoint = activity.paidAmount !== 0;
              const hasFreePoint = activity.freeAmount !== 0;
              const mixedCurrency = hasPaidPoint && hasFreePoint;
              return (
                <div
                  key={activity.id}
                  className="flex items-start gap-3.5 border-b border-[#f4f5ee] px-[18px] py-[15px] last:border-b-0"
                >
                  <div
                    className={`mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-[11px] ${ICON[activity.type][1]}`}
                  >
                    <span className="material-symbols-outlined text-xl">
                      {ICON[activity.type][0]}
                    </span>
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-1.5">
                      <span className="text-[14.5px] font-bold">
                        {getPointActivityTitle(activity)}
                      </span>
                      {hasPaidPoint && (
                        <span
                          title="직접 충전한 유상 포인트"
                          className="rounded-md bg-[#fff3cc] px-1.5 py-0.5 text-[10px] font-bold text-gold-text"
                        >
                          충전 포인트
                        </span>
                      )}
                      {hasFreePoint && (
                        <span
                          title="보상이나 이벤트로 받은 무상 포인트"
                          className="rounded-md bg-brand-soft px-1.5 py-0.5 text-[10px] font-bold text-brand-dark"
                        >
                          보너스 포인트
                        </span>
                      )}
                    </div>
                    <p className="mt-0.5 text-[12.5px] text-sub">
                      {getPointActivityDescription(activity)}
                    </p>
                    <div className="mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-[12px] text-faint">
                      <span>{formatTransactionDate(activity.createdAt)}</span>
                      {relatedLink && (
                        <Link href={relatedLink.href} className="font-bold text-brand-dark">
                          {relatedLink.label} →
                        </Link>
                      )}
                    </div>
                  </div>
                  <div className="shrink-0 text-right">
                    <div
                      className={`font-extrabold ${
                        activity.amount > 0 ? 'text-brand-text' : 'text-[#767676]'
                      }`}
                    >
                      {activity.amount > 0 ? '+' : ''}
                      {fmt(activity.amount)}P
                    </div>
                    {mixedCurrency && (
                      <div className="mt-0.5 text-[11px] text-sub">
                        충전 {activity.paidAmount > 0 ? '+' : ''}{fmt(activity.paidAmount)}P · 보너스{' '}
                        {activity.freeAmount > 0 ? '+' : ''}{fmt(activity.freeAmount)}P
                      </div>
                    )}
                    <div className="mt-0.5 text-[11px] text-faint">
                      {activity.paidBalanceAfter !== null && (
                        <span>충전 잔액 {fmt(activity.paidBalanceAfter)}P</span>
                      )}
                      {activity.paidBalanceAfter !== null && activity.freeBalanceAfter !== null && ' · '}
                      {activity.freeBalanceAfter !== null && (
                        <span>보너스 잔액 {fmt(activity.freeBalanceAfter)}P</span>
                      )}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>

          {totalPages > 1 && (
            <div className="mt-7 flex items-center justify-center gap-3">
              <button
                type="button"
                disabled={page === 0}
                onClick={() => setPage((current) => Math.max(0, current - 1))}
                className="cursor-pointer rounded-xl border border-line bg-white px-4 py-2 font-bold disabled:cursor-not-allowed disabled:opacity-40"
              >
                이전
              </button>
              <span className="text-sm font-bold text-sub">
                {page + 1} / {totalPages}
              </span>
              <button
                type="button"
                disabled={page + 1 >= totalPages}
                onClick={() => setPage((current) => current + 1)}
                className="cursor-pointer rounded-xl border border-line bg-white px-4 py-2 font-bold disabled:cursor-not-allowed disabled:opacity-40"
              >
                다음
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
