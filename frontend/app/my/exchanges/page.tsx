'use client';
import { useState } from 'react';
import { useStore } from '@/lib/store';
import { useUI } from '@/lib/ui';

const STEPS: [string, string][] = [['REQUESTED', '신청됨'], ['PREPARING', '준비중'], ['SHIPPING', '배송중'], ['DELIVERED', '배송완료']];

interface Exchange {
  id: number;
  realName: string;
  realEmoji: string;
  realGrad: string;
  cardName: string;
  usedCount: number;
  date: string;
  status: string;
  cancelBy?: string;
  cancelReason?: string;
}

const INITIAL: Exchange[] = [
  { id: 1, realName: '진짜 수박', realEmoji: '🍉', realGrad: 'linear-gradient(135deg,#C8E6C9,#81C784)', cardName: '수박 카드', usedCount: 5, date: '2026.07.19', status: 'SHIPPING' },
  { id: 2, realName: '방울토마토 1kg', realEmoji: '🍅', realGrad: 'linear-gradient(135deg,#FFE0B2,#FFAB91)', cardName: '토마토 카드', usedCount: 4, date: '2026.07.10', status: 'DELIVERED' },
  { id: 3, realName: '딸기 한 팩', realEmoji: '🍓', realGrad: 'linear-gradient(135deg,#FCE4EC,#F48FB1)', cardName: '딸기 카드', usedCount: 6, date: '2026.07.05', status: 'CANCELLED', cancelBy: 'ADMIN', cancelReason: '실물 재고 소진으로 부득이하게 취소되었어요.' },
];

export default function MyExchanges() {
  const { set } = useStore();
  const { showToast, askConfirm } = useUI();
  const [exchanges, setExchanges] = useState<Exchange[]>(INITIAL);

  const cancel = (id: number) => askConfirm({ icon: 'undo', title: '교환을 취소할까요?', ok: '취소하기', danger: true, body: '카드와 수량이 다시 돌아와요. 취소할까요?',
    onOk: () => { setExchanges(exchanges.map((e) => e.id === id ? { ...e, status: 'CANCELLED', cancelBy: 'USER' } : e)); set((s) => ({ readyCards: s.readyCards + 1 })); showToast('교환을 취소했어요. 카드와 수량이 다시 돌아왔어요.'); } });

  return (
    <div className="container">
      <h1 className="mb-5 text-[26px] font-extrabold">교환 내역</h1>
      <div className="flex flex-col gap-4">
        {exchanges.map((x) => {
          const cancelled = x.status === 'CANCELLED';
          const idx = STEPS.findIndex((s) => s[0] === x.status);
          return (
            <div key={x.id} className="rounded-[18px] bg-white p-5 shadow-card">
              <div className="flex flex-wrap items-center gap-3.5">
                <div className="flex h-14 w-14 items-center justify-center rounded-[13px] text-[28px]" style={{ background: x.realGrad }}>{x.realEmoji}</div>
                <div className="min-w-[160px] flex-1">
                  <div className="font-extrabold">{x.realName}</div>
                  <div className="text-[13px] text-sub">{x.cardName} {x.usedCount}장 사용 · {x.date}</div>
                </div>
                {x.status === 'REQUESTED' && (
                  <button type="button" onClick={() => cancel(x.id)} className="cursor-pointer rounded-[11px] border-[1.5px] border-[#e8bdad] bg-white px-4 py-[9px] font-bold text-[#b5502f]">
                    취소하기
                  </button>
                )}
              </div>

              {cancelled ? (
                <div className="mt-3.5 rounded-xl bg-[#f5f2ee] px-3.5 py-3 text-[13.5px] font-semibold text-[#8a7d6f]">
                  취소됨 {x.cancelBy === 'ADMIN' ? `· 관리자 취소: ${x.cancelReason}` : '· 직접 취소하셨어요'}
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
    </div>
  );
}
