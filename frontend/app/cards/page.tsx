'use client';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import FilterBar from '@/components/FilterBar';
import PointPrice from '@/components/PointPrice';
import { ApiError } from '@/lib/api';
import { CardData, getCards, getMyCards } from '@/lib/card-api';
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

export default function Cards({
  searchParams,
}: {
  searchParams?: { scope?: string };
}) {
  const { state, hydrated } = useStore();
  const [filter, setFilter] = useState('all');
  const [sort, setSort] = useState('new');
  const [cards, setCards] = useState<CardData[]>([]);
  const [myCards, setMyCards] = useState<CardData[]>([]);
  const [scope, setScope] = useState<'all' | 'mine'>(
    searchParams?.scope === 'mine' ? 'mine' : 'all',
  );
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const personalized = cards.some((card) => card.ownedCount !== null);
  const activeScope = scope === 'mine' && personalized ? 'mine' : 'all';

  useEffect(() => {
    if (!hydrated) return;

    const controller = new AbortController();
    setLoading(true);
    setError('');

    Promise.all([
      getCards(state.accessToken, controller.signal),
      state.accessToken
        ? getMyCards(state.accessToken, controller.signal)
        : Promise.resolve([]),
    ])
      .then(([allCards, ownedCards]) => {
        setCards(allCards);
        setMyCards(ownedCards);
      })
      .catch((requestError) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return;
        setCards([]);
        setMyCards([]);
        setError(
          requestError instanceof ApiError
            ? requestError.message
            : '카드를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.',
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [hydrated, state.accessToken]);

  useEffect(() => {
    if (!hydrated || state.accessToken) return;
    setScope('all');
    setFilter('all');
    if (sort === 'progress') setSort('new');
  }, [hydrated, state.accessToken, sort]);

  let list = [...myCards];
  if (activeScope === 'all') {
    list = cards.filter((card) => {
      if (!personalized) return true;
      if (filter === 'ready') {
        return (card.ownedCount ?? 0) >= card.requiredCountForExchange;
      }
      if (filter === 'collecting') {
        return (card.ownedCount ?? 0) < card.requiredCountForExchange;
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
  }

  return (
    <div className="container animate-upIn">
      <h1 className="mb-4 text-2xl font-extrabold">카드</h1>

      <div className="mb-4 flex w-fit gap-1.5 rounded-xl bg-[#F0F2E8] p-[5px]">
        <button
          type="button"
          onClick={() => setScope('all')}
          className={`cursor-pointer rounded-[9px] px-4 py-2 text-sm font-bold ${
            activeScope === 'all'
              ? 'bg-white text-ink shadow-[0_2px_8px_rgba(0,0,0,.06)]'
              : 'bg-transparent text-sub'
          }`}
        >
          전체 카드
        </button>
        {personalized && (
          <button
            type="button"
            onClick={() => setScope('mine')}
            className={`cursor-pointer rounded-[9px] px-4 py-2 text-sm font-bold ${
              activeScope === 'mine'
                ? 'bg-white text-ink shadow-[0_2px_8px_rgba(0,0,0,.06)]'
                : 'bg-transparent text-sub'
            }`}
          >
            내 카드
          </button>
        )}
      </div>

      {activeScope === 'all' && (
        <FilterBar
          tabs={personalized ? TABS : TABS.slice(0, 1)}
          activeTab={filter}
          onTab={setFilter}
          sorts={personalized ? SORTS : SORTS.filter((item) => item.key !== 'progress')}
          activeSort={sort}
          onSort={setSort}
        />
      )}

      {loading ? (
        <div className="rounded-[22px] bg-white py-14 text-center text-[15px] text-sub">
          카드를 불러오고 있어요 🃏
        </div>
      ) : error ? (
        <div className="rounded-[22px] bg-white px-5 py-14 text-center text-[15px] text-sub">
          <p>{error}</p>
        </div>
      ) : list.length === 0 ? (
        <div className="rounded-[22px] bg-white py-14 text-center text-[15px] text-sub">
          {activeScope === 'mine'
            ? '아직 보유한 카드가 없어요. 전체 카드에서 첫 카드를 구매해 보세요!'
            : '해당하는 카드가 없어요. 일지를 기록하고 포인트를 모아보세요!'}
        </div>
      ) : (
        <div className="grid gap-[18px] [grid-template-columns:repeat(auto-fill,minmax(220px,1fr))]">
          {list.map((card) => {
            const ready =
              card.ownedCount !== null &&
              card.ownedCount >= card.requiredCountForExchange;
            const pct = Math.min(100, Math.round(progress(card) * 100));
            return (
              <Link
                key={card.id}
                href={`/cards/${card.id}`}
                className={`block overflow-hidden rounded-[20px] border-[2.5px] bg-white text-ink shadow-card hover:text-ink ${
                  ready ? 'animate-glowPulse border-gold' : 'border-transparent'
                }`}
              >
                <div
                  className="relative grid h-[170px] place-items-center bg-brand-soft bg-cover bg-center text-[78px]"
                  style={
                    card.imageUrl
                      ? { backgroundImage: `url("${card.imageUrl}")` }
                      : undefined
                  }
                >
                  {!card.imageUrl && '🃏'}
                  {ready && (
                    <span className="absolute right-3 top-3 rounded-full bg-gold px-[11px] py-[5px] text-xs font-extrabold text-gold-text">
                      교환 가능 🎉
                    </span>
                  )}
                </div>
                <div className="p-4">
                  <div className="flex items-start justify-between gap-2">
                    <div className="text-base font-extrabold">{card.name}</div>
                    <span className="shrink-0 rounded-full bg-brand-soft px-2 py-1 text-[10.5px] font-extrabold text-brand-dark">
                      무상 포인트 우선
                    </span>
                  </div>
                  <div className="mb-3 mt-[3px] text-[13px] text-sub">
                    모으면 {card.exchangeProductName}(으)로 교환할 수 있어요!
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
                </div>
              </Link>
            );
          })}
        </div>
      )}
    </div>
  );
}
