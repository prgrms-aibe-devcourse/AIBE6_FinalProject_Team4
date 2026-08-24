'use client';
import { Suspense, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { ApiError } from '@/lib/api';
import { useStore, fmt } from '@/lib/store';
import { useUI } from '@/lib/ui';
import PointPrice from '@/components/PointPrice';
import AddressForm, { AddressFields, EMPTY_ADDRESS_FIELDS, isCompleteAddress } from '@/components/AddressForm';
import { CartItemData, createOrder, getCart, OrderDetailData } from '@/lib/order-api';
import {
  calculateOrderPointUsage,
  getMaximumOrderFreePoint,
  ORDER_FREE_POINT_UNIT,
} from '@/features/order/point-policy';

function CheckoutInner() {
  const params = useSearchParams();
  const { state, hydrated, refreshWallet, refreshCartCount } = useStore();
  const { showToast, askConfirm } = useUI();
  const selectedIds = (params.get('ids') || '')
    .split(',')
    .map((value) => Number(value))
    .filter((id) => Number.isInteger(id) && id > 0);

  const [items, setItems] = useState<CartItemData[]>([]);
  const [addressFields, setAddressFields] = useState<AddressFields>(EMPTY_ADDRESS_FIELDS);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [useFreePoint, setUseFreePoint] = useState(false);
  const [freePointInput, setFreePointInput] = useState(0);
  const [ordering, setOrdering] = useState(false);
  const [result, setResult] = useState<OrderDetailData | null>(null);
  // Fixed per payment attempt so a client-side timeout/retry replays the same idempotency
  // key instead of minting a fresh one — otherwise the server's replay protection (which
  // hashes address + free-point request into the key) is bypassed and a request that
  // actually succeeded gets re-run as a brand-new order on retry, double-charging points.
  const idempotencyKeyRef = useRef<string | null>(null);

  useEffect(() => {
    if (!hydrated || !state.accessToken) return;
    if (selectedIds.length === 0) {
      setError('주문할 상품을 먼저 장바구니에서 선택해 주세요.');
      setLoading(false);
      return;
    }

    setLoading(true);
    setError('');
    getCart(state.accessToken)
      .then((cart) => {
        const selected = cart.items.filter((item) => selectedIds.includes(item.id));
        if (selected.length === 0) {
          setError('선택한 상품을 장바구니에서 찾을 수 없어요. 다시 선택해 주세요.');
          return;
        }
        setItems(selected);
      })
      .catch((requestError) => {
        setError(
          requestError instanceof ApiError
            ? requestError.message
            : '주문 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.',
        );
      })
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hydrated, state.accessToken]);

  const total = items.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0);
  const requestedFreePoint = useFreePoint ? freePointInput : 0;
  const maximumFreePoint = getMaximumOrderFreePoint(total, state.wallet.free);
  const pointUsage = calculateOrderPointUsage({
    totalPoint: total,
    paidPoint: state.wallet.paid,
    freePoint: state.wallet.free,
    requestedFreePoint,
  });
  const addressValid = isCompleteAddress(addressFields);

  const place = async () => {
    if (ordering || !pointUsage.valid || !addressValid) return;
    if (!idempotencyKeyRef.current) idempotencyKeyRef.current = crypto.randomUUID();
    setOrdering(true);
    try {
      const order = await createOrder(
        {
          cartItemIds: items.map((item) => item.id),
          requestedFreePoint,
          receiverName: addressFields.receiverName.trim(),
          receiverPhone: addressFields.receiverPhone.trim(),
          zipCode: addressFields.zipCode.trim(),
          address: addressFields.address.trim(),
          addressDetail: addressFields.addressDetail.trim() || undefined,
        },
        idempotencyKeyRef.current,
        state.accessToken,
      );
      await Promise.all([refreshWallet(), refreshCartCount()]);
      setResult(order);
    } catch (requestError) {
      // A 4xx means the server explicitly rejected this attempt (e.g. bad request, stock
      // gone) — safe to mint a new key next try. Anything else (network/timeout/5xx) is
      // ambiguous about whether the order actually went through, so keep the same key.
      if (requestError instanceof ApiError && requestError.status >= 400 && requestError.status < 500) {
        idempotencyKeyRef.current = null;
      }
      showToast(
        requestError instanceof ApiError ? requestError.message : '주문에 실패했어요. 잠시 후 다시 시도해 주세요.',
        'err',
      );
    } finally {
      setOrdering(false);
    }
  };

  if (!hydrated) return <div className="container" />;

  if (!state.accessToken) {
    return (
      <div className="container">
        <div className="rounded-[22px] bg-white px-5 py-14 text-center text-sub">
          <p>주문·결제는 로그인 후 이용할 수 있어요.</p>
          <Link href="/auth" className="mt-4 inline-block rounded-xl bg-brand px-5 py-2.5 font-bold text-white hover:text-white">
            로그인하러 가기
          </Link>
        </div>
      </div>
    );
  }

  if (result) {
    return (
      <div className="container">
        <div className="mx-auto my-10 max-w-[480px] animate-pop rounded-[22px] bg-white px-[30px] py-11 text-center shadow-[0_8px_30px_rgba(124,179,66,.12)]">
          <span className="material-symbols-outlined text-[66px]">eco</span>
          <h1 className="mb-2 mt-4 text-[23px] font-extrabold">주문이 완료됐어요!</h1>
          <p className="mb-5 leading-[1.6] text-[#6d7a68]">정성껏 준비해서 보내드릴게요</p>
          <div className="mb-[22px] rounded-[14px] bg-[#F6F9EF] p-4 text-left text-sm">
            <div className="flex justify-between py-1"><span className="text-sub">주문번호</span><span className="font-extrabold">#{result.order.id}</span></div>
            <div className="flex justify-between py-1"><span className="text-sub">배송지</span><span className="font-bold">{result.order.address}</span></div>
            <div className="mt-2 flex justify-between border-t border-[#e4ead8] pt-2"><span className="text-sub">충전 포인트 사용</span><span className="font-bold">{fmt(result.order.usedPaidPoint)}P</span></div>
            <div className="flex justify-between py-1"><span className="text-sub">보너스 포인트 사용</span><span className="font-bold">{fmt(result.order.usedFreePoint)}P</span></div>
          </div>
          <div className="flex flex-wrap justify-center gap-2.5">
            <Link href="/my/orders" className="rounded-xl bg-brand px-6 py-[13px] font-bold text-white hover:text-white">주문 내역 보기</Link>
            <Link href="/shop" className="rounded-xl border-[1.5px] border-[#cfe0b6] bg-white px-6 py-[13px] font-bold text-brand-dark">쇼핑 계속하기</Link>
          </div>
        </div>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="container">
        <div className="rounded-[22px] bg-white py-14 text-center text-sub">주문 정보를 불러오고 있어요</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="container">
        <div className="rounded-[22px] bg-white px-5 py-14 text-center text-sub">
          <p>{error}</p>
          <Link href="/cart" className="mt-4 inline-block rounded-xl bg-brand px-5 py-2.5 font-bold text-white hover:text-white">
            장바구니로 돌아가기
          </Link>
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
          <div className="mb-6 rounded-2xl bg-white p-[18px] shadow-card">
            <div className="mb-3 font-extrabold">배송지</div>
            <AddressForm accessToken={state.accessToken} value={addressFields} onChange={setAddressFields} />
          </div>

          <div className="rounded-2xl bg-white p-[18px] shadow-card">
            <div className="mb-2 font-extrabold">주문 상품</div>
            {items.map((item) => (
              <div key={item.id} className="flex items-center gap-3 border-b border-[#f4f5ee] py-3 last:border-b-0">
                <div
                  className="flex h-12 w-12 items-center justify-center rounded-[11px] bg-brand-soft bg-cover bg-center text-2xl"
                  style={
                    item.imageUrl
                      ? { backgroundImage: `url("${item.imageUrl}")` }
                      : undefined
                  }
                  role={item.imageUrl ? "img" : undefined}
                  aria-label={item.imageUrl ? item.productName : undefined}
                >
                  {!item.imageUrl && <span className="material-symbols-outlined">potted_plant</span>}
                </div>
                <div className="flex-1">
                  <div className="text-sm font-bold">{item.productName}</div>
                  <div className="text-[12.5px] text-sub">수량 {item.quantity}</div>
                </div>
                <PointPrice value={item.unitPrice * item.quantity} size="sm" />
              </div>
            ))}
          </div>
        </div>

        <div className="rounded-[18px] bg-white p-[22px] shadow-card">
          <div className="mb-4 font-extrabold">결제</div>
          <div className="flex justify-between py-2 text-[14.5px]"><span className="text-sub">충전 포인트</span><span className="font-bold">{fmt(state.wallet.paid)}P</span></div>
          <div className="flex justify-between py-2 text-[14.5px]"><span className="text-sub">보너스 포인트</span><span className="font-bold">{fmt(state.wallet.free)}P</span></div>
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
                <span className="block text-sm font-extrabold">보너스 포인트 함께 사용</span>
                <span className="mt-0.5 block text-xs leading-[1.5] text-sub">
                  선택하지 않으면 충전 포인트로만 결제돼요.
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
                    aria-label="사용할 보너스 포인트"
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
            <div className="flex justify-between py-1.5"><span className="text-sub">충전 포인트 차감</span><span className="font-bold">{fmt(pointUsage.usedPaidPoint)}P</span></div>
            <div className="flex justify-between py-1.5"><span className="text-sub">보너스 포인트 차감</span><span className="font-bold">{fmt(pointUsage.usedFreePoint)}P</span></div>
            <div className="mt-1.5 flex justify-between border-t border-[#f2f3ec] py-2"><span className="text-sub">충전 포인트 잔액</span><span className="font-extrabold">{fmt(Math.max(0, pointUsage.remainingPaidPoint))}P</span></div>
            <div className="flex justify-between pb-2"><span className="text-sub">보너스 포인트 잔액</span><span className="font-extrabold">{fmt(Math.max(0, pointUsage.remainingFreePoint))}P</span></div>
          </div>

          {pointUsage.error && (
            <div className="mb-4 mt-2 rounded-[11px] bg-danger-soft px-[13px] py-[11px] text-[13px] font-semibold text-danger">
              {pointUsage.error}
              {!useFreePoint && maximumFreePoint > 0 && (
                <span className="mt-1 block">보너스 포인트를 함께 사용하려면 위 항목을 선택해 주세요.</span>
              )}
            </div>
          )}

          <button
            type="button"
            onClick={() =>
              askConfirm({
                icon: 'shopping_cart_checkout',
                title: '주문을 완료할까요?',
                body: `충전 포인트 ${fmt(pointUsage.usedPaidPoint)}P, 보너스 포인트 ${fmt(pointUsage.usedFreePoint)}P가 사용돼요.`,
                ok: '결제하고 주문 완료',
                onOk: place,
              })
            }
            disabled={ordering || !pointUsage.valid || !addressValid}
            className={`w-full rounded-[13px] p-[15px] font-extrabold text-white ${
              ordering || !pointUsage.valid || !addressValid ? 'cursor-not-allowed bg-[#b0c894]' : 'cursor-pointer bg-brand'
            }`}
          >
            {ordering ? '주문 처리 중…' : '결제하고 주문 완료'}
          </button>
        </div>
      </div>
    </div>
  );
}

export default function Checkout() {
  return (
    <Suspense fallback={<div className="container" />}>
      <CheckoutInner />
    </Suspense>
  );
}
