'use client';
import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { ApiError } from '@/lib/api';
import { useStore, fmt } from '@/lib/store';
import { useUI } from '@/lib/ui';
import PointPrice from '@/components/PointPrice';
import {
  CartItemData,
  deleteCartItem,
  deleteCartItems,
  getCart,
  updateCartItemQuantity,
} from '@/lib/order-api';

export default function Cart() {
  const router = useRouter();
  const { state, hydrated, refreshCartCount } = useStore();
  const { showToast, askConfirm } = useUI();
  const [items, setItems] = useState<CartItemData[]>([]);
  const [checked, setChecked] = useState<Record<number, boolean>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busyIds, setBusyIds] = useState<Record<number, boolean>>({});
  const [bulkDeleting, setBulkDeleting] = useState(false);

  const loadCart = useCallback(async () => {
    if (!state.accessToken) return;
    setLoading(true);
    setError('');
    try {
      const cart = await getCart(state.accessToken);
      setItems(cart.items);
      setChecked((prevChecked) => {
        const next: Record<number, boolean> = {};
        cart.items.forEach((item) => {
          next[item.id] = item.id in prevChecked ? prevChecked[item.id] : !item.soldOut;
        });
        return next;
      });
    } catch (requestError) {
      setError(
        requestError instanceof ApiError
          ? requestError.message
          : '장바구니를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.',
      );
    } finally {
      setLoading(false);
    }
  }, [state.accessToken]);

  useEffect(() => {
    if (!hydrated) return;
    void loadCart();
  }, [hydrated, loadCart]);

  if (!hydrated) return <div className="container" />;

  if (!state.accessToken) {
    return (
      <div className="container">
        <div className="rounded-[22px] bg-white px-5 py-14 text-center text-sub shadow-card">
          <p>장바구니는 로그인 후 이용할 수 있어요.</p>
          <Link href="/auth" className="mt-4 inline-block rounded-xl bg-brand px-5 py-2.5 font-bold text-white hover:text-white">
            로그인하러 가기
          </Link>
        </div>
      </div>
    );
  }

  const setBusy = (id: number, value: boolean) => setBusyIds((prev) => ({ ...prev, [id]: value }));

  const changeQuantity = async (item: CartItemData, nextQuantity: number) => {
    if (nextQuantity < 1 || nextQuantity > 99 || busyIds[item.id]) return;
    setBusy(item.id, true);
    try {
      const updated = await updateCartItemQuantity(item.id, nextQuantity, state.accessToken);
      setItems((prev) => prev.map((existing) => (existing.id === item.id ? updated : existing)));
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError ? requestError.message : '수량을 변경하지 못했어요.',
        'err',
      );
    } finally {
      setBusy(item.id, false);
    }
  };

  const removeItem = async (item: CartItemData) => {
    if (busyIds[item.id]) return;
    setBusy(item.id, true);
    try {
      await deleteCartItem(item.id, state.accessToken);
      setItems((prev) => prev.filter((existing) => existing.id !== item.id));
      await refreshCartCount();
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError ? requestError.message : '삭제하지 못했어요.',
        'err',
      );
    } finally {
      setBusy(item.id, false);
    }
  };

  // 체크된 항목(주문 선택용 체크박스를 그대로 재사용)을 한 번에 지운다. 서버가 원자적으로
  // 처리하므로 하나라도 실패하면 전체가 실패하고 아무것도 지워지지 않는다. 여러 개가 한 번에
  // 사라지는 동작이라 단일 삭제(×)와 달리 확인창을 거친다.
  const removeSelected = () => {
    const ids = items.filter((item) => checked[item.id]).map((item) => item.id);
    if (ids.length === 0 || bulkDeleting) return;

    askConfirm({
      icon: 'delete',
      title: `${ids.length}개 상품을 삭제할까요?`,
      body: '선택한 장바구니 상품이 모두 삭제돼요.',
      ok: '삭제',
      danger: true,
      onOk: async () => {
        setBulkDeleting(true);
        try {
          await deleteCartItems(ids, state.accessToken);
          setItems((prev) => prev.filter((existing) => !ids.includes(existing.id)));
          await refreshCartCount();
        } catch (requestError) {
          showToast(
            requestError instanceof ApiError ? requestError.message : '선택한 항목을 삭제하지 못했어요.',
            'err',
          );
        } finally {
          setBulkDeleting(false);
        }
      },
    });
  };

  const selectedItems = items.filter((item) => checked[item.id] && !item.soldOut);
  const checkedCount = items.filter((item) => checked[item.id]).length;
  const total = selectedItems.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0);
  const walletTotal = state.wallet.free + state.wallet.paid;
  const canPurchase = selectedItems.length > 0 && !selectedItems.some((item) => item.stockShortage);

  const goToCheckout = () => {
    if (!canPurchase) return;
    const ids = selectedItems.map((item) => item.id).join(',');
    router.push(`/checkout?ids=${ids}`);
  };

  return (
    <div className="container">
      <h1 className="mb-5 text-[26px] font-extrabold">장바구니</h1>

      {loading ? (
        <div className="rounded-[22px] bg-white py-14 text-center text-sub shadow-card">장바구니를 불러오고 있어요</div>
      ) : error ? (
        <div className="rounded-[22px] bg-white px-5 py-14 text-center text-sub shadow-card">{error}</div>
      ) : items.length === 0 ? (
        <div className="rounded-[22px] bg-white px-5 py-14 text-center text-sub shadow-card">
          <p>장바구니가 비어있어요.</p>
          <Link href="/shop" className="mt-4 inline-block rounded-xl bg-brand px-5 py-2.5 font-bold text-white hover:text-white">
            상점 둘러보기
          </Link>
        </div>
      ) : (
        <div className="grid items-start gap-[22px] [grid-template-columns:repeat(auto-fit,minmax(300px,1fr))]">
          <div className="flex flex-col gap-3">
            <div className="flex h-7 items-center justify-end">
              <button
                type="button"
                disabled={checkedCount === 0 || bulkDeleting}
                onClick={removeSelected}
                className="cursor-pointer text-[13px] font-bold text-sub underline disabled:cursor-not-allowed disabled:no-underline disabled:opacity-40"
              >
                {bulkDeleting ? '삭제 중...' : `선택 삭제 (${checkedCount})`}
              </button>
            </div>
            {items.map((item) => {
              const on = checked[item.id] && !item.soldOut;
              return (
                <div key={item.id} className="flex items-center gap-3.5 rounded-2xl bg-white p-[15px] shadow-[0_2px_10px_rgba(46,54,42,.07)]">
                  <button
                    type="button"
                    onClick={() => !item.soldOut && setChecked((prev) => ({ ...prev, [item.id]: !prev[item.id] }))}
                    className={`flex h-[22px] w-[22px] flex-none cursor-pointer items-center justify-center rounded-[7px] border-2 text-[13px] text-white ${
                      on ? 'border-brand bg-brand' : 'border-[#cfd6c6] bg-white'
                    }`}
                  >
                    {on ? '✓' : ''}
                  </button>
                  <div
                    className="flex h-16 w-16 flex-none items-center justify-center rounded-xl bg-brand-soft bg-cover bg-center text-[32px]"
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
                  <div className="min-w-0 flex-1">
                    <div className="text-[14.5px] font-extrabold">{item.productName}</div>
                    {item.soldOut && (
                      <div className="mt-1 inline-block rounded-full bg-[#fdecec] px-2 py-0.5 text-[11.5px] font-bold text-danger">
                        품절됐어요
                      </div>
                    )}
                    {!item.soldOut && item.stockShortage && (
                      <div className="mt-1 inline-block rounded-full bg-[#fff3d6] px-2 py-0.5 text-[11.5px] font-bold text-[#b5771a]">
                        재고 {item.availableStock}개만 남았어요
                      </div>
                    )}
                    <PointPrice value={item.unitPrice * item.quantity} size="sm" className="mt-1.5" />
                  </div>
                  <div className="flex items-center overflow-hidden rounded-[10px] border-[1.5px] border-line">
                    <button
                      type="button"
                      disabled={busyIds[item.id]}
                      onClick={() => changeQuantity(item, item.quantity - 1)}
                      className="flex h-8 w-8 cursor-pointer items-center justify-center text-[#6d7a68] disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      −
                    </button>
                    <div className="w-[34px] text-center text-sm font-bold">{item.quantity}</div>
                    <button
                      type="button"
                      disabled={busyIds[item.id]}
                      onClick={() => changeQuantity(item, item.quantity + 1)}
                      className="flex h-8 w-8 cursor-pointer items-center justify-center text-[#6d7a68] disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      +
                    </button>
                  </div>
                  <button
                    type="button"
                    disabled={busyIds[item.id]}
                    onClick={() => removeItem(item)}
                    className="cursor-pointer p-1 text-xl text-[#c2c9b8] disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    ×
                  </button>
                </div>
              );
            })}
          </div>

          <div className="flex flex-col gap-3">
            <div className="h-7" />
            <div className="rounded-[18px] bg-white p-[22px] shadow-card">
              <div className="mb-4 font-extrabold">주문 요약</div>
            <div className="flex items-center justify-between py-2 text-[14.5px]">
              <span className="text-sub">선택 상품 합계</span>
              <PointPrice value={total} size="sm" />
            </div>
            <div className="flex justify-between border-b border-[#f2f3ec] py-2 text-[14.5px]">
              <span className="text-sub">보유 포인트</span>
              <span className="font-bold">{fmt(walletTotal)}P</span>
            </div>
            {selectedItems.some((item) => item.stockShortage) && (
              <div className="my-3.5 rounded-[11px] bg-danger-soft px-[13px] py-[11px] text-[13px] font-semibold text-danger">
                재고가 부족한 상품이 있어요. 수량을 줄이거나 선택을 해제해 주세요.
              </div>
            )}
            {total > walletTotal && (
              <div className="my-3.5 rounded-[11px] bg-danger-soft px-[13px] py-[11px] text-[13px] font-semibold text-danger">
                사용 가능한 포인트가 {fmt(total - walletTotal)}P 부족해요.{' '}
                <Link href="/my/points/charge" className="font-extrabold text-danger underline hover:text-danger">충전하러 가기</Link>
              </div>
            )}
            <button
              type="button"
              onClick={goToCheckout}
              disabled={!canPurchase}
              className={`mt-4 w-full rounded-[13px] p-[15px] font-extrabold text-white ${
                canPurchase ? 'cursor-pointer bg-brand' : 'cursor-not-allowed bg-[#b0c894]'
              }`}
            >
              주문하기
            </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
