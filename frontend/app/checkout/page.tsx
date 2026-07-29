'use client';
import { useState } from 'react';
import Link from 'next/link';
import { useStore, fmt } from '@/lib/store';
import PointPrice from '@/components/PointPrice';
import { PRODUCTS, ADDRESSES } from '@/lib/data';
import {
  calculateOrderPointUsage,
  getMaximumOrderFreePoint,
  ORDER_FREE_POINT_UNIT,
} from '@/features/order/point-policy';

const ITEMS = [{ id: 1, qty: 1 }, { id: 3, qty: 2 }];

export default function Checkout() {
  const { state, spendForOrder, set } = useStore();
  const [selAddr, setSelAddr] = useState(0);
  const [useFreePoint, setUseFreePoint] = useState(false);
  const [freePointInput, setFreePointInput] = useState(0);
  const [ordering, setOrdering] = useState(false);
  const [done, setDone] = useState(false);
  const prod = (id: number) => PRODUCTS.find((p) => p.id === id);
  const total = ITEMS.reduce((s, i) => s + (prod(i.id)?.price ?? 0) * i.qty, 0);
  const requestedFreePoint = useFreePoint ? freePointInput : 0;
  const maximumFreePoint = getMaximumOrderFreePoint(total, state.wallet.free);
  const pointUsage = calculateOrderPointUsage({
    totalPoint: total,
    paidPoint: state.wallet.paid,
    freePoint: state.wallet.free,
    requestedFreePoint,
  });

  const place = () => {
    if (ordering) return;
    if (!pointUsage.valid) return;
    setOrdering(true);
    setTimeout(() => {
      spendForOrder(total, requestedFreePoint);
      set((s) => ({ cartCount: Math.max(0, s.cartCount - ITEMS.length) }));
      setDone(true);
    }, 600);
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
            <div className="mt-2 flex justify-between border-t border-[#e4ead8] pt-2"><span className="text-sub">유상 포인트 사용</span><span className="font-bold">{fmt(pointUsage.usedPaidPoint)}P</span></div>
            <div className="flex justify-between py-1"><span className="text-sub">무상 포인트 사용</span><span className="font-bold">{fmt(pointUsage.usedFreePoint)}P</span></div>
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
          <div className="flex justify-between py-2 text-[14.5px]"><span className="text-sub">유상 포인트</span><span className="font-bold">{fmt(state.wallet.paid)}P</span></div>
          <div className="flex justify-between py-2 text-[14.5px]"><span className="text-sub">무상 포인트</span><span className="font-bold">{fmt(state.wallet.free)}P</span></div>
          <div className="flex items-center justify-between py-2 text-[14.5px]"><span className="text-sub">주문 금액</span><PointPrice value={total} size="sm" /></div>

          <div className="my-4 rounded-[14px] border border-[#e4ead8] bg-[#FAFCF6] p-4">
            <label className="flex cursor-pointer items-start gap-3">
              <input
                type="checkbox"
                checked={useFreePoint}
                disabled={maximumFreePoint < ORDER_FREE_POINT_UNIT}
                onChange={(event) => {
                  setUseFreePoint(event.target.checked);
                  if (!event.target.checked) setFreePointInput(0);
                }}
                className="mt-0.5 h-[18px] w-[18px] accent-[#7CB342]"
              />
              <span>
                <span className="block text-sm font-extrabold">무상 포인트 함께 사용</span>
                <span className="mt-0.5 block text-xs leading-[1.5] text-sub">
                  선택하지 않으면 유상 포인트로만 결제돼요.
                </span>
              </span>
            </label>

            {useFreePoint && (
              <div className="mt-3 border-t border-[#e4ead8] pt-3">
                <div className="mb-2 flex items-center justify-between text-xs text-sub">
                  <span>{ORDER_FREE_POINT_UNIT}P 단위로 입력</span>
                  <span>최대 {fmt(maximumFreePoint)}P</span>
                </div>
                <div className="flex gap-2">
                  <input
                    type="number"
                    min={0}
                    max={maximumFreePoint}
                    step={ORDER_FREE_POINT_UNIT}
                    value={freePointInput}
                    onChange={(event) => setFreePointInput(Number(event.target.value))}
                    className="min-w-0 flex-1 rounded-xl border-[1.5px] border-line bg-white px-3 py-2.5 text-right font-bold outline-none focus:border-brand"
                    aria-label="사용할 무상 포인트"
                  />
                  <button
                    type="button"
                    onClick={() => setFreePointInput(maximumFreePoint)}
                    className="cursor-pointer whitespace-nowrap rounded-xl bg-brand-soft px-3.5 text-sm font-bold text-brand-dark"
                  >
                    최대 사용
                  </button>
                </div>
              </div>
            )}
          </div>

          <div className="border-t border-[#f2f3ec] pt-2 text-[14.5px]">
            <div className="flex justify-between py-1.5"><span className="text-sub">유상 포인트 차감</span><span className="font-bold">{fmt(pointUsage.usedPaidPoint)}P</span></div>
            <div className="flex justify-between py-1.5"><span className="text-sub">무상 포인트 차감</span><span className="font-bold">{fmt(pointUsage.usedFreePoint)}P</span></div>
            <div className="mt-1.5 flex justify-between border-t border-[#f2f3ec] py-2"><span className="text-sub">결제 후 유상 잔액</span><span className="font-extrabold">{fmt(Math.max(0, pointUsage.remainingPaidPoint))}P</span></div>
            <div className="flex justify-between pb-2"><span className="text-sub">결제 후 무상 잔액</span><span className="font-extrabold">{fmt(Math.max(0, pointUsage.remainingFreePoint))}P</span></div>
          </div>

          {pointUsage.error && (
            <div className="mb-4 mt-2 rounded-[11px] bg-danger-soft px-[13px] py-[11px] text-[13px] font-semibold text-danger">
              {pointUsage.error}
              {!useFreePoint && maximumFreePoint > 0 && (
                <span className="mt-1 block">무상 포인트를 함께 사용하려면 위 항목을 선택해 주세요.</span>
              )}
            </div>
          )}

          <button
            type="button"
            onClick={place}
            disabled={ordering || !pointUsage.valid}
            className={`w-full rounded-[13px] p-[15px] font-extrabold text-white ${
              ordering || !pointUsage.valid ? 'cursor-not-allowed bg-[#b0c894]' : 'cursor-pointer bg-brand'
            }`}
          >
            {ordering ? '주문 처리 중…' : '결제하고 주문 완료'}
          </button>
        </div>
      </div>
    </div>
  );
}
