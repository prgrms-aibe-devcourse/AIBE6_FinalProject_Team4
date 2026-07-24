'use client';
import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useStore, fmt } from '@/lib/store';
import PointPrice from '@/components/PointPrice';
import { PRODUCTS } from '@/lib/data';

interface CartItem {
  id: number;
  qty: number;
  notice: 'sold' | 'price' | null;
}

const INITIAL: CartItem[] = [
  { id: 1, qty: 1, notice: null }, { id: 3, qty: 2, notice: null },
  { id: 5, qty: 1, notice: 'sold' }, { id: 6, qty: 1, notice: 'price' },
];

export default function Cart() {
  const router = useRouter();
  const { balance } = useStore();
  const [cart, setCart] = useState<CartItem[]>(INITIAL);
  const [checked, setChecked] = useState<Record<number, boolean>>({ 1: true, 3: true, 5: false, 6: true });
  const prod = (id: number) => PRODUCTS.find((p) => p.id === id);

  const total = cart.filter((i) => checked[i.id] && (prod(i.id)?.stock ?? 0) > 0).reduce((s, i) => s + (prod(i.id)?.price ?? 0) * i.qty, 0);
  const short = Math.max(0, total - balance);

  return (
    <div className="container">
      <h1 className="mb-5 text-[26px] font-extrabold">장바구니</h1>
      <div className="grid items-start gap-[22px] [grid-template-columns:repeat(auto-fit,minmax(300px,1fr))]">
        <div className="flex flex-col gap-3">
          {cart.map((i) => {
            const p = prod(i.id); if (!p) return null; const sold = p.stock <= 0; const on = checked[i.id] && !sold;
            return (
              <div key={i.id} className="flex items-center gap-3.5 rounded-2xl bg-white p-[15px] shadow-card">
                <button
                  type="button"
                  onClick={() => !sold && setChecked({ ...checked, [i.id]: !checked[i.id] })}
                  className={`flex h-[22px] w-[22px] flex-none cursor-pointer items-center justify-center rounded-[7px] border-2 text-[13px] text-white ${
                    on ? 'border-brand bg-brand' : 'border-[#cfd6c6] bg-white'
                  }`}
                >
                  {on ? '✓' : ''}
                </button>
                <div
                  className={`flex h-16 w-16 flex-none items-center justify-center rounded-xl text-[32px] ${sold ? 'opacity-60 grayscale' : ''}`}
                  style={{ background: p.grad }}
                >
                  {p.emoji}
                </div>
                <div className="min-w-0 flex-1">
                  <div className="text-[14.5px] font-extrabold">{p.name}</div>
                  {i.notice && (
                    <div className={`mt-1 inline-block rounded-full px-2 py-0.5 text-[11.5px] font-bold ${
                      i.notice === 'sold' ? 'bg-[#fdecec] text-danger' : 'bg-[#fff3d6] text-[#b5771a]'
                    }`}>
                      {i.notice === 'sold' ? '품절됐어요' : '가격이 바뀌었어요'}
                    </div>
                  )}
                  <PointPrice value={p.price * i.qty} size="sm" className="mt-1.5" />
                </div>
                <div className="flex items-center overflow-hidden rounded-[10px] border-[1.5px] border-line">
                  <button type="button" onClick={() => setCart(cart.map((x) => x.id === i.id ? { ...x, qty: Math.max(1, x.qty - 1) } : x))} className="flex h-8 w-8 cursor-pointer items-center justify-center text-[#6d7a68]">−</button>
                  <div className="w-[34px] text-center text-sm font-bold">{i.qty}</div>
                  <button type="button" onClick={() => setCart(cart.map((x) => x.id === i.id ? { ...x, qty: Math.min(99, x.qty + 1) } : x))} className="flex h-8 w-8 cursor-pointer items-center justify-center text-[#6d7a68]">+</button>
                </div>
                <button type="button" onClick={() => setCart(cart.filter((x) => x.id !== i.id))} className="cursor-pointer p-1 text-xl text-[#c2c9b8]">×</button>
              </div>
            );
          })}
        </div>

        <div className="rounded-[18px] bg-white p-[22px] shadow-card">
          <div className="mb-4 font-extrabold">주문 요약</div>
          <div className="flex items-center justify-between py-2 text-[14.5px]">
            <span className="text-sub">선택 상품 합계</span>
            <PointPrice value={total} size="sm" />
          </div>
          <div className="flex justify-between border-b border-[#f2f3ec] py-2 text-[14.5px]">
            <span className="text-sub">내 잔액</span><span className="font-bold">{fmt(balance)}P</span>
          </div>
          {short > 0 && (
            <div className="my-3.5 rounded-[11px] bg-danger-soft px-[13px] py-[11px] text-[13px] font-semibold text-danger">
              포인트가 {fmt(short)}P 부족해요.{' '}
              <Link href="/my/points/charge" className="font-extrabold text-danger underline hover:text-danger">충전하러 가기</Link>
            </div>
          )}
          <button type="button" onClick={() => total > 0 && router.push('/checkout')} className="mt-4 w-full cursor-pointer rounded-[13px] bg-brand p-[15px] font-extrabold text-white">
            주문하기
          </button>
        </div>
      </div>
    </div>
  );
}
