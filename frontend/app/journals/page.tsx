'use client';
import { useState } from 'react';
import Link from 'next/link';
import { JOURNALS, PLANTS } from '@/lib/data';

const TILE_H = ['190px', '150px', '210px', '160px', '200px', '170px'];

export default function JournalsPage() {
  const [filter, setFilter] = useState('all');
  const plants = PLANTS.filter((p) => !p.archived);
  const filters = [['all', '전체'], ...plants.map((p) => [String(p.id), p.nickname])];
  const visible = JOURNALS.filter((j) => filter === 'all' || String(j.plantId) === filter);

  return (
    <div className="container">
      <div className="mb-1.5 flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-[27px] font-extrabold">성장 일지</h1>
        <Link href="/journals/new" className="rounded-xl bg-brand px-5 py-3 text-[15px] font-bold text-white hover:text-white">+ 오늘의 일지 쓰기</Link>
      </div>
      <p className="mb-5 text-sub">한 장 한 장이 모여 이 아이의 이야기가 돼요.</p>

      <div className="mb-6 flex flex-wrap items-center gap-2.5">
        {filters.map(([k, label]) => (
          <button
            key={k}
            type="button"
            onClick={() => setFilter(k)}
            className={`cursor-pointer rounded-full border-[1.5px] px-[15px] py-2 text-sm font-bold ${
              filter === k ? 'border-brand bg-brand text-white' : 'border-line bg-white text-[#6d7a68]'
            }`}
          >
            {label}
          </button>
        ))}
        <div className="flex-1" />
        <div className="flex items-center gap-2 rounded-[11px] border-[1.5px] border-line bg-white px-[13px] py-2 text-sm font-bold text-[#6d7a68]">
          <span className="material-symbols-outlined text-base">calendar_month</span> 2026년 7월
        </div>
      </div>

      {visible.length === 0 ? (
        <div className="px-5 py-[60px] text-center text-sub">이 조건의 일지가 아직 없어요. 오늘의 기록을 남겨볼까요? 🌱</div>
      ) : (
        <div className="gap-[18px] [column-gap:18px] [columns:auto_250px]">
          {visible.map((j, i) => (
            <Link
              key={j.id}
              href={`/journals/${j.id}`}
              className="mb-[18px] block overflow-hidden rounded-[18px] bg-white text-ink shadow-card [break-inside:avoid] hover:text-ink"
            >
              <div
                className="relative flex items-center justify-center text-[62px]"
                style={{ height: TILE_H[i % TILE_H.length], background: j.grad }}
              >
                {j.emoji}
                {j.rewarded && (
                  <span className="absolute right-2.5 top-2.5 rounded-full bg-white/90 px-[9px] py-1 text-[11px] font-extrabold text-gold-text">✨ 포인트</span>
                )}
              </div>
              <div className="p-3.5">
                <div className="mb-[7px] flex items-center gap-1.5">
                  <span className="rounded-full bg-brand-soft px-[9px] py-[3px] text-xs font-extrabold text-brand-dark">{j.plantNickname}</span>
                  <span className="text-xs text-faint">{j.date}</span>
                </div>
                <div className="text-sm leading-[1.55] text-[#4a5647]">{j.content.length > 48 ? j.content.slice(0, 48) + '…' : j.content}</div>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
