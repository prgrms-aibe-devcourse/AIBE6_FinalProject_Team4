'use client';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { ApiError } from '@/lib/api';
import { fmt, useStore } from '@/lib/store';
import {
  getPaymentHistory,
  PaymentHistory,
  PaymentRefundStatus,
  PaymentStatus,
} from '@/features/payment/api';

const PAYMENT_STATUS_LABEL: Record<PaymentStatus, string> = {
  PENDING: '결제 진행 중',
  PAID: '결제완료',
  FAILED: '실패',
  CANCELED: '취소',
  REFUNDED: '환불완료',
  PARTIAL_REFUNDED: '부분 환불',
};

const REFUND_STATUS_LABEL: Record<PaymentRefundStatus, string> = {
  REQUESTED: '환불 요청',
  COMPLETED: '환불완료',
  FAILED: '환불 실패',
};

function formatDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date);
}

function paymentStatusClass(status: PaymentStatus): string {
  if (status === 'PAID') return 'bg-[#E8F3D8] text-brand-text';
  if (status === 'REFUNDED' || status === 'PARTIAL_REFUNDED') return 'bg-[#fff1eb] text-danger';
  return 'bg-[#f0f1ea] text-[#8a8a8a]';
}

export default function Payments() {
  const { state } = useStore();
  const [history, setHistory] = useState<PaymentHistory[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    if (!state.accessToken) return;

    const controller = new AbortController();
    setLoading(true);
    setError('');

    getPaymentHistory(state.accessToken, controller.signal)
      .then(setHistory)
      .catch((requestError) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return;
        setHistory([]);
        setError(
          requestError instanceof ApiError
            ? requestError.message
            : '결제 내역을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.',
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [reloadKey, state.accessToken]);

  return (
    <div className="container max-w-[900px]">
      <Link href="/my/points" className="text-sm font-semibold text-sub">← 포인트</Link>
      <h1 className="mb-5 mt-3.5 text-2xl font-extrabold">결제 내역</h1>
      <div className="mb-[22px] rounded-[13px] bg-gold-soft px-4 py-[13px] text-sm font-bold text-gold-text">
        <span className="material-symbols-outlined text-base">light_mode</span> 테스트 모드 결제 내역이에요.
      </div>

      {loading ? (
        <div className="rounded-2xl bg-white py-14 text-center text-sm text-sub shadow-card">
          결제 내역을 불러오고 있어요.
        </div>
      ) : error ? (
        <div className="rounded-2xl bg-white px-5 py-14 text-center text-sm text-sub shadow-card">
          <p>{error}</p>
          <button
            type="button"
            onClick={() => setReloadKey((current) => current + 1)}
            className="mt-4 cursor-pointer rounded-xl bg-brand px-5 py-2.5 font-bold text-white"
          >
            다시 시도
          </button>
        </div>
      ) : history.length === 0 ? (
        <div className="rounded-2xl bg-white py-14 text-center text-sm text-sub shadow-card">
          아직 결제 내역이 없어요.
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          {history.map(({ payment, refunds }) => (
            <div key={payment.id} className="rounded-2xl bg-white px-5 py-[18px] shadow-card">
              <div className="flex flex-wrap items-center gap-3.5">
                <div className="min-w-[160px] flex-1">
                  <div className="font-extrabold">{payment.chargeProductName}</div>
                  <div className="mt-[3px] text-[13px] text-sub">
                    {formatDate(payment.createdAt)} · {fmt(payment.cashAmount)}원 · {fmt(payment.pointAmount)}P
                  </div>
                </div>
                <span className={`rounded-full px-3 py-[5px] text-[12.5px] font-extrabold ${paymentStatusClass(payment.status)}`}>
                  {PAYMENT_STATUS_LABEL[payment.status]}
                </span>
              </div>

              {refunds.length > 0 && (
                <div className="mt-3 border-t border-[#f4f5ee] pt-3">
                  <div className="mb-1 text-xs font-extrabold text-sub">환불 이력</div>
                  {refunds.map((refund) => (
                    <div key={refund.id} className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 text-[13px] text-sub">
                      <span>
                        {formatDate(refund.createdAt)} · {fmt(refund.cashAmount)}원 · {fmt(refund.pointAmount)}P
                      </span>
                      <span className="font-bold">{REFUND_STATUS_LABEL[refund.status]}</span>
                    </div>
                  ))}
                </div>
              )}

              {payment.status === 'PAID' && (
                <p className="mt-3 border-t border-[#f4f5ee] pt-3 text-xs text-faint">
                  환불 요청 기능은 준비 중이에요.
                </p>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
