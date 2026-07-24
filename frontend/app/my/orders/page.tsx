'use client';
import { useState } from 'react';
import { useUI } from '@/lib/ui';
import { grads } from '@/lib/theme';
import { fmt } from '@/lib/store';
import PointPrice from '@/components/PointPrice';

const PAY: Record<string, [string, string]> = {
  PAID: ['결제완료', 'bg-[#E8F3D8] text-brand-text'],
  CANCELLED: ['취소됨', 'bg-[#f0f1ea] text-[#8a8a8a]'],
  PURCHASE_CONFIRMED: ['구매확정', 'bg-[#E8F3D8] text-brand-text'],
};
const DEL: Record<string, [string, string]> = {
  PREPARING: ['준비중', 'bg-[#FFF3CC] text-gold-text'],
  SHIPPING: ['배송중', 'bg-[#E3F0FA] text-[#3a76a8]'],
  DELIVERED: ['배송완료', 'bg-[#E8F3D8] text-brand-text'],
};

interface OrderItem {
  name: string;
  emoji: string;
  grad: string;
  qty: number;
}

interface Order {
  id: number;
  orderNo: string;
  date: string;
  status: string;
  delivery: string;
  total: number;
  free: number;
  paid: number;
  items: OrderItem[];
}

const INITIAL: Order[] = [
  { id: 1, orderNo: 'ORD-20260721-0043', date: '2026.07.21', status: 'PAID', delivery: 'PREPARING', total: 2100, free: 1100, paid: 1000, items: [{ name: '새싹 재배 키트', emoji: '🌱', grad: grads.sprout, qty: 1 }, { name: '바질 모종', emoji: '🌿', grad: grads.basil, qty: 1 }] },
  { id: 2, orderNo: 'ORD-20260714-0031', date: '2026.07.14', status: 'PAID', delivery: 'DELIVERED', total: 1500, free: 500, paid: 1000, items: [{ name: '미니 화분 세트', emoji: '🪴', grad: grads.mint, qty: 1 }] },
  { id: 3, orderNo: 'ORD-20260709-0022', date: '2026.07.09', status: 'PAID', delivery: 'SHIPPING', total: 1200, free: 1200, paid: 0, items: [{ name: '방울토마토 모종', emoji: '🍅', grad: grads.tomato, qty: 1 }] },
  { id: 4, orderNo: 'ORD-20260702-0014', date: '2026.07.02', status: 'CANCELLED', delivery: 'PREPARING', total: 800, free: 800, paid: 0, items: [{ name: '상추 모종', emoji: '🥬', grad: grads.lettuce, qty: 1 }] },
];

const CHIP = 'rounded-full px-[11px] py-1 text-xs font-extrabold';

export default function Orders() {
  const { showToast, askConfirm } = useUI();
  const [orders, setOrders] = useState<Order[]>(INITIAL);

  const cancel = (o: Order) => askConfirm({ icon: 'undo', title: '주문을 취소할까요?', ok: '주문 취소', danger: true, body: '포인트와 재고가 다시 돌아와요. 취소할까요?',
    onOk: () => { setOrders(orders.map((x) => x.id === o.id ? { ...x, status: 'CANCELLED' } : x)); showToast('주문을 취소하고 포인트를 돌려드렸어요.'); } });
  const confirm = (o: Order) => askConfirm({ icon: 'check_circle', title: '구매를 확정할까요?', ok: '구매 확정', body: '구매 확정 후에는 취소할 수 없어요.',
    onOk: () => { setOrders(orders.map((x) => x.id === o.id ? { ...x, status: 'PURCHASE_CONFIRMED' } : x)); showToast('구매를 확정했어요. 고마워요 🌿'); } });

  return (
    <div className="container max-w-[960px]">
      <h1 className="mb-5 text-2xl font-extrabold">주문 내역</h1>
      <div className="flex flex-col gap-4">
        {orders.map((o) => {
          const pay = PAY[o.status], del = DEL[o.delivery];
          const canCancel = o.status === 'PAID' && o.delivery === 'PREPARING';
          const canConfirm = o.status === 'PAID' && o.delivery === 'DELIVERED';
          const shipLocked = o.status === 'PAID' && o.delivery === 'SHIPPING';
          return (
            <div key={o.id} className="rounded-[18px] bg-white p-5 shadow-card">
              <div className="mb-3.5 flex flex-wrap items-center justify-between gap-2">
                <div>
                  <span className="font-extrabold">{o.orderNo}</span>
                  <span className="ml-2 text-[13px] text-faint">{o.date}</span>
                </div>
                <div className="flex gap-1.5">
                  <span className={`${CHIP} ${pay[1]}`}>{pay[0]}</span>
                  <span className={`${CHIP} ${del[1]}`}>{del[0]}</span>
                </div>
              </div>

              <div className="mb-3.5 flex flex-col gap-2">
                {o.items.map((it, i) => (
                  <div key={i} className="flex items-center gap-3">
                    <div className="flex h-11 w-11 items-center justify-center rounded-[11px] text-[22px]" style={{ background: it.grad }}>{it.emoji}</div>
                    <div className="flex-1 text-sm font-semibold">{it.name} <span className="text-faint">× {it.qty}</span></div>
                  </div>
                ))}
              </div>

              <div className="flex flex-wrap items-center justify-between gap-2.5 border-t border-[#f2f3ec] pt-3.5">
                <div className="flex items-center gap-1.5 font-extrabold text-gold-text">
                  총 <PointPrice value={o.total} size="sm" />
                  <span className="text-xs font-semibold text-faint">(보상 {fmt(o.free)}P · 충전 {fmt(o.paid)}P)</span>
                </div>
                <div className="flex items-center gap-2">
                  {canCancel && (
                    <button type="button" onClick={() => cancel(o)} className="cursor-pointer rounded-[11px] border-[1.5px] border-[#e8bdad] bg-white px-4 py-[9px] font-bold text-[#b5502f]">주문 취소</button>
                  )}
                  {canConfirm && (
                    <button type="button" onClick={() => confirm(o)} className="cursor-pointer rounded-[11px] bg-brand px-4 py-[9px] font-bold text-white">구매 확정</button>
                  )}
                  {shipLocked && <span className="text-[12.5px] text-faint">배송이 시작되어 취소할 수 없어요</span>}
                </div>
              </div>
              {canConfirm && <div className="mt-2 text-[12.5px] text-faint">배송완료 7일 후 자동으로 확정돼요.</div>}
            </div>
          );
        })}
      </div>
    </div>
  );
}
