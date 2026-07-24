'use client';
import { useState } from 'react';
import Link from 'next/link';
import { CARDS } from '@/lib/data';
import { fmt } from '@/lib/store';

const LOGS = [
  { name: '수박 카드', unit: 300, qty: 2, used: 600, date: '2026.07.18' },
  { name: '당근 카드', unit: 150, qty: 3, used: 450, date: '2026.07.15' },
  { name: '토마토 카드', unit: 200, qty: 2, used: 400, date: '2026.07.12' },
];

export default function MyCards() {
  const [tab, setTab] = useState('coll');
  const owned = CARDS.filter((c) => c.owned > 0).sort((a, b) => Number(b.owned >= b.required) - Number(a.owned >= a.required));

  return (
    <div className="container">
      <h1 className="mb-4 text-[26px] font-extrabold">내 카드</h1>
      <div className="mb-[22px] flex w-fit gap-1.5 rounded-xl bg-[#F0F2E8] p-[5px]">
        {[['coll', '컬렉션'], ['hist', '구매 이력']].map(([k, label]) => (
          <button
            key={k}
            type="button"
            onClick={() => setTab(k)}
            className={`cursor-pointer rounded-[9px] px-4 py-2 text-sm font-bold ${
              tab === k ? 'bg-white text-ink shadow-[0_2px_8px_rgba(0,0,0,.06)]' : 'bg-transparent text-sub'
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {tab === 'coll' ? (
        owned.length === 0 ? (
          <div className="rounded-[22px] bg-white px-5 py-[60px] text-center">
            <div className="text-[64px]">🃏</div>
            <p className="mb-[18px] mt-3.5 font-bold text-[#6d7a68]">아직 모은 카드가 없어요.<br />상점에서 첫 카드를 만나보세요!</p>
            <Link href="/cards" className="rounded-xl bg-brand px-6 py-3 font-bold text-white hover:text-white">카드 보러 가기</Link>
          </div>
        ) : (
          <div className="grid gap-[18px] [grid-template-columns:repeat(auto-fill,minmax(200px,1fr))]">
            {owned.map((c) => {
              const ready = c.owned >= c.required; const pct = Math.min(100, Math.round((c.owned / c.required) * 100));
              return (
                <div
                  key={c.id}
                  className={`overflow-hidden rounded-[20px] border-[2.5px] bg-white shadow-card ${ready ? 'animate-glowPulse border-gold' : 'border-transparent'}`}
                >
                  <div className="relative flex h-[150px] items-center justify-center text-[70px]" style={{ background: c.grad }}>
                    {c.emoji}
                    <span className="absolute bottom-2.5 right-2.5 rounded-full bg-ink px-[11px] py-1 text-[13px] font-extrabold text-white">×{c.owned}</span>
                  </div>
                  <div className="p-[15px]">
                    <div className="font-extrabold">{c.name}</div>
                    <div className="mb-2.5 mt-1 text-xs text-sub">보유 {c.owned} / 필요 {c.required}</div>
                    {ready ? (
                      <Link href={`/exchange/new?cardId=${c.id}`} className="block rounded-[11px] bg-gold p-2.5 text-center font-extrabold text-[#6b5500] hover:text-[#6b5500]">
                        교환 신청하기 🎉
                      </Link>
                    ) : (
                      <div className="h-2 overflow-hidden rounded-full bg-[#eef0e6]">
                        <div className="h-full rounded-full bg-[#AED581]" style={{ width: `${pct}%` }} />
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )
      ) : (
        <div className="overflow-hidden rounded-[18px] bg-white shadow-card">
          <div className="grid grid-cols-[2fr_1fr_1fr_1.4fr] bg-[#f6f7f1] px-[18px] py-3.5 text-[12.5px] font-extrabold text-sub">
            <div>카드</div><div className="text-center">단가</div><div className="text-center">수량</div><div className="text-right">사용 포인트 · 날짜</div>
          </div>
          {LOGS.map((l, i) => (
            <div key={i} className="grid grid-cols-[2fr_1fr_1fr_1.4fr] items-center border-t border-[#f2f3ec] px-[18px] py-3.5 text-sm">
              <div className="font-bold">{l.name}</div>
              <div className="text-center text-sub">{l.unit}P</div>
              <div className="text-center text-sub">×{l.qty}</div>
              <div className="text-right">
                <span className="font-extrabold text-gold-text">{fmt(l.used)}P</span>
                <div className="text-xs text-faint">{l.date}</div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
