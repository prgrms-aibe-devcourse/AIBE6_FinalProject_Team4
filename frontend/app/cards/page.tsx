'use client';
import { useState } from 'react';
import Link from 'next/link';
import FilterBar from '@/components/FilterBar';
import PointPrice from '@/components/PointPrice';
import { CARDS } from '@/lib/data';

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

const progress = (c: { owned: number; required: number }) => c.owned / c.required;

export default function Cards() {
  const [filter, setFilter] = useState('all');
  const [sort, setSort] = useState('new');

  let list = CARDS.filter((c) => {
    if (filter === 'ready') return c.owned >= c.required;
    if (filter === 'collecting') return c.owned < c.required;
    return true;
  });

  if (sort === 'low') list = [...list].sort((a, b) => a.price - b.price);
  else if (sort === 'high') list = [...list].sort((a, b) => b.price - a.price);
  else if (sort === 'progress') list = [...list].sort((a, b) => progress(b) - progress(a));
  else list = [...list].sort((a, b) => b.id - a.id);

  return (
    <div className="container animate-upIn">
      <h1 className="mb-4 text-2xl font-extrabold">카드</h1>

      <FilterBar
        tabs={TABS}
        activeTab={filter}
        onTab={setFilter}
        sorts={SORTS}
        activeSort={sort}
        onSort={setSort}
      />

      {list.length === 0 ? (
        <div className="rounded-[22px] bg-white py-14 text-center text-[15px] text-sub">
          해당하는 카드가 없어요. 일지를 기록하고 포인트를 모아보세요!
        </div>
      ) : (
        <div className="grid gap-[18px] [grid-template-columns:repeat(auto-fill,minmax(220px,1fr))]">
          {list.map((c) => {
            const ready = c.owned >= c.required;
            const pct = Math.min(100, Math.round(progress(c) * 100));
            return (
              <Link
                key={c.id}
                href={`/cards/${c.id}`}
                className={`block overflow-hidden rounded-[20px] border-[2.5px] bg-white text-ink shadow-card hover:text-ink ${
                  ready ? 'animate-glowPulse border-gold' : 'border-transparent'
                }`}
              >
                <div className="relative grid h-[170px] place-items-center text-[78px]" style={{ background: c.grad }}>
                  {c.emoji}
                  {ready && (
                    <span className="absolute right-3 top-3 rounded-full bg-gold px-[11px] py-[5px] text-xs font-extrabold text-gold-text">
                      교환 가능 🎉
                    </span>
                  )}
                </div>
                <div className="p-4">
                  <div className="text-base font-extrabold">{c.name}</div>
                  <div className="mb-3 mt-[3px] text-[13px] text-sub">모으면 진짜 {c.realName}이 돼요!</div>
                  <div className="mb-2 flex items-center justify-between">
                    <span className="text-xs font-bold text-[#6d7a68]">보유 {c.owned} / 필요 {c.required}</span>
                    <PointPrice value={c.price} size="sm" />
                  </div>
                  <div className="h-2 overflow-hidden rounded-full bg-[#eef0e6]">
                    <div className={`h-full rounded-full ${ready ? 'bg-gold' : 'bg-brand'}`} style={{ width: `${pct}%` }} />
                  </div>
                </div>
              </Link>
            );
          })}
        </div>
      )}
    </div>
  );
}
