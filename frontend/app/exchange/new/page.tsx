'use client';
import { useState, Suspense } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { CARDS, ADDRESSES } from '@/lib/data';

function ExchangeNewInner() {
  const params = useSearchParams();
  const cardId = Number(params.get('cardId')) || CARDS[0].id;
  const card = CARDS.find((c) => c.id === cardId) || CARDS[0];
  const [selAddr, setSelAddr] = useState(0);
  const [done, setDone] = useState(false);
  const outOfStock = card.stock <= 0;

  if (done) {
    return (
      <div className="container">
        <div className="mx-auto my-10 max-w-[480px] animate-pop rounded-[22px] bg-white px-[30px] py-11 text-center shadow-[0_8px_30px_rgba(124,179,66,.12)]">
          <div className="text-[70px]">🍉</div>
          <h1 className="mb-2 mt-4 text-2xl font-extrabold">신청이 접수됐어요!</h1>
          <p className="mb-[26px] leading-[1.6] text-[#6d7a68]">밭에서 가장 좋은 아이로 골라 보내드릴게요 🍉</p>
          <div className="flex flex-wrap justify-center gap-2.5">
            <Link href="/my/exchanges" className="rounded-xl bg-brand px-6 py-[13px] font-bold text-white hover:text-white">교환 내역 보기</Link>
            <Link href="/cards" className="rounded-xl border-[1.5px] border-[#cfe0b6] bg-white px-6 py-[13px] font-bold text-brand-dark">카드 더 모으기</Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="container">
      <Link href="/my/cards" className="text-sm font-semibold text-sub">← 내 카드</Link>
      <h1 className="mb-5 mt-3.5 text-[26px] font-extrabold">실물 교환 신청</h1>
      <div className="grid items-start gap-[22px] [grid-template-columns:repeat(auto-fit,minmax(280px,1fr))]">
        <div>
          <div className="flex items-center gap-4 rounded-[18px] bg-white p-5 shadow-card">
            <div className="flex h-[66px] w-[66px] items-center justify-center rounded-[14px] text-[34px]" style={{ background: card.grad }}>{card.emoji}</div>
            <div className="text-[26px] text-[#c2c9b8]">→</div>
            <div className="flex h-[66px] w-[66px] items-center justify-center rounded-[14px] text-[34px]" style={{ background: card.realGrad }}>{card.realEmoji}</div>
            <div className="flex-1">
              <div className="font-extrabold">{card.realName}</div>
              <div className="text-[13px] text-sub">카드 {card.required}장을 사용해요</div>
            </div>
          </div>

          <div className="mb-3 mt-5 font-extrabold">배송지</div>
          <div className="flex flex-col gap-2.5">
            {ADDRESSES.map((a, i) => (
              <button
                key={a.id}
                type="button"
                onClick={() => setSelAddr(i)}
                className={`cursor-pointer rounded-[14px] border-2 bg-white px-4 py-[15px] text-left ${selAddr === i ? 'border-brand' : 'border-[#eceee5]'}`}
              >
                <div className="flex items-center gap-2 font-bold">
                  {a.name}
                  {a.isDefault && <span className="rounded-full bg-brand-soft px-2 py-0.5 text-[11px] text-brand-dark">기본</span>}
                </div>
                <div className="mt-1 text-[13.5px] text-sub">{a.phone} · {a.addr}</div>
              </button>
            ))}
          </div>
        </div>

        <div className="rounded-[18px] bg-white p-[22px] shadow-card">
          <div className="mb-3.5 font-extrabold">신청 요약</div>
          <div className="flex justify-between py-[9px] text-[14.5px]"><span className="text-sub">교환 상품</span><span className="font-bold">{card.realName}</span></div>
          <div className="flex justify-between border-b border-[#f2f3ec] py-[9px] text-[14.5px]"><span className="text-sub">사용 카드</span><span className="font-bold">{card.name} {card.required}장</span></div>
          {outOfStock ? (
            <>
              <div className="my-3.5 rounded-xl bg-danger-soft px-3.5 py-3 text-[13.5px] font-semibold text-[#b5502f]">
                지금은 실물 수량이 모두 소진됐어요. 다시 채워지면 알려드릴게요.
              </div>
              <button type="button" disabled className="mt-1.5 w-full cursor-not-allowed rounded-[13px] bg-line p-[15px] font-extrabold text-[#a9b3a0]">교환 불가</button>
            </>
          ) : (
            <>
              <p className="my-3.5 text-[12.5px] text-[#a9b3a0]">한 번의 신청으로 실물 하나를 받아요. 여러 개는 나눠서 신청해 주세요.</p>
              <button type="button" onClick={() => setDone(true)} className="w-full cursor-pointer rounded-[13px] bg-brand p-[15px] font-extrabold text-white">교환 신청하기</button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

export default function ExchangeNew() {
  return (<Suspense fallback={<div className="container" />}><ExchangeNewInner /></Suspense>);
}
