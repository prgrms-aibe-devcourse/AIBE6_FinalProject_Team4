'use client';
import { useState } from 'react';
import Link from 'next/link';
import { useStore } from '@/lib/store';
import { useUI } from '@/lib/ui';
import { PLANTS, JOURNALS, BADGE } from '@/lib/data';

export default function PlantDetail({ params }: { params: { id: string } }) {
  const { set } = useStore();
  const { showToast, askConfirm } = useUI();
  const id = Number(params.id);
  const [plant, setPlant] = useState(PLANTS.find((p) => p.id === id) || PLANTS[0]);
  const [careOpen, setCareOpen] = useState(true);
  const [statusOpen, setStatusOpen] = useState(false);
  const [editNick, setEditNick] = useState(plant.nickname);
  const [editStatus, setEditStatus] = useState<string>(plant.status);

  const b = (BADGE as Record<string, { label: string; bg: string; color: string }>)[plant.status];
  const editable = !plant.archived;
  const journals = JOURNALS.filter((j) => j.plantId === plant.id);

  const archive = () => askConfirm({
    icon: 'inventory_2', title: '정말 보관할까요?', ok: '보관하기', danger: true,
    body: '보관하면 목록에서 사라지고 되돌릴 수 없어요. 일지는 소중히 보관돼요.',
    onOk: () => {
      setPlant({ ...plant, archived: true });
      set((s) => ({ plantCount: Math.max(0, s.plantCount - 1), growingCount: plant.status === 'GROWING' ? Math.max(0, s.growingCount - 1) : s.growingCount }));
      showToast('식물을 소중히 보관했어요. 일지는 그대로 남아있어요.');
    },
  });

  const saveStatus = () => {
    setPlant({ ...plant, nickname: editNick || plant.nickname, status: editStatus });
    setStatusOpen(false);
    showToast('식물 정보를 수정했어요 🌿');
  };

  return (
    <div className="container">
      <Link href="/plants" className="text-sm font-semibold text-sub">← 내 식물</Link>

      <div className="mt-4 grid items-start gap-[26px] [grid-template-columns:repeat(auto-fit,minmax(280px,1fr))]">
        <div className="flex h-[300px] items-center justify-center overflow-hidden rounded-[22px] text-[140px]" style={{ background: plant.grad }}>{plant.emoji}</div>
        <div>
          <div className="mb-2.5 inline-block rounded-full px-3 py-[5px] text-xs font-extrabold" style={{ background: b.bg, color: b.color }}>{b.label}</div>
          <h1 className="mb-1.5 text-[28px] font-extrabold">{plant.nickname}</h1>
          <div className="flex items-center gap-1.5 text-[15px] text-sub">
            {plant.species}
            <span className="text-xs text-[#c2c9b8]"><span className="material-symbols-outlined text-sm">lock</span> 종은 변경할 수 없어요</span>
          </div>
          <div className="mt-2 text-sm text-faint">
            <span className="material-symbols-outlined text-[15px]">calendar_month</span> {plant.startDate} 시작 · D+{plant.dplus}
          </div>

          {!editable && (
            <div className="mt-4 rounded-xl bg-[#f5f2ee] px-[15px] py-3 text-[13.5px] font-semibold text-[#8a7d6f]">
              <span className="material-symbols-outlined text-base">inventory_2</span> 보관된 식물은 수정할 수 없어요.
            </div>
          )}

          {editable && (
            <div className="mt-5 flex flex-wrap gap-2.5">
              <button
                type="button"
                onClick={() => { setEditNick(plant.nickname); setEditStatus(plant.status); setStatusOpen(true); }}
                className="cursor-pointer rounded-[11px] bg-brand-soft px-[18px] py-[11px] font-bold text-brand-dark"
              >
                <span className="material-symbols-outlined text-[17px]">edit</span> 상태·정보 수정
              </button>
              <Link href={`/journals/new?plant=${plant.id}`} className="rounded-[11px] bg-brand px-[18px] py-[11px] font-bold text-white hover:text-white">
                + 오늘의 일지 쓰기
              </Link>
            </div>
          )}
        </div>
      </div>

      <button type="button" onClick={() => setCareOpen(!careOpen)} className="mt-[26px] w-full cursor-pointer rounded-2xl bg-white px-5 py-[18px] text-left shadow-card">
        <div className="flex items-center justify-between text-base font-extrabold">
          이 아이를 돌보는 법 <span className="material-symbols-outlined text-[18px] text-brand">favorite</span>
          <span className="text-faint">{careOpen ? '▲' : '▼'}</span>
        </div>
        {careOpen && <p className="mt-3.5 whitespace-pre-line text-[14.5px] leading-[1.75] text-[#6d7a68]">{plant.careGuide}</p>}
      </button>

      <div className="mt-7 flex items-center justify-between">
        <h2 className="text-[19px] font-extrabold">이 식물의 일지</h2>
        <Link href={`/journals/new?plant=${plant.id}`} className="text-sm font-bold text-brand-dark">+ 오늘의 일지 쓰기</Link>
      </div>
      <div className="mt-3.5 flex flex-col gap-3">
        {journals.map((j) => (
          <Link key={j.id} href={`/journals/${j.id}`} className="flex items-center gap-3.5 rounded-2xl bg-white p-3.5 text-ink shadow-card hover:text-ink">
            <div className="flex h-[66px] w-[66px] flex-none items-center justify-center rounded-xl text-[32px]" style={{ background: j.grad }}>{j.emoji}</div>
            <div className="flex-1">
              <div className="text-[13px] text-faint">{j.date}</div>
              <div className="mt-[3px] text-[14.5px] text-[#4a5647]">{j.content.length > 40 ? j.content.slice(0, 40) + '…' : j.content}</div>
            </div>
            {j.rewarded && (
              <span className="whitespace-nowrap rounded-full bg-gold-soft px-2.5 py-[5px] text-xs font-extrabold text-gold-text">
                <span className="material-symbols-outlined text-[13px]">auto_awesome</span> 지급
              </span>
            )}
          </Link>
        ))}
      </div>

      {editable && (
        <div className="mt-[34px] rounded-2xl border-[1.5px] border-dashed border-[#f0c9b8] bg-[#fdf6f3] px-5 py-[18px]">
          <div className="mb-1 font-extrabold text-[#b5502f]">위험 구역</div>
          <div className="mb-3 text-[13.5px] text-[#a56b58]">보관하면 목록에서 사라지고 되돌릴 수 없어요. 일지는 소중히 보관돼요.</div>
          <button type="button" onClick={archive} className="cursor-pointer rounded-[11px] border-[1.5px] border-[#e8bdad] bg-white px-[18px] py-2.5 font-bold text-[#b5502f]">
            보관하기
          </button>
        </div>
      )}

      {statusOpen && (
        <div onClick={() => setStatusOpen(false)} className="fixed inset-0 z-[60] flex items-center justify-center bg-[rgba(46,54,42,.4)] p-5">
          <div onClick={(e) => e.stopPropagation()} className="w-full max-w-[400px] animate-pop rounded-[20px] bg-white p-6">
            <h3 className="mb-4 text-lg font-extrabold">상태·정보 수정</h3>
            <label className="text-[13px] font-bold text-[#6d7a68]">별명</label>
            <input
              value={editNick}
              onChange={(e) => setEditNick(e.target.value)}
              className="mb-4 mt-1.5 w-full rounded-xl border-[1.5px] border-line px-[13px] py-3 outline-none"
            />
            <label className="text-[13px] font-bold text-[#6d7a68]">상태</label>
            <div className="mb-5 mt-2 flex flex-wrap gap-2">
              {[['GROWING', '재배중'], ['HARVESTED', '수확완료'], ['FAILED', '실패']].map(([k, label]) => (
                <button
                  key={k}
                  type="button"
                  onClick={() => setEditStatus(k)}
                  className={`cursor-pointer rounded-[11px] border-[1.5px] px-4 py-[9px] text-sm font-bold ${
                    editStatus === k ? 'border-brand bg-[#F3F8EA] text-ink' : 'border-[#eceee5] bg-white text-[#6d7a68]'
                  }`}
                >
                  {label}
                </button>
              ))}
            </div>
            <button type="button" onClick={saveStatus} className="w-full cursor-pointer rounded-xl bg-brand p-[13px] font-extrabold text-white">저장하기</button>
          </div>
        </div>
      )}
    </div>
  );
}
