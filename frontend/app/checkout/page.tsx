'use client';
import { useState } from 'react';
import Link from 'next/link';
import { useStore, fmt } from '@/lib/store';
import PointPrice from '@/components/PointPrice';
import { PRODUCTS, ADDRESSES } from '@/lib/data';

const ITEMS = [{ id: 1, qty: 1 }, { id: 3, qty: 2 }];

export default function Checkout() {
  const { balance, spend, set } = useStore();
  const [selAddr, setSelAddr] = useState(0);
  const [ordering, setOrdering] = useState(false);
  const [done, setDone] = useState(false);
  const prod = (id: number) => PRODUCTS.find((p) => p.id === id);
  const total = ITEMS.reduce((s, i) => s + (prod(i.id)?.price ?? 0) * i.qty, 0);

  const place = () => {
    if (ordering) return;
    if (total > balance) return;
    setOrdering(true);
    setTimeout(() => { spend(total); set((s) => ({ cartCount: Math.max(0, s.cartCount - ITEMS.length) })); setDone(true); }, 600);
  };

  if (done) {
    return (
      <div className="container">
        <div className="mx-auto my-10 max-w-[480px] animate-pop rounded-[22px] bg-white px-[30px] py-11 text-center shadow-[0_8px_30px_rgba(124,179,66,.12)]">
          <div className="text-[66px]">🌿</div>
          <h1 className="mb-2 mt-4 text-[23px] font-extrabold">주문이 완료됐어요!</h1>
          <p className="mb-5 leading-[1.6] text-[#6d7a68]">정성껏 준비해서 보내드릴게요 🌿</p>
          <div className="mb-[22px] rounded-[14px] bg-[#F6F9EF] p-4 text-left text-sm">
            <div className="flex justify-between py-1"><span className="text-sub">주문번호</span><span className="font-extrabold">ORD-20260721-0043</span></div>
            <div className="flex justify-between py-1"><span className="text-sub">배송지</span><span className="font-bold">{ADDRESSES[selAddr].addr.split(',')[0]}</span></div>
          </div>
          <div className="flex flex-wrap justify-center gap-2.5">
            <Link href="/my/orders" className="rounded-xl bg-brand px-6 py-[13px] font-bold text-white hover:text-white">주문 내역 보기</Link>
            <Link href="/shop" className="rounded-xl border-[1.5px] border-[#cfe0b6] bg-white px-6 py-[13px] font-bold text-brand-dark">쇼핑 계속하기</Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="container">
      <Link href="/cart" className="text-sm font-semibold text-sub">← 장바구니</Link>
      <h1 className="mb-5 mt-3.5 text-[26px] font-extrabold">주문·결제</h1>
      <div className="grid items-start gap-[22px] [grid-template-columns:repeat(auto-fit,minmax(300px,1fr))]">
        <div>
          <div className="mb-3 font-extrabold">배송지</div>
          <div className="mb-6 flex flex-col gap-2.5">
            {ADDRESSES.map((a, i) => (
              <button
                key={a.id}
                type="button"
                onClick={() => setSelAddr(i)}
                className={`cursor-pointer rounded-[14px] border-2 bg-white px-4 py-[15px] text-left ${
                  selAddr === i ? 'border-brand' : 'border-[#eceee5]'
                }`}
              >
                <div className="flex items-center gap-2 font-bold">
                  {a.name}
                  {a.isDefault && <span className="rounded-full bg-brand-soft px-2 py-0.5 text-[11px] text-brand-dark">기본</span>}
                </div>
                <div className="mt-1 text-[13.5px] text-sub">{a.phone} · {a.addr}</div>
              </button>
            ))}
            <button type="button" className="cursor-pointer rounded-[14px] border-[1.5px] border-dashed border-[#cfe0b6] bg-white p-3.5 text-center font-bold text-brand-dark">
              + 새 배송지 입력
            </button>
          </div>

          <div className="mb-3 font-extrabold">주문 상품</div>
          <div className="rounded-2xl bg-white px-[18px] py-1.5 shadow-card">
            {ITEMS.map((i) => { const p = prod(i.id); if (!p) return null; return (
              <div key={i.id} className="flex items-center gap-3 border-b border-[#f4f5ee] py-3">
                <div className="flex h-12 w-12 items-center justify-center rounded-[11px] text-2xl" style={{ background: p.grad }}>{p.emoji}</div>
                <div className="flex-1">
                  <div className="text-sm font-bold">{p.name}</div>
                  <div className="text-[12.5px] text-sub">수량 {i.qty}</div>
                </div>
                <PointPrice value={p.price * i.qty} size="sm" />
              </div>
            ); })}
          </div>
        </div>

        <div className="rounded-[18px] bg-white p-[22px] shadow-card">
          <div className="mb-4 font-extrabold">결제</div>
          <div className="flex justify-between py-2 text-[14.5px]"><span className="text-sub">보유 포인트</span><span className="font-bold">{fmt(balance)}P</span></div>
          <div className="flex items-center justify-between py-2 text-[14.5px]"><span className="text-sub">주문 금액</span><PointPrice value={total} size="sm" /></div>
          <div className="mt-1.5 flex justify-between border-t border-[#f2f3ec] py-2 text-[14.5px]"><span className="text-sub">결제 후 잔액</span><span className="font-extrabold">{fmt(balance - total)}P</span></div>
          <p className="mb-4 mt-3 text-xs text-[#a9b3a0]">무상 포인트가 먼저 사용돼요.</p>
          <button
            type="button"
            onClick={place}
            disabled={ordering}
            className={`w-full rounded-[13px] p-[15px] font-extrabold text-white ${ordering ? 'cursor-default bg-[#b0c894]' : 'cursor-pointer bg-brand'}`}
          >
            {ordering ? '주문 처리 중…' : '결제하고 주문 완료'}
          </button>
        </div>
      </div>
    </div>
  );
}
