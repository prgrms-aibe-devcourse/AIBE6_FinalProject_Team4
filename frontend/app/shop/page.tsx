'use client';
import { useState } from 'react';
import Link from 'next/link';
import FilterBar from '@/components/FilterBar';
import PointPrice from '@/components/PointPrice';
import { PRODUCTS } from '@/lib/data';

const TABS = [
  { key: 'all', label: '전체' },
  { key: 'KIT', label: '키트' },
  { key: 'SEEDLING', label: '모종' },
];

const SORTS = [
  { key: 'new', label: '최신순' },
  { key: 'low', label: '가격 낮은순' },
  { key: 'high', label: '가격 높은순' },
];

const CAT_LABEL: Record<string, string> = { KIT: '키트', SEEDLING: '모종' };

export default function Shop() {
  const [cat, setCat] = useState('all');
  const [sort, setSort] = useState('new');

  let list = PRODUCTS.filter((p) => cat === 'all' || p.cat === cat);
  if (sort === 'low') list = [...list].sort((a, b) => a.price - b.price);
  else if (sort === 'high') list = [...list].sort((a, b) => b.price - a.price);
  else list = [...list].sort((a, b) => b.id - a.id);

  return (
    <div className="container animate-upIn">
      <h1 className="mb-4 text-2xl font-extrabold">상점</h1>

      <FilterBar
        tabs={TABS}
        activeTab={cat}
        onTab={setCat}
        sorts={SORTS}
        activeSort={sort}
        onSort={setSort}
      />

      {list.length === 0 ? (
        <div className="rounded-[22px] bg-white py-14 text-center text-[15px] text-sub">
          찾으시는 상품이 아직 없어요. 곧 새 식구들이 들어올 예정이에요!
        </div>
      ) : (
        <div className="grid gap-4 [grid-template-columns:repeat(auto-fill,minmax(210px,1fr))]">
          {list.map((p) => {
            const sold = p.stock <= 0;
            return (
              <Link
                key={p.id}
                href={`/shop/${p.id}`}
                className="block overflow-hidden rounded-[20px] bg-white text-ink shadow-card hover:text-ink"
              >
                <div
                  className={`relative grid h-[150px] place-items-center text-[46px] ${sold ? 'opacity-70 grayscale' : ''}`}
                  style={{ background: p.grad }}
                >
                  {p.emoji}
                  {sold && (
                    <span className="absolute left-3 top-3 rounded-lg bg-sub px-3 py-1 text-xs font-bold text-white">품절</span>
                  )}
                </div>
                <div className="px-4 py-3.5">
                  <div className="mb-[7px] inline-block rounded-md bg-brand-soft px-[9px] py-[3px] text-[11px] font-bold text-brand-dark">
                    {CAT_LABEL[p.cat]}
                  </div>
                  <div className="text-[15.5px] font-bold">{p.name}</div>
                  <PointPrice value={p.price} className="mt-1.5" />
                </div>
              </Link>
            );
          })}
        </div>
      )}
    </div>
  );
}
