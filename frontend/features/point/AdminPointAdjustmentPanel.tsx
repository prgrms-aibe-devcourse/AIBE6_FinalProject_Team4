'use client';

import { FormEvent, useRef, useState } from 'react';
import {
  AdminPointAdjustmentData,
  AdminPointAdjustmentInput,
  adjustPointByAdmin,
  PointCurrencyType,
} from '@/features/point/api';
import { ApiError } from '@/lib/api';
import { useUI } from '@/lib/ui';

type AdjustmentMode = 'GRANT' | 'DEDUCT';

interface PendingAttempt {
  signature: string;
  idempotencyKey: string;
}

interface AdminPointAdjustmentPanelProps {
  accessToken: string | null;
}

function createIdempotencyKey(): string {
  return globalThis.crypto?.randomUUID?.()
    ?? `point-adjust-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function formatPoint(value: number): string {
  return value.toLocaleString('ko-KR');
}

export default function AdminPointAdjustmentPanel({
  accessToken,
}: AdminPointAdjustmentPanelProps) {
  const { askConfirm, showToast } = useUI();
  const [userId, setUserId] = useState('');
  const [currencyType, setCurrencyType] = useState<PointCurrencyType>('FREE');
  const [mode, setMode] = useState<AdjustmentMode>('GRANT');
  const [amount, setAmount] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [result, setResult] = useState<AdminPointAdjustmentData | null>(null);
  const pendingAttempt = useRef<PendingAttempt | null>(null);

  const executeAdjustment = async (payload: AdminPointAdjustmentInput) => {
    if (!accessToken) {
      setErrorMessage('관리자 로그인이 필요합니다.');
      return;
    }

    const signature = JSON.stringify(payload);
    const idempotencyKey = pendingAttempt.current?.signature === signature
      ? pendingAttempt.current.idempotencyKey
      : createIdempotencyKey();
    pendingAttempt.current = { signature, idempotencyKey };

    setSubmitting(true);
    setErrorMessage('');
    try {
      const adjusted = await adjustPointByAdmin(accessToken, payload, idempotencyKey);
      pendingAttempt.current = null;
      setResult(adjusted);
      setAmount('');
      showToast(
        `${adjusted.userId}번 회원의 ${adjusted.currencyType === 'FREE' ? '무상' : '유상'} 포인트를 조정했어요.`,
      );
    } catch (requestError) {
      const message = requestError instanceof ApiError
        ? requestError.message
        : '포인트 조정에 실패했어요. 같은 내용으로 다시 시도해 주세요.';
      setErrorMessage(message);
      showToast(message, 'err');
    } finally {
      setSubmitting(false);
    }
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setErrorMessage('');
    setResult(null);

    const parsedUserId = Number(userId);
    const parsedAmount = Number(amount);
    if (!Number.isSafeInteger(parsedUserId) || parsedUserId < 1) {
      setErrorMessage('대상 회원 ID를 1 이상의 정수로 입력해 주세요.');
      return;
    }
    if (!Number.isSafeInteger(parsedAmount) || parsedAmount < 1) {
      setErrorMessage('조정 포인트를 1 이상의 정수로 입력해 주세요.');
      return;
    }

    const payload: AdminPointAdjustmentInput = {
      userId: parsedUserId,
      currencyType,
      amount: mode === 'DEDUCT' ? -parsedAmount : parsedAmount,
    };

    if (mode === 'DEDUCT') {
      askConfirm({
        icon: 'remove_circle',
        title: '포인트를 차감할까요?',
        body: `${parsedUserId}번 회원의 ${currencyType === 'FREE' ? '무상' : '유상'} 포인트 ${formatPoint(parsedAmount)}P를 차감합니다. 잔액보다 많이 차감할 수 없습니다.`,
        ok: '차감하기',
        danger: true,
        onOk: () => void executeAdjustment(payload),
      });
      return;
    }

    void executeAdjustment(payload);
  };

  return (
    <div className="grid gap-4 lg:grid-cols-[minmax(0,1.1fr)_minmax(280px,.9fr)]">
      <form onSubmit={handleSubmit} className="rounded-[18px] bg-white p-6 shadow-card">
        <div className="mb-5">
          <h2 className="text-lg font-extrabold">회원 포인트 조정</h2>
          <p className="mt-1 text-sm leading-6 text-sub">
            지급과 차감은 모두 원장에 기록됩니다. 유상·무상 포인트 잔액은 음수가 될 수 없습니다.
          </p>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <label htmlFor="admin-point-user-id" className="text-sm font-bold text-sub">
            대상 회원 ID
            <input
              id="admin-point-user-id"
              type="number"
              min="1"
              step="1"
              inputMode="numeric"
              value={userId}
              onChange={(event) => setUserId(event.target.value)}
              placeholder="예: 10"
              className="mt-2 w-full rounded-xl border border-line bg-white px-3.5 py-3 text-ink outline-none focus:border-brand"
              disabled={submitting}
              required
            />
          </label>

          <label htmlFor="admin-point-currency" className="text-sm font-bold text-sub">
            포인트 종류
            <select
              id="admin-point-currency"
              value={currencyType}
              onChange={(event) => setCurrencyType(event.target.value as PointCurrencyType)}
              className="mt-2 w-full rounded-xl border border-line bg-white px-3.5 py-3 text-ink outline-none focus:border-brand"
              disabled={submitting}
            >
              <option value="FREE">무상 포인트</option>
              <option value="PAID">유상 포인트</option>
            </select>
          </label>

          <label htmlFor="admin-point-mode" className="text-sm font-bold text-sub">
            조정 방식
            <select
              id="admin-point-mode"
              value={mode}
              onChange={(event) => setMode(event.target.value as AdjustmentMode)}
              className="mt-2 w-full rounded-xl border border-line bg-white px-3.5 py-3 text-ink outline-none focus:border-brand"
              disabled={submitting}
            >
              <option value="GRANT">지급</option>
              <option value="DEDUCT">차감</option>
            </select>
          </label>

          <label htmlFor="admin-point-amount" className="text-sm font-bold text-sub">
            조정 포인트
            <div className="relative mt-2">
              <input
                id="admin-point-amount"
                type="number"
                min="1"
                step="1"
                inputMode="numeric"
                value={amount}
                onChange={(event) => setAmount(event.target.value)}
                placeholder="예: 1000"
                className="w-full rounded-xl border border-line bg-white px-3.5 py-3 pr-10 text-ink outline-none focus:border-brand"
                disabled={submitting}
                required
              />
              <span className="absolute right-3.5 top-1/2 -translate-y-1/2 text-sm font-bold text-sub">P</span>
            </div>
          </label>
        </div>

        {errorMessage && (
          <p role="alert" className="mt-4 rounded-xl bg-danger-soft px-4 py-3 text-sm font-bold text-danger">
            {errorMessage}
          </p>
        )}

        <button
          type="submit"
          disabled={submitting || !accessToken}
          className={`mt-5 w-full rounded-xl px-5 py-3.5 font-extrabold text-white transition-colors ${
            submitting || !accessToken
              ? 'cursor-not-allowed bg-[#b9c1b4]'
              : mode === 'DEDUCT'
                ? 'cursor-pointer bg-danger hover:bg-[#a64729]'
                : 'cursor-pointer bg-brand hover:bg-brand-dark'
          }`}
        >
          {submitting ? '처리 중...' : mode === 'DEDUCT' ? '포인트 차감' : '포인트 지급'}
        </button>
      </form>

      <section className="rounded-[18px] bg-white p-6 shadow-card" aria-live="polite">
        <h2 className="text-lg font-extrabold">최근 조정 결과</h2>
        {result ? (
          <div className="mt-5 space-y-3 text-sm">
            <div className="flex justify-between gap-3 border-b border-line pb-3">
              <span className="text-sub">회원 ID</span>
              <strong>{result.userId}</strong>
            </div>
            <div className="flex justify-between gap-3 border-b border-line pb-3">
              <span className="text-sub">조정 내용</span>
              <strong className={result.amount < 0 ? 'text-danger' : 'text-brand-text'}>
                {result.currencyType === 'FREE' ? '무상' : '유상'} {result.amount > 0 ? '+' : ''}
                {formatPoint(result.amount)}P
              </strong>
            </div>
            <div className="flex justify-between gap-3 border-b border-line pb-3">
              <span className="text-sub">유상 잔액</span>
              <strong>{formatPoint(result.paidPoint)}P</strong>
            </div>
            <div className="flex justify-between gap-3 border-b border-line pb-3">
              <span className="text-sub">무상 잔액</span>
              <strong>{formatPoint(result.freePoint)}P</strong>
            </div>
            <div className="flex justify-between gap-3">
              <span className="text-sub">총 잔액</span>
              <strong className="text-base text-brand-text">{formatPoint(result.balance)}P</strong>
            </div>
            <p className="pt-2 text-xs text-sub">원장 번호 #{result.transactionId}</p>
          </div>
        ) : (
          <div className="mt-5 rounded-xl bg-[#f6f7f1] px-4 py-8 text-center text-sm leading-6 text-sub">
            포인트를 조정하면 처리 결과와<br />회원의 최신 잔액이 표시됩니다.
          </div>
        )}
      </section>
    </div>
  );
}
