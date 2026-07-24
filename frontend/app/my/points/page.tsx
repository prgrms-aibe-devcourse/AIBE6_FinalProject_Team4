'use client';
import { useState } from 'react';
import Link from 'next/link';
import { useStore, fmt } from '@/lib/store';

const ICON: Record<string, [string, string]> = {
  CHARGE: ['light_mode', 'bg-gold-soft'],
  JOURNAL_REWARD: ['auto_awesome', 'bg-gold-soft'],
  PURCHASE: ['shopping_cart', 'bg-brand-soft'],
  REFUND: ['undo', 'bg-brand-soft'],
  CLAWBACK: ['history', 'bg-[#f4f0eb]'],
};
const TXS = [
  { type: 'JOURNAL_REWARD', label: '일지 보상', amount: 30, bal: 4240, date: '2026.07.21' },
  { type: 'PURCHASE', label: '카드 구매 사용', amount: -600, bal: 4210, date: '2026.07.18' },
  { type: 'CHARGE', label: '포인트 충전', amount: 5000, bal: 4810, date: '2026.07.17' },
  { type: 'REFUND', label: '주문 취소 환급', amount: 800, bal: 3610, date: '2026.07.16' },
  { type: 'CLAWBACK', label: '포인트 회수', amount: -30, bal: 2810, date: '2026.07.14' },
];
const FILTERS = [['all', '전체'], ['CHARGE', '충전'], ['JOURNAL_REWARD', '일지 보상'], ['PURCHASE', '구매'], ['REFUND', '환급']];

export default function PointsHome() {
  const { state, balance } = useStore();
  const [filter, setFilter] = useState('all');
  const txs = TXS.filter((t) => filter === 'all' || t.type === filter);

  return (
    <div className="container max-w-[900px]">
      <div className="mb-6 rounded-[22px] bg-gradient-to-br from-[#FFE9A6] to-[#FFD54F] p-7 shadow-[0_8px_24px_rgba(255,213,79,.3)]">
        <div className="text-sm font-bold text-gold-text">내 포인트</div>
        <div className="mb-1 mt-1.5 flex items-center gap-2 text-[42px] font-extrabold text-[#6b5500]">
          <span className="grid h-8 w-8 place-items-center rounded-full bg-gold text-base text-gold-text">P</span>
          {fmt(balance)}<span className="text-xl">P</span>
        </div>
        <div className="text-[13.5px] text-gold-text opacity-85">충전 {fmt(state.wallet.paid)}P · 보상 {fmt(state.wallet.free)}P</div>
        <Link href="/my/points/charge" className="mt-4 inline-block rounded-xl bg-ink px-[22px] py-[11px] font-bold text-white hover:text-white">충전하기</Link>
      </div>

      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-lg font-extrabold">포인트 내역</h2>
        <Link href="/my/points/payments" className="text-sm font-bold text-brand-dark">결제·환불 →</Link>
      </div>

      <div className="mb-4 flex flex-wrap gap-[7px]">
        {FILTERS.map(([k, label]) => (
          <button
            key={k}
            type="button"
            onClick={() => setFilter(k)}
            className={`cursor-pointer rounded-full border-[1.5px] px-3.5 py-[7px] text-[13px] font-bold ${
              filter === k ? 'border-brand bg-brand text-white' : 'border-line bg-white text-[#6d7a68]'
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      <div className="overflow-hidden rounded-[18px] bg-white shadow-card">
        {txs.map((t, i) => (
          <div key={i} className="flex items-center gap-3.5 border-b border-[#f4f5ee] px-[18px] py-[15px]">
            <div className={`flex h-10 w-10 items-center justify-center rounded-[11px] ${ICON[t.type][1]}`}>
              <span className="material-symbols-outlined text-xl">{ICON[t.type][0]}</span>
            </div>
            <div className="flex-1">
              <div className="text-[14.5px] font-bold">{t.label}</div>
              <div className="text-[12.5px] text-faint">{t.date}</div>
            </div>
            <div className="text-right">
              <div className={`font-extrabold ${t.amount > 0 ? 'text-brand-text' : 'text-[#8a8a8a]'}`}>
                {t.amount > 0 ? '+' : ''}{fmt(t.amount)}P
              </div>
              <div className="text-xs text-faint">잔액 {fmt(t.bal)}P</div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
