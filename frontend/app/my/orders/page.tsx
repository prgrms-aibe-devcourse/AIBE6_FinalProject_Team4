'use client';
import { useCallback, useEffect, useState } from 'react';
import { ApiError } from '@/lib/api';
import { useStore, fmt } from '@/lib/store';
import { useUI } from '@/lib/ui';
import PointPrice from '@/components/PointPrice';
import { formatPhone } from '@/components/AddressForm';
import {
  cancelOrder,
  confirmOrder,
  getOrder,
  getOrders,
  OrderData,
  OrderItemData,
} from '@/lib/order-api';

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

const CHIP = 'rounded-full px-[11px] py-1 text-xs font-extrabold';

const formatDate = (iso: string) => iso.slice(0, 10).replaceAll('-', '.');

export default function Orders() {
  const { state, hydrated, refreshWallet } = useStore();
  const { showToast, askConfirm } = useUI();
  const [orders, setOrders] = useState<OrderData[]>([]);
  const [itemsByOrderId, setItemsByOrderId] = useState<Record<number, OrderItemData[]>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busyIds, setBusyIds] = useState<Record<number, boolean>>({});

  const load = useCallback(async () => {
    if (!state.accessToken) return;
    setLoading(true);
    setError('');
    try {
      const page = await getOrders(state.accessToken);
      setOrders(page.content);
      const details = await Promise.all(
        page.content.map((order) => getOrder(order.id, state.accessToken)),
      );
      const map: Record<number, OrderItemData[]> = {};
      details.forEach((detail) => { map[detail.order.id] = detail.items; });
      setItemsByOrderId(map);
    } catch (requestError) {
      setError(
        requestError instanceof ApiError
          ? requestError.message
          : '주문 내역을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.',
      );
    } finally {
      setLoading(false);
    }
  }, [state.accessToken]);

  useEffect(() => {
    if (!hydrated) return;
    void load();
  }, [hydrated, load]);

  useEffect(() => {
    if (loading || orders.length === 0 || !window.location.hash) return;
    const targetId = decodeURIComponent(window.location.hash.slice(1));
    if (!targetId.startsWith('order-')) return;
    window.requestAnimationFrame(() => {
      document.getElementById(targetId)?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    });
  }, [loading, orders]);

  const setBusy = (id: number, value: boolean) => setBusyIds((prev) => ({ ...prev, [id]: value }));

  const cancel = (order: OrderData) => askConfirm({
    icon: 'undo',
    title: '주문을 취소할까요?',
    ok: '주문 취소',
    danger: true,
    body: '포인트와 재고가 다시 돌아와요. 취소할까요?',
    onOk: async () => {
      setBusy(order.id, true);
      try {
        await cancelOrder(order.id, state.accessToken);
        await Promise.all([load(), refreshWallet()]);
        showToast('주문을 취소하고 포인트를 돌려드렸어요.');
      } catch (requestError) {
        showToast(
          requestError instanceof ApiError ? requestError.message : '취소에 실패했어요.',
          'err',
        );
      } finally {
        setBusy(order.id, false);
      }
    },
  });

  const confirm = (order: OrderData) => askConfirm({
    icon: 'check_circle',
    title: '구매를 확정할까요?',
    ok: '구매 확정',
    body: '구매 확정 후에는 취소할 수 없어요.',
    onOk: async () => {
      setBusy(order.id, true);
      try {
        await confirmOrder(order.id, state.accessToken);
        await load();
        showToast('구매를 확정했어요. 고마워요 🌿');
      } catch (requestError) {
        showToast(
          requestError instanceof ApiError ? requestError.message : '확정에 실패했어요.',
          'err',
        );
      } finally {
        setBusy(order.id, false);
      }
    },
  });

  if (!hydrated) return <div className="container" />;

  if (!state.accessToken) {
    return (
      <div className="container max-w-[960px]">
        <div className="rounded-[22px] bg-white px-5 py-14 text-center text-sub">
          주문 내역은 로그인 후 확인할 수 있어요.
        </div>
      </div>
    );
  }

  return (
    <div className="container max-w-[960px]">
      <h1 className="mb-5 text-2xl font-extrabold">주문 내역</h1>

      {loading ? (
        <div className="rounded-[22px] bg-white py-14 text-center text-sub">주문 내역을 불러오고 있어요 🌱</div>
      ) : error ? (
        <div className="rounded-[22px] bg-white px-5 py-14 text-center text-sub">{error}</div>
      ) : orders.length === 0 ? (
        <div className="rounded-[22px] bg-white py-14 text-center text-sub">아직 주문 내역이 없어요.</div>
      ) : (
        <div className="flex flex-col gap-4">
          {orders.map((order) => {
            const pay = PAY[order.status];
            const del = DEL[order.deliveryStatus];
            const shipLocked = order.status === 'PAID' && order.deliveryStatus === 'SHIPPING';
            const items = itemsByOrderId[order.id] || [];
            const busy = busyIds[order.id];
            return (
              <div
                id={`order-${order.id}`}
                key={order.id}
                className="scroll-mt-24 rounded-[18px] bg-white p-5 shadow-card target:ring-2 target:ring-brand"
              >
                <div className="mb-3.5 flex flex-wrap items-center justify-between gap-2">
                  <div>
                    <span className="font-extrabold">주문 #{order.id}</span>
                    <span className="ml-2 text-[13px] text-faint">{formatDate(order.orderedAt)}</span>
                  </div>
                  <div className="flex gap-1.5">
                    <span className={`${CHIP} ${pay[1]}`}>{pay[0]}</span>
                    <span className={`${CHIP} ${del[1]}`}>{del[0]}</span>
                  </div>
                </div>

                <div className="mb-3.5 flex flex-col gap-2">
                  {items.map((item) => (
                    <div key={item.id} className="flex items-center gap-3">
                      <div className="flex h-11 w-11 items-center justify-center rounded-[11px] bg-brand-soft text-[22px]">🌱</div>
                      <div className="flex-1 text-sm font-semibold">{item.productName} <span className="text-faint">× {item.quantity}</span></div>
                    </div>
                  ))}
                </div>

                <div className="mb-3.5 rounded-xl bg-[#F8FAF3] px-3.5 py-3 text-[13px]">
                  <div className="mb-1 flex items-center gap-1.5 font-bold text-ink">
                    <span className="material-symbols-outlined text-[16px] text-sub">local_shipping</span>
                    배송지
                  </div>
                  <div className="text-sub">
                    {order.receiverName} · {formatPhone(order.receiverPhone)}
                  </div>
                  <div className="text-sub">
                    {order.zipCode && `[${order.zipCode}] `}{order.address} {order.addressDetail}
                  </div>
                </div>

                <div className="flex flex-wrap items-center justify-between gap-2.5 border-t border-[#f2f3ec] pt-3.5">
                  <div className="flex items-center gap-1.5 font-extrabold text-gold-text">
                    총 <PointPrice value={order.totalPoint} size="sm" />
                    <span className="text-xs font-semibold text-faint">
                      (무상 {fmt(order.usedFreePoint)}P · 유상 {fmt(order.usedPaidPoint)}P)
                    </span>
                  </div>
                  <div className="flex items-center gap-2">
                    {order.cancellable && (
                      <button
                        type="button"
                        disabled={busy}
                        onClick={() => cancel(order)}
                        className="cursor-pointer rounded-[11px] border-[1.5px] border-[#e8bdad] bg-white px-4 py-[9px] font-bold text-[#b5502f] disabled:cursor-not-allowed disabled:opacity-60"
                      >
                        주문 취소
                      </button>
                    )}
                    {order.confirmable && (
                      <button
                        type="button"
                        disabled={busy}
                        onClick={() => confirm(order)}
                        className="cursor-pointer rounded-[11px] bg-brand px-4 py-[9px] font-bold text-white disabled:cursor-not-allowed disabled:opacity-60"
                      >
                        구매 확정
                      </button>
                    )}
                    {shipLocked && <span className="text-[12.5px] text-faint">배송이 시작되어 취소할 수 없어요</span>}
                  </div>
                </div>
                {order.confirmable && <div className="mt-2 text-[12.5px] text-faint">배송완료 7일 후 자동으로 확정돼요.</div>}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
