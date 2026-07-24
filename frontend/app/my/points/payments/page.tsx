'use client';
import { useState } from 'react';
import Link from 'next/link';
import { useStore, fmt } from '@/lib/store';
import { useUI } from '@/lib/ui';

interface Payment {
  id: number;
  name: string;
  amount: number;
  point: number;
  date: string;
  status: string;
}

const INITIAL: Payment[] = [
  { id: 1, name: '10,000P 충전', amount: 10000, point: 10000, date: '2026.07.17', status: 'PAID' },
  { id: 2, name: '5,000P 충전', amount: 5000, point: 5000, date: '2026.07.10', status: 'PAID' },
  { id: 3, name: '1,000P 충전', amount: 1000, point: 1000, date: '2026.07.02', status: 'REFUNDED' },
];
const STAT: Record<string, string> = { PAID: '결제완료', REFUNDED: '환불완료', FAILED: '실패', CANCELED: '취소' };

export default function Payments() {
  const { set } = useStore();
  const { showToast, askConfirm } = useUI();
  const [payments, setPayments] = useState<Payment[]>(INITIAL);

  const refund = (p: Payment) => askConfirm({ icon: 'payments', title: '환불을 요청할까요?', ok: '환불 요청', danger: true,
    body: `${fmt(p.amount)}원을 환불해요. 이미 사용한 포인트만큼은 환불이 어려워요. 환불 가능: ${fmt(p.amount)}원`,
    onOk: () => { setPayments(payments.map((x) => x.id === p.id ? { ...x, status: 'REFUNDED' } : x)); set((s) => ({ wallet: { ...s.wallet, paid: Math.max(0, s.wallet.paid - 1000) } })); showToast('환불이 완료됐어요.'); } });

  return (
    <div className="container max-w-[900px]">
      <Link href="/my/points" className="text-sm font-semibold text-sub">← 포인트</Link>
      <h1 className="mb-5 mt-3.5 text-2xl font-extrabold">결제 내역</h1>
      <div className="flex flex-col gap-3">
        {payments.map((p) => (
          <div key={p.id} className="flex flex-wrap items-center gap-3.5 rounded-2xl bg-white px-5 py-[18px] shadow-card">
            <div className="min-w-[160px] flex-1">
              <div className="font-extrabold">{p.name}</div>
              <div className="mt-[3px] text-[13px] text-sub">{p.date} · {fmt(p.amount)}원 · {fmt(p.point)}P</div>
            </div>
            <span className={`rounded-full px-3 py-[5px] text-[12.5px] font-extrabold ${
              p.status === 'PAID' ? 'bg-[#E8F3D8] text-brand-text' : 'bg-[#f0f1ea] text-[#8a8a8a]'
            }`}>
              {STAT[p.status]}
            </span>
            {p.status === 'PAID' && (
              <button type="button" onClick={() => refund(p)} className="cursor-pointer rounded-[11px] border-[1.5px] border-[#e8bdad] bg-white px-4 py-[9px] font-bold text-[#b5502f]">
                환불 요청
              </button>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
