'use client';
import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import FilterBar from '@/components/FilterBar';
import PointPrice from '@/components/PointPrice';
import { ApiError } from '@/lib/api';
import { CardData, getCards } from '@/lib/card-api';
import { couponName } from '@/lib/coupon-label';
import { useStore } from '@/lib/store';

const TABS = [
  { key: 'all', label: '전체' },
  { key: 'collecting', label: '수집중' },
  { key: 'ready', label: '교환가능' },
];

const SORTS = [
  { key: 'new', label: '최신순' },
  { key: 'low', label: '가격 낮은순' },
  { key: 'high', label: '가격 높은순' },
  { key: 'progress', label: '진행률순' },
];

const progress = (card: CardData) =>
  (card.ownedCount ?? 0) / card.requiredCountForExchange;

const PAGE_SIZE = 12;
type CardSearchParams = {
  tab?: string | string[];
  sort?: string | string[];
  page?: string | string[];
};

const queryValue = (value?: string | string[]) =>
  Array.isArray(value) ? value[0] : value;

export default function Cards({ searchParams }: { searchParams?: CardSearchParams }) {
  const router = useRouter();
  const { state, hydrated } = useStore();
  const requestedFilter = queryValue(searchParams?.tab);
  const requestedSort = queryValue(searchParams?.sort);
  const requestedPage = Number(queryValue(searchParams?.page));
  const urlFilter = TABS.some((tab) => tab.key === requestedFilter) ? requestedFilter! : 'all';
  const urlSort = SORTS.some((item) => item.key === requestedSort) ? requestedSort! : 'new';
  const urlPage = Number.isInteger(requestedPage) && requestedPage > 0 ? requestedPage - 1 : 0;
  const [filter, setFilter] = useState(urlFilter);
  const [sort, setSort] = useState(urlSort);
  const [page, setPage] = useState(urlPage);
  const [cards, setCards] = useState<CardData[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const personalized = cards.some((card) => card.ownedCount !== null);

  useEffect(() => {
    setFilter(urlFilter);
    setSort(urlSort);
    setPage(urlPage);
  }, [urlFilter, urlPage, urlSort]);

  useEffect(() => {
    if (!hydrated) return;

    const controller = new AbortController();
    setLoading(true);
    setError('');

    getCards(state.accessToken, controller.signal)
      .then((allCards) => {
        setCards(allCards);
      })
      .catch((requestError) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return;
        setCards([]);
        setError(
          requestError instanceof ApiError
            ? requestError.message
            : '쿠폰을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.',
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [hydrated, state.accessToken]);

  useEffect(() => {
    if (!hydrated || state.accessToken) return;
    setFilter('all');
    if (sort === 'progress') setSort('new');
  }, [hydrated, state.accessToken, sort]);

  let list = cards.filter((card) => {
    if (!personalized) return true;
    if (filter === 'ready') {
      return (card.ownedCount ?? 0) >= card.requiredCountForExchange;
    }
    if (filter === 'collecting') {
      const ownedCount = card.ownedCount ?? 0;
      return ownedCount > 0 && ownedCount < card.requiredCountForExchange;
    }
    return true;
  });

  if (sort === 'low') list = [...list].sort((a, b) => a.pointPrice - b.pointPrice);
  else if (sort === 'high') list = [...list].sort((a, b) => b.pointPrice - a.pointPrice);
  else if (sort === 'progress') list = [...list].sort((a, b) => progress(b) - progress(a));
  else {
    list = [...list].sort(
      (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
    );
  }

  const totalPages = Math.ceil(list.length / PAGE_SIZE);
  const visibleCards = list.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  const navigate = useCallback(
    (nextFilter: string, nextSort: string, nextPage: number) => {
      const params = new URLSearchParams();
      if (nextFilter !== 'all') params.set('tab', nextFilter);
      if (nextSort !== 'new') params.set('sort', nextSort);
      params.set('page', String(nextPage + 1));
      router.replace(`/cards?${params}`, { scroll: false });
    },
    [router],
  );

  const changeFilter = (nextFilter: string) => {
    setFilter(nextFilter);
    setPage(0);
    navigate(nextFilter, sort, 0);
  };

  const changeSort = (nextSort: string) => {
    setSort(nextSort);
    setPage(0);
    navigate(filter, nextSort, 0);
  };

  const changePage = (nextPage: number) => {
    setPage(nextPage);
    navigate(filter, sort, nextPage);
  };

  useEffect(() => {
    if (totalPages > 0 && page >= totalPages) {
      setPage(totalPages - 1);
      navigate(filter, sort, totalPages - 1);
    }
  }, [filter, navigate, page, sort, totalPages]);

  const returnTo = `/cards?${new URLSearchParams({
    ...(filter === 'all' ? {} : { tab: filter }),
    ...(sort === 'new' ? {} : { sort }),
    page: String(page + 1),
  })}`;

  return (
    <div className="container animate-upIn">
      <h1 className="mb-4 text-2xl font-extrabold">쿠폰</h1>

      <FilterBar
        tabs={personalized ? TABS : TABS.slice(0, 1)}
        activeTab={filter}
        onTab={changeFilter}
        sorts={personalized ? SORTS : SORTS.filter((item) => item.key !== 'progress')}
        activeSort={sort}
        onSort={changeSort}
      />

      {loading ? (
        <div className="rounded-[22px] bg-white py-14 text-center text-[15px] text-sub">
          쿠폰을 불러오고 있어요 🎟️
        </div>
      ) : error ? (
        <div className="rounded-[22px] bg-white px-5 py-14 text-center text-[15px] text-sub">
          <p>{error}</p>
        </div>
      ) : list.length === 0 ? (
        <div className="rounded-[22px] bg-white py-14 text-center text-[15px] text-sub">
          해당하는 쿠폰이 없어요. 일지를 기록하고 포인트를 모아보세요!
        </div>
      ) : (
        <div className="grid gap-[18px] [grid-template-columns:repeat(auto-fill,minmax(220px,1fr))]">
          {visibleCards.map((card) => {
            const ready =
              card.ownedCount !== null &&
              card.ownedCount >= card.requiredCountForExchange;
            const outOfStock = card.exchangeProductStock <= 0;
            const pct = Math.min(100, Math.round(progress(card) * 100));
            return (
              <article
                key={card.id}
                className={`relative block overflow-hidden rounded-[20px] border-[2.5px] bg-white text-ink shadow-card ${
                  ready ? 'animate-glowPulse border-gold' : 'border-transparent'
                }`}
              >
                <Link
                  href={`/cards/${card.id}?returnTo=${encodeURIComponent(returnTo)}`}
                  aria-label={`${couponName(card.name)} 상세 보기`}
                  className="absolute inset-0 z-10"
                />
                <div
                  className="pointer-events-none relative z-0 grid h-[170px] place-items-center bg-brand-soft bg-cover bg-center text-[78px]"
                  style={
                    card.imageUrl
                      ? { backgroundImage: `url("${card.imageUrl}")` }
                      : undefined
                  }
                >
                  {!card.imageUrl && '🃏'}
                  {ready && (
                    <span className={`absolute right-3 top-3 rounded-full px-[11px] py-[5px] text-xs font-extrabold ${outOfStock ? 'bg-[#eceee8] text-sub' : 'bg-gold text-gold-text'}`}>
                      {outOfStock ? '교환 상품 품절' : '교환 가능 🎉'}
                    </span>
                  )}
                </div>
                <div className="pointer-events-none relative z-10 p-4">
                  <div className="flex items-start justify-between gap-2">
                    <div className="text-base font-extrabold">{couponName(card.name)}</div>
                    <span className="shrink-0 rounded-full bg-brand-soft px-2 py-1 text-[10.5px] font-extrabold text-brand-dark">
                      보너스 포인트 우선
                    </span>
                  </div>
                  <div className="mb-3 mt-[3px] text-[13px] text-sub">
                    모으면 {card.exchangeProductName}(으)로 교환할 수 있어요!
                  </div>
                  <div className={`mb-3 text-xs font-bold ${outOfStock ? 'text-danger' : 'text-brand'}`}>
                    {outOfStock
                      ? '현재 교환 상품 재고가 없어요'
                      : `교환 상품 재고 ${card.exchangeProductStock}개`}
                  </div>
                  <div className="flex items-center justify-between">
                    {card.ownedCount === null ? (
                      <span className="text-xs font-bold text-[#6d7a68]">
                        {card.requiredCountForExchange}장 필요
                      </span>
                    ) : (
                      <span className="text-xs font-bold text-[#6d7a68]">
                        보유 {card.ownedCount} / 필요 {card.requiredCountForExchange}
                      </span>
                    )}
                    <span className="flex items-center gap-1 text-xs font-bold text-sub">
                      1장당
                      <PointPrice value={card.pointPrice} size="sm" />
                    </span>
                  </div>
                  {card.ownedCount !== null && (
                    <div className="mt-2 h-2 overflow-hidden rounded-full bg-[#eef0e6]">
                      <div
                        className={`h-full rounded-full ${ready ? 'bg-gold' : 'bg-brand'}`}
                        style={{ width: `${pct}%` }}
                      />
                    </div>
                  )}
                  {ready ? (
                    outOfStock ? (
                      <div className="pointer-events-auto relative z-20 mt-3 rounded-xl bg-[#eceee8] px-4 py-2.5 text-center text-sm font-extrabold text-sub">
                        재입고 대기 중
                      </div>
                    ) : (
                      <Link
                        href={`/exchange/new?cardId=${card.id}`}
                        className="pointer-events-auto relative z-20 mt-3 block rounded-xl bg-brand px-4 py-2.5 text-center text-sm font-extrabold text-white hover:text-white"
                      >
                        바로 교환하기
                      </Link>
                    )
                  ) : null}
                </div>
              </article>
            );
          })}
        </div>
      )}

      {!loading && !error && totalPages > 1 ? (
        <div className="mt-7 flex items-center justify-center gap-3">
          <button
            type="button"
            disabled={page === 0}
            onClick={() => changePage(Math.max(0, page - 1))}
            className="rounded-xl border border-line bg-white px-4 py-2 font-bold disabled:opacity-40"
          >
            이전
          </button>
          <span className="text-sm font-bold text-sub">
            {page + 1} / {totalPages}
          </span>
          <button
            type="button"
            disabled={page + 1 >= totalPages}
            onClick={() => changePage(page + 1)}
            className="rounded-xl border border-line bg-white px-4 py-2 font-bold disabled:opacity-40"
          >
            다음
          </button>
        </div>
      ) : null}
    </div>
  );
}
