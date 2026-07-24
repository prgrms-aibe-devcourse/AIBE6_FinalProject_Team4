'use client';
import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useStore, fmt } from '@/lib/store';
import { useUI } from '@/lib/ui';
import PointPrice from '@/components/PointPrice';
import { CARDS } from '@/lib/data';

const CONFETTI = [
  { left: '10%', dur: '1.5s', delay: '0s', emoji: '🎉' }, { left: '28%', dur: '1.8s', delay: '.2s', emoji: '✨' },
  { left: '48%', dur: '1.4s', delay: '.1s', emoji: '🍉' }, { left: '66%', dur: '1.9s', delay: '.3s', emoji: '🥕' },
  { left: '82%', dur: '1.6s', delay: '.15s', emoji: '✨' }, { left: '92%', dur: '1.7s', delay: '.25s', emoji: '🎉' },
];

export default function CardDetail({ params }: { params: { id: string } }) {
  const router = useRouter();
  const { balance, spend, set } = useStore();
  const { showToast, askConfirm } = useUI();
  const id = Number(params.id);
  const base = CARDS.find((c) => c.id === id) || CARDS[0];
  const [owned, setOwned] = useState(base.owned);
  const [qty, setQty] = useState(1);
  const [celebrate, setCelebrate] = useState(false);

  const total = base.price * qty;
  const ring = `conic-gradient(#7CB342 ${Math.min(360, (owned / base.required) * 360)}deg,#eef0e6 0)`;

  const buy = () => {
    if (total > balance) return showToast(`포인트가 ${fmt(total - balance)}P 부족해요.`, 'err');
    askConfirm({ icon: 'paid', title: '카드를 구매할까요?', ok: '구매하기',
      body: `${base.name} ${qty}장 · 총 ${fmt(total)}P를 사용해요. 무상 포인트가 먼저 사용돼요.`,
      onOk: () => {
        spend(total);
        const newOwned = owned + qty;
        const reached = newOwned >= base.required && owned < base.required;
        setOwned(newOwned); setQty(1);
        if (reached) { set((s) => ({ readyCards: s.readyCards + 1 })); setCelebrate(true); }
        else showToast('카드를 구매했어요! 🃏');
      } });
  };

  return (
    <div className="container">
      <Link href="/cards" className="text-sm font-semibold text-sub">← 카드</Link>
      <div className="mt-4 grid items-start gap-7 [grid-template-columns:repeat(auto-fit,minmax(280px,1fr))]">
        <div
          className="flex aspect-[3/4] max-h-[420px] items-center justify-center overflow-hidden rounded-[22px] text-[150px] shadow-[0_12px_36px_rgba(0,0,0,.1)]"
          style={{ background: base.grad }}
        >
          {base.emoji}
        </div>
        <div>
          <h1 className="mb-1.5 text-[28px] font-extrabold">{base.name}</h1>
          <PointPrice value={base.price} size="lg" className="mb-5" />

          <div className="mb-5 flex items-center gap-5 rounded-[18px] bg-white px-5 py-[18px] shadow-card">
            <div className="flex h-[92px] w-[92px] flex-none items-center justify-center rounded-full" style={{ background: ring }}>
              <div className="flex h-[70px] w-[70px] flex-col items-center justify-center rounded-full bg-white font-extrabold">
                <span className="text-xl text-brand">{owned}</span>
                <span className="text-xs text-faint">/ {base.required}</span>
              </div>
            </div>
            <div>
              <div className="text-base font-extrabold">내 진행도</div>
              <div className="mt-[3px] text-[13.5px] text-sub">
                {owned >= base.required ? '모두 모았어요! 교환할 수 있어요 🎉' : `${base.required - owned}장만 더 모으면 교환할 수 있어요`}
              </div>
            </div>
          </div>

          <div className="mb-[22px] flex items-center gap-3.5 rounded-[18px] bg-white px-5 py-[18px] shadow-card">
            <div className="flex h-16 w-16 flex-none items-center justify-center rounded-[14px] text-[34px]" style={{ background: base.realGrad }}>
              {base.realEmoji}
            </div>
            <div>
              <div className="text-xs font-bold text-faint">교환 상품</div>
              <div className="font-extrabold">{base.realName}</div>
              <div className="mt-0.5 text-[13px] text-sub">{base.realDesc} · {base.required}장 필요</div>
            </div>
          </div>

          <div className="mb-4 flex items-center gap-3.5">
            <span className="font-bold text-[#6d7a68]">수량</span>
            <div className="flex items-center overflow-hidden rounded-[11px] border-[1.5px] border-line">
              <button type="button" onClick={() => setQty(Math.max(1, qty - 1))} className="flex h-10 w-10 cursor-pointer items-center justify-center text-xl text-[#6d7a68]">−</button>
              <div className="w-[46px] text-center text-base font-extrabold">{qty}</div>
              <button type="button" onClick={() => setQty(Math.min(99, qty + 1))} className="flex h-10 w-10 cursor-pointer items-center justify-center text-xl text-[#6d7a68]">+</button>
            </div>
            <span className="ml-auto flex items-center gap-1.5 font-extrabold text-gold-text">
              합계 <PointPrice value={total} size="sm" />
            </span>
          </div>

          <button type="button" onClick={buy} className="w-full cursor-pointer rounded-[14px] bg-brand p-[15px] text-base font-extrabold text-white">
            구매하기
          </button>
        </div>
      </div>

      {celebrate && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-[rgba(46,54,42,.45)] p-5">
          <div className="relative w-full max-w-[400px] animate-pop overflow-hidden rounded-3xl bg-white px-7 py-8 text-center">
            {CONFETTI.map((c, i) => (
              <span
                key={i}
                className="absolute -top-2 animate-confettiFall text-base"
                style={{ left: c.left, animationDuration: c.dur, animationDelay: c.delay, animationIterationCount: 'infinite' }}
              >
                {c.emoji}
              </span>
            ))}
            <div className="text-[66px]">🎉</div>
            <h3 className="mb-2 mt-3.5 text-xl font-extrabold">축하해요!</h3>
            <p className="mb-6 leading-[1.6] text-[#6d7a68]">{base.name}가 모두 모였어요.<br />지금 바로 교환할 수 있어요 🎉</p>
            <div className="flex gap-2.5">
              <button type="button" onClick={() => router.push(`/exchange/new?cardId=${base.id}`)} className="flex-1 cursor-pointer rounded-xl bg-brand p-[13px] font-extrabold text-white">
                교환 신청하기
              </button>
              <button type="button" onClick={() => setCelebrate(false)} className="cursor-pointer rounded-xl border-[1.5px] border-line bg-white px-[18px] py-[13px] font-bold text-sub">
                나중에
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
