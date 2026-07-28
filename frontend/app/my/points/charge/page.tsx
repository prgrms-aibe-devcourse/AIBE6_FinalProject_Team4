'use client';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { ApiError } from '@/lib/api';
import { useStore, fmt } from '@/lib/store';
import { useUI } from '@/lib/ui';
import {
  ChargeProduct,
  confirmPayment,
  getChargeProducts,
  PaymentScenario,
  requestCharge,
} from '@/features/payment/api';

interface ChargeSession {
  product: ChargeProduct;
  chargeIdempotencyKey: string;
  confirmIdempotencyKey: string;
  paymentKey: string;
  scenario: PaymentScenario | null;
}

function createIdempotencyKey(operation: string): string {
  return `${operation}-${crypto.randomUUID()}`;
}

export default function Charge() {
  const router = useRouter();
  const { state, refreshWallet } = useStore();
  const { showToast } = useUI();
  const [sheet, setSheet] = useState<ChargeSession | null>(null);
  const [products, setProducts] = useState<ChargeProduct[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const [processing, setProcessing] = useState(false);
  const [paymentError, setPaymentError] = useState('');

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

  const openPaymentSheet = (product: ChargeProduct) => {
    setPaymentError('');
    setSheet({
      product,
      chargeIdempotencyKey: createIdempotencyKey('charge'),
      confirmIdempotencyKey: createIdempotencyKey('confirm'),
      paymentKey: `mock-${crypto.randomUUID()}`,
      scenario: null,
    });
  };

  const closePaymentSheet = () => {
    if (processing) return;
    setPaymentError('');
    setSheet(null);
  };

  const completePayment = async (scenario: PaymentScenario) => {
    if (!sheet || processing || !state.accessToken) return;
    if (sheet.scenario && sheet.scenario !== scenario) {
      setPaymentError('진행 중인 결제 결과가 있어 같은 버튼으로 다시 시도해 주세요.');
      return;
    }

    const session = { ...sheet, scenario };
    setSheet(session);
    setProcessing(true);
    setPaymentError('');

    try {
      const pendingPayment = await requestCharge(
        state.accessToken,
        session.product.id,
        session.chargeIdempotencyKey,
      );
      const result = await confirmPayment(
        state.accessToken,
        {
          providerOrderId: pendingPayment.providerOrderId,
          paymentKey: session.paymentKey,
          amount: pendingPayment.cashAmount,
          scenario,
        },
        session.confirmIdempotencyKey,
      );

      setSheet(null);
      if (result.status === 'PAID') {
        await refreshWallet();
        showToast(result.message || '충전이 완료됐어요! ☀️');
        router.push('/my/points');
        return;
      }

      showToast(
        result.message || (result.status === 'CANCELED' ? '결제를 취소했어요.' : '결제에 실패했어요.'),
        'err',
      );
    } catch (requestError) {
      setPaymentError(
        requestError instanceof ApiError
          ? requestError.message
          : '결제 처리 중 문제가 발생했어요. 같은 버튼으로 다시 시도해 주세요.',
      );
    } finally {
      setProcessing(false);
    }
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
              onClick={() => openPaymentSheet(product)}
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
        <div onClick={closePaymentSheet} className="fixed inset-0 z-[60] flex items-end justify-center bg-[rgba(46,54,42,.45)]">
          <div onClick={(e) => e.stopPropagation()} className="w-full max-w-[440px] animate-pop rounded-t-[22px] bg-white p-[26px]">
            <div className="mx-auto mb-[18px] h-[5px] w-11 rounded-full bg-line" />
            <div className="mb-2 text-center text-[13px] font-extrabold text-sub">MOCK 결제 (테스트)</div>
            <div className="mb-1 text-center text-[26px] font-extrabold">{fmt(sheet.product.price)}원</div>
            <div className="mb-[22px] text-center text-sub">{fmt(sheet.product.pointAmount)}P 충전</div>
            {paymentError && (
              <p className="mb-3 rounded-xl bg-[#fff4ef] px-3 py-2.5 text-center text-sm font-bold text-danger">
                {paymentError}
              </p>
            )}
            <div className="flex flex-col gap-2.5">
              <button
                type="button"
                disabled={processing || (sheet.scenario !== null && sheet.scenario !== 'SUCCESS')}
                onClick={() => void completePayment('SUCCESS')}
                className="cursor-pointer rounded-[13px] bg-brand p-3.5 font-extrabold text-white disabled:cursor-not-allowed disabled:opacity-50"
              >
                <span className="material-symbols-outlined text-lg">check_circle</span> 결제 성공 시뮬레이션
              </button>
              <button
                type="button"
                disabled={processing || (sheet.scenario !== null && sheet.scenario !== 'FAILURE')}
                onClick={() => void completePayment('FAILURE')}
                className="cursor-pointer rounded-[13px] border-[1.5px] border-[#e8bdad] bg-white p-3.5 font-bold text-danger disabled:cursor-not-allowed disabled:opacity-50"
              >
                <span className="material-symbols-outlined text-lg">cancel</span> 결제 실패
              </button>
              <button
                type="button"
                disabled={processing || (sheet.scenario !== null && sheet.scenario !== 'CANCEL')}
                onClick={() => void completePayment('CANCEL')}
                className="cursor-pointer rounded-[13px] border-[1.5px] border-line bg-white p-3.5 font-bold text-sub disabled:cursor-not-allowed disabled:opacity-50"
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
