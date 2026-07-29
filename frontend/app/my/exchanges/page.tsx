'use client';
import { useEffect, useState } from 'react';
import { ApiError } from '@/lib/api';
import { cancelExchange, ExchangeOrderData, ExchangeStatus, getMyExchanges } from '@/lib/exchange-api';
import { useStore } from '@/lib/store';
import { useUI } from '@/lib/ui';

const STEPS: [ExchangeStatus, string][] = [['REQUESTED', '신청됨'], ['PREPARING', '준비중'], ['SHIPPING', '배송중'], ['DELIVERED', '배송완료']];

function formatDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' }).format(date);
}

export default function MyExchanges() {
  const { state, hydrated } = useStore();
  const { showToast, askConfirm } = useUI();
  const [exchanges, setExchanges] = useState<ExchangeOrderData[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!hydrated || !state.accessToken) return;
    const accessToken = state.accessToken;

    const controller = new AbortController();
    setLoading(true);
    setError('');

    getMyExchanges(accessToken, 0, 50, controller.signal)
      .then((page) => setExchanges(page.content))
      .catch((requestError) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return;
        setExchanges([]);
        setError(
          requestError instanceof ApiError
            ? requestError.message
            : '교환 내역을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.',
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [hydrated, state.accessToken]);

  const cancel = (id: number) => askConfirm({
    icon: 'undo', title: '교환을 취소할까요?', ok: '취소하기', danger: true,
    body: '카드와 수량이 다시 돌아와요. 취소할까요?',
    onOk: async () => {
      if (!state.accessToken) return;
      try {
        await cancelExchange(id, '단순 변심', state.accessToken);
        setExchanges((prev) => prev.map((x) => (x.id === id ? { ...x, status: 'CANCELLED', cancelledBy: 'USER' } : x)));
        showToast('교환을 취소했어요. 카드와 수량이 다시 돌아왔어요.');
      } catch (requestError) {
        showToast(
          requestError instanceof ApiError ? requestError.message : '취소에 실패했어요. 잠시 후 다시 시도해 주세요.',
          'err',
        );
      }
    },
  });

  return (
    <div className="container">
      <h1 className="mb-5 text-[26px] font-extrabold">교환 내역</h1>

      {loading ? (
        <div className="rounded-[22px] bg-white py-14 text-center text-[15px] text-sub">교환 내역을 불러오고 있어요 🍉</div>
      ) : error ? (
        <div className="rounded-[22px] bg-white px-5 py-14 text-center text-[15px] text-sub">{error}</div>
      ) : exchanges.length === 0 ? (
        <div className="rounded-[22px] bg-white py-14 text-center text-[15px] text-sub">아직 신청한 교환이 없어요.</div>
      ) : (
        <div className="flex flex-col gap-4">
          {exchanges.map((x) => {
            const cancelled = x.status === 'CANCELLED';
            const idx = STEPS.findIndex((s) => s[0] === x.status);
            return (
              <div key={x.id} className="rounded-[18px] bg-white p-5 shadow-card">
                <div className="flex flex-wrap items-center gap-3.5">
                  <div className="flex h-14 w-14 items-center justify-center rounded-[13px] bg-brand-soft text-[28px]">🎁</div>
                  <div className="min-w-[160px] flex-1">
                    <div className="font-extrabold">{x.exchangeProductName}</div>
                    <div className="text-[13px] text-sub">{x.cardName} {x.usedCardCount}장 사용 · {formatDate(x.requestedAt)}</div>
                  </div>
                  {x.status === 'REQUESTED' && (
                    <button type="button" onClick={() => cancel(x.id)} className="cursor-pointer rounded-[11px] border-[1.5px] border-[#e8bdad] bg-white px-4 py-[9px] font-bold text-[#b5502f]">
                      취소하기
                    </button>
                  )}
                </div>

                {cancelled ? (
                  <div className="mt-3.5 rounded-xl bg-[#f5f2ee] px-3.5 py-3 text-[13.5px] font-semibold text-[#8a7d6f]">
                    취소됨 {x.cancelledBy === 'ADMIN' ? `· 관리자 취소: ${x.cancelReason}` : '· 직접 취소하셨어요'}
                  </div>
                ) : (
                  <div className="mt-4 flex items-center">
                    {STEPS.map(([k, label], i) => (
                      <div key={k} className="flex flex-1 items-center">
                        <div className="flex flex-col items-center gap-[5px]">
                          <div className={`flex h-[26px] w-[26px] items-center justify-center rounded-full text-[13px] font-extrabold ${
                            i <= idx ? 'bg-brand text-white' : 'bg-[#eef0e6] text-faint'
                          }`}>
                            {i <= idx ? '✓' : i + 1}
                          </div>
                          <span className={`whitespace-nowrap text-[11px] font-bold ${i <= idx ? 'text-brand-dark' : 'text-faint'}`}>{label}</span>
                        </div>
                        {i < STEPS.length - 1 && (
                          <div className={`mx-1 mb-[18px] h-0.5 flex-1 ${i < idx ? 'bg-brand' : 'bg-[#eef0e6]'}`} />
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
