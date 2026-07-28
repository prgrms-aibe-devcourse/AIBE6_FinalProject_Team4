'use client';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { ApiError } from '@/lib/api';
import { useStore, fmt } from '@/lib/store';
import { useUI } from '@/lib/ui';
import { ChargeProduct, getChargeProducts } from '@/features/payment/api';

export default function Charge() {
  const router = useRouter();
  const { state, creditPaid } = useStore();
  const { showToast } = useUI();
  const [sheet, setSheet] = useState<ChargeProduct | null>(null);
  const [products, setProducts] = useState<ChargeProduct[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    if (!state.accessToken) return;

    const controller = new AbortController();
    setLoading(true);
    setError('');

    getChargeProducts(state.accessToken, controller.signal)
      .then(setProducts)
      .catch((requestError) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return;
        setProducts([]);
        setError(
          requestError instanceof ApiError
            ? requestError.message
            : '충전 상품을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.',
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [reloadKey, state.accessToken]);

  const success = () => {
    if (!sheet) return;
    creditPaid(sheet.pointAmount);
    setSheet(null);
    showToast('충전이 완료됐어요! ☀️');
    router.push('/my/points');
  };

  return (
    <div className="container max-w-[900px]">
      <Link href="/my/points" className="text-sm font-semibold text-sub">← 포인트</Link>
      <h1 className="mb-2 mt-3.5 text-2xl font-extrabold">포인트 충전</h1>
      <div className="mb-[22px] rounded-[13px] bg-gold-soft px-4 py-[13px] text-sm font-bold text-gold-text">
        <span className="material-symbols-outlined text-base">light_mode</span> 테스트 모드예요. 실제 결제가 발생하지 않아요.
      </div>

      {loading ? (
        <div className="rounded-[18px] bg-white py-14 text-center text-sm text-sub shadow-card">
          충전 상품을 불러오고 있어요.
        </div>
      ) : error ? (
        <div className="rounded-[18px] bg-white px-5 py-14 text-center text-sm text-sub shadow-card">
          <p>{error}</p>
          <button
            type="button"
            onClick={() => setReloadKey((current) => current + 1)}
            className="mt-4 cursor-pointer rounded-xl bg-brand px-5 py-2.5 font-bold text-white"
          >
            다시 시도
          </button>
        </div>
      ) : products.length === 0 ? (
        <div className="rounded-[18px] bg-white py-14 text-center text-sm text-sub shadow-card">
          지금은 구매할 수 있는 충전 상품이 없어요.
        </div>
      ) : (
        <div className="grid gap-4 [grid-template-columns:repeat(auto-fill,minmax(200px,1fr))]">
          {products.map((product) => (
            <button
              key={product.id}
              type="button"
              onClick={() => setSheet(product)}
              className="cursor-pointer rounded-[18px] border-2 border-transparent bg-white px-5 py-6 text-center shadow-card"
            >
              <div>
                <span className="material-symbols-outlined text-[34px] text-gold-text">
                  monetization_on
                </span>
              </div>
              <div className="mb-0.5 mt-2 text-[22px] font-extrabold">
                {fmt(product.pointAmount)}P
              </div>
              <div className="font-bold text-sub">{fmt(product.price)}원</div>
              <div className="mt-1 text-xs text-faint">{product.name}</div>
            </button>
          ))}
        </div>
      )}

      {sheet && (
        <div onClick={() => setSheet(null)} className="fixed inset-0 z-[60] flex items-end justify-center bg-[rgba(46,54,42,.45)]">
          <div onClick={(e) => e.stopPropagation()} className="w-full max-w-[440px] animate-pop rounded-t-[22px] bg-white p-[26px]">
            <div className="mx-auto mb-[18px] h-[5px] w-11 rounded-full bg-line" />
            <div className="mb-2 text-center text-[13px] font-extrabold text-sub">MOCK 결제 (테스트)</div>
            <div className="mb-1 text-center text-[26px] font-extrabold">{fmt(sheet.price)}원</div>
            <div className="mb-[22px] text-center text-sub">{fmt(sheet.pointAmount)}P 충전</div>
            <div className="flex flex-col gap-2.5">
              <button type="button" onClick={success} className="cursor-pointer rounded-[13px] bg-brand p-3.5 font-extrabold text-white">
                <span className="material-symbols-outlined text-lg">check_circle</span> 결제 성공 시뮬레이션
              </button>
              <button
                type="button"
                onClick={() => { setSheet(null); showToast('결제에 실패했어요. 잠시 후 다시 시도해 주세요.', 'err'); }}
                className="cursor-pointer rounded-[13px] border-[1.5px] border-[#e8bdad] bg-white p-3.5 font-bold text-danger"
              >
                <span className="material-symbols-outlined text-lg">cancel</span> 결제 실패
              </button>
              <button
                type="button"
                onClick={() => { setSheet(null); showToast('결제를 취소했어요. 포인트 변동은 없어요.', 'err'); }}
                className="cursor-pointer rounded-[13px] border-[1.5px] border-line bg-white p-3.5 font-bold text-sub"
              >
                결제 취소
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
