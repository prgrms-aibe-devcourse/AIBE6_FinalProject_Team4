'use client';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useStore } from '@/lib/store';
import { useUI } from '@/lib/ui';
import { JOURNALS, BADGE } from '@/lib/data';
import { ApiError } from '@/lib/api';
import { deletePlant, getPlant, PlantProfileData, PlantStatus, updatePlant } from '@/lib/plant-api';
import { dPlus, formatDate, plantVisual } from '@/lib/plant-visual';

export default function PlantDetail({ params }: { params: { id: string } }) {
  const { state, hydrated, set } = useStore();
  const { showToast, askConfirm } = useUI();
  const router = useRouter();
  const id = Number(params.id);

  const [plant, setPlant] = useState<PlantProfileData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [careOpen, setCareOpen] = useState(true);
  const [statusOpen, setStatusOpen] = useState(false);
  const [editNick, setEditNick] = useState('');
  const [editStatus, setEditStatus] = useState<PlantStatus>('GROWING');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!hydrated || !state.accessToken) return;
    const accessToken = state.accessToken;
    const controller = new AbortController();
    setLoading(true);
    setError('');

    getPlant(id, accessToken, controller.signal)
      .then((data) => setPlant(data))
      .catch((requestError) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return;
        setPlant(null);
        setError(
          requestError instanceof ApiError
            ? requestError.message
            : '식물 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.',
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [hydrated, state.accessToken, id]);

  const remove = () => {
    if (!plant || !state.accessToken) return;
    const accessToken = state.accessToken;
    askConfirm({
      icon: 'delete', title: '정말 삭제할까요?', ok: '삭제하기', danger: true,
      body: '삭제하면 이 식물과 일지가 함께 사라지고 되돌릴 수 없어요.',
      onOk: async () => {
        try {
          await deletePlant(plant.id, accessToken);
          set((s) => ({
            plantCount: Math.max(0, s.plantCount - 1),
            growingCount: plant.status === 'GROWING' ? Math.max(0, s.growingCount - 1) : s.growingCount,
          }));
          showToast('식물을 삭제했어요.');
          router.push('/plants');
        } catch (requestError) {
          showToast(
            requestError instanceof ApiError ? requestError.message : '삭제에 실패했어요. 잠시 후 다시 시도해 주세요.',
            'err',
          );
        }
      },
    });
  };

  const saveStatus = async () => {
    if (!plant || !state.accessToken) return;
    setSaving(true);
    try {
      const updated = await updatePlant(
        plant.id,
        { nickname: editNick || plant.nickname, status: editStatus },
        state.accessToken,
      );
      setPlant(updated);
      setStatusOpen(false);
      showToast('식물 정보를 수정했어요 🌿');
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError ? requestError.message : '수정에 실패했어요. 잠시 후 다시 시도해 주세요.',
        'err',
      );
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="container">
        <div className="rounded-[22px] bg-white py-14 text-center text-[15px] text-sub">식물 정보를 불러오고 있어요 🌱</div>
      </div>
    );
  }

  if (error || !plant) {
    return (
      <div className="container">
        <Link href="/plants" className="text-sm font-semibold text-sub">← 내 식물</Link>
        <div className="mt-4 rounded-[22px] bg-white py-14 text-center text-[15px] text-sub">{error || '식물을 찾을 수 없어요.'}</div>
      </div>
    );
  }

  const b = (BADGE as Record<string, { label: string; bg: string; color: string }>)[plant.status];
  const visual = plantVisual(plant.speciesName);
  const journals = JOURNALS.filter((j) => j.plantId === plant.id);

  return (
    <div className="container">
      <Link href="/plants" className="text-sm font-semibold text-sub">← 내 식물</Link>

      <div className="mt-4 grid items-start gap-[26px] [grid-template-columns:repeat(auto-fit,minmax(280px,1fr))]">
        <div className="flex h-[300px] items-center justify-center overflow-hidden rounded-[22px] text-[140px]" style={{ background: visual.grad }}>{visual.emoji}</div>
        <div>
          <div className="mb-2.5 inline-block rounded-full px-3 py-[5px] text-xs font-extrabold" style={{ background: b.bg, color: b.color }}>{b.label}</div>
          <h1 className="mb-1.5 text-[28px] font-extrabold">{plant.nickname}</h1>
          <div className="flex items-center gap-1.5 text-[15px] text-sub">
            {plant.speciesName}
            <span className="text-xs text-[#c2c9b8]"><span className="material-symbols-outlined text-sm">lock</span> 종은 변경할 수 없어요</span>
          </div>
          <div className="mt-2 text-sm text-faint">
            <span className="material-symbols-outlined text-[15px]">calendar_month</span> {formatDate(plant.startDate)} 시작 · D+{dPlus(plant.startDate)}
          </div>

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
        </div>
      </div>

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

      <div className="mt-[34px] rounded-2xl border-[1.5px] border-dashed border-[#f0c9b8] bg-[#fdf6f3] px-5 py-[18px]">
        <div className="mb-1 font-extrabold text-[#b5502f]">위험 구역</div>
        <div className="mb-3 text-[13.5px] text-[#a56b58]">삭제하면 이 식물과 일지가 함께 사라지고 되돌릴 수 없어요.</div>
        <button type="button" onClick={remove} className="cursor-pointer rounded-[11px] border-[1.5px] border-[#e8bdad] bg-white px-[18px] py-2.5 font-bold text-[#b5502f]">
          삭제하기
        </button>
      </div>

      {statusOpen && (
        <div onClick={() => !saving && setStatusOpen(false)} className="fixed inset-0 z-[60] flex items-center justify-center bg-[rgba(46,54,42,.4)] p-5">
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
              {(['GROWING', 'HARVESTED', 'FAILED'] as const).map((k) => (
                <button
                  key={k}
                  type="button"
                  onClick={() => setEditStatus(k)}
                  className={`cursor-pointer rounded-[11px] border-[1.5px] px-4 py-[9px] text-sm font-bold ${
                    editStatus === k ? 'border-brand bg-[#F3F8EA] text-ink' : 'border-[#eceee5] bg-white text-[#6d7a68]'
                  }`}
                >
                  {BADGE[k].label}
                </button>
              ))}
            </div>
            <button type="button" onClick={saveStatus} disabled={saving} className="w-full cursor-pointer rounded-xl bg-brand p-[13px] font-extrabold text-white disabled:opacity-60">
              {saving ? '저장 중...' : '저장하기'}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
