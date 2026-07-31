'use client';
import { useEffect, useState, useRef, Suspense } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { fmt, useStore } from '@/lib/store';
import { useUI } from '@/lib/ui';
import { ApiError } from '@/lib/api';
import { getMyPlants, PlantProfileData } from '@/lib/plant-api';
import { plantVisual } from '@/lib/plant-visual';
import { createJournal, PlantJournalCreateData, deleteJournalImage, uploadJournalImage } from '@/lib/journal-api';

const MAX_SIZE = 5 * 1024 * 1024;
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

interface Draft {
  plantId: number | null;
  content: string;
}

function NewJournalInner() {
  const params = useSearchParams();
  const router = useRouter();
  const preselect = params.get('plant');
  const { state, hydrated, refreshWallet } = useStore();
  const { showToast } = useUI();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [plants, setPlants] = useState<PlantProfileData[]>([]);
  const [plantsLoading, setPlantsLoading] = useState(true);
  const [draft, setDraft] = useState<Draft>({ plantId: preselect ? Number(preselect) : null, content: '' });
  const [photoFile, setPhotoFile] = useState<File | null>(null);
  const [photoPreview, setPhotoPreview] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [saved, setSaved] = useState(false);
  const [createResult, setCreateResult] = useState<PlantJournalCreateData | null>(null);
  const [plantModalOpen, setPlantModalOpen] = useState(false);

  useEffect(() => {
    if (!hydrated || !state.accessToken) return;
    const accessToken = state.accessToken;
    const controller = new AbortController();
    setPlantsLoading(true);

    getMyPlants(accessToken, controller.signal)
      .then((data) => setPlants(data))
      .catch((requestError) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return;
        setPlants([]);
      })
      .finally(() => {
        if (!controller.signal.aborted) setPlantsLoading(false);
      });

    return () => controller.abort();
  }, [hydrated, state.accessToken]);

  const pickPhoto = (file: File | null) => {
    if (!file) return;
    if (!ALLOWED_TYPES.includes(file.type)) {
      return showToast('jpg, png, webp 형식만 가능해요.', 'err');
    }
    if (file.size > MAX_SIZE) {
      return showToast('5MB 이하 사진만 올릴 수 있어요.', 'err');
    }
    if (photoPreview) URL.revokeObjectURL(photoPreview);
    setPhotoFile(file);
    setPhotoPreview(URL.createObjectURL(file));
  };

  const submit = async () => {
    if (!photoFile) return showToast('앗, 사진이 꼭 필요해요. 오늘의 모습을 한 장 담아주세요 📷', 'err');
    if (draft.plantId === null) return showToast('먼저 어떤 식물인지 골라주세요 🌿', 'err');
    if (!state.accessToken) return;

    setSubmitting(true);
    try {
      const uploaded = await uploadJournalImage(photoFile, state.accessToken);
      let result;
      try {
        result = await createJournal(
          {
            plantProfileId: draft.plantId,
            content: draft.content,
            images: [{ imageUrl: uploaded.imageUrl, imageHash: uploaded.imageHash, representative: true }],
          },
          state.accessToken,
        );
      } catch (createError) {
        deleteJournalImage(uploaded.imageUrl, state.accessToken).catch(() => {});
        throw createError;
      }
      await refreshWallet();
      setCreateResult(result);
      setSaved(true);
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError ? requestError.message : '일지 저장에 실패했어요. 잠시 후 다시 시도해 주세요.',
        'err',
      );
    } finally {
      setSubmitting(false);
    }
  };

  const reset = () => {
    if (photoPreview) URL.revokeObjectURL(photoPreview);
    setSaved(false);
    setCreateResult(null);
    setDraft({ plantId: null, content: '' });
    setPhotoFile(null);
    setPhotoPreview(null);
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  const selectedPlant = plants.find((p) => p.id === draft.plantId) ?? null;

  const selectPlant = (plant: PlantProfileData) => {
    if (plant.status === 'FAILED') return;
    setDraft({ ...draft, plantId: plant.id });
    setPlantModalOpen(false);
  };

  return (
    <div className="container">
      <button type="button" onClick={() => router.back()} className="cursor-pointer rounded-[10px] border-[1.5px] border-line bg-white px-3 py-2 text-sm font-semibold text-sub hover:bg-brand-soft hover:text-brand-dark">← 뒤로</button>
      <h1 className="mb-1 mt-3.5 text-[26px] font-extrabold">오늘의 일지 쓰기</h1>
      <p className="mb-[22px] text-[14.5px] text-sub">오늘 이 아이의 모습을 남겨주세요.</p>

      {saved ? (
        <div className="max-w-[640px] rounded-[18px] bg-brand-soft p-6">
          <div className="text-[34px]">🌿</div>
          <div className="mt-2 text-lg font-extrabold text-ink">일지가 저장됐어요!</div>
          {createResult?.rewardGranted ? (
            <div className="mt-2 font-bold text-gold-text">
              일지 보상 {fmt(createResult.rewardAmount)}P가 지급됐어요!
            </div>
          ) : (
            <div className="mt-2 font-bold text-sub">오늘 보상은 이미 완료됐어요.</div>
          )}
          <div className="mt-[18px] flex flex-wrap gap-2.5">
            <Link href="/journals" className="rounded-[11px] bg-ink px-5 py-[11px] font-bold text-white hover:text-white">
              일지 목록으로
            </Link>
            <button type="button" onClick={reset} className="cursor-pointer rounded-[11px] bg-white/70 px-5 py-[11px] font-bold text-ink">
              다른 식물도 기록
            </button>
          </div>
        </div>
      ) : (
        <div className="max-w-[640px] rounded-[20px] bg-white p-6 shadow-card">
          <div className="mb-3 font-extrabold">1. 어떤 식물인가요?</div>
          {plantsLoading ? (
            <div className="mb-[26px] text-sm text-sub">식물 목록을 불러오고 있어요...</div>
          ) : (
            <button
              type="button"
              onClick={() => setPlantModalOpen(true)}
              className="mb-[26px] flex w-full cursor-pointer items-center gap-3 rounded-[14px] border-2 border-[#eceee5] bg-white p-3 text-left hover:border-brand"
            >
              {selectedPlant ? (
                <>
                  <span
                    className="flex h-[52px] w-[52px] flex-none items-center justify-center rounded-[11px] text-[26px]"
                    style={{ background: plantVisual(selectedPlant.speciesName).grad }}
                  >
                    {plantVisual(selectedPlant.speciesName).emoji}
                  </span>
                  <span className="font-bold">{selectedPlant.nickname}</span>
                </>
              ) : (
                <>
                  <span className="flex h-[52px] w-[52px] flex-none items-center justify-center rounded-[11px] bg-[#f9faf6] text-[#a9b3a0]">
                    <span className="material-symbols-outlined">potted_plant</span>
                  </span>
                  <span className="font-bold text-[#a9b3a0]">식물을 선택해 주세요</span>
                </>
              )}
              <span className="material-symbols-outlined ml-auto text-faint">chevron_right</span>
            </button>
          )}

          <div className="mb-[5px] font-extrabold">2. 오늘의 사진 <span className="text-[#e5533b]">*</span></div>
          <div className="mb-3 text-[12.5px] text-[#a9b3a0]">jpg · png · webp / 5MB 이하</div>
          <input
            ref={fileInputRef}
            type="file"
            accept="image/jpeg,image/png,image/webp"
            onChange={(e) => pickPhoto(e.target.files?.[0] ?? null)}
            className="hidden"
          />
          <div className="mb-[26px] flex items-center gap-4">
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              className={`flex h-[100px] w-[100px] flex-none cursor-pointer flex-col items-center justify-center gap-1.5 overflow-hidden rounded-[14px] border-[1.5px] ${
                photoPreview ? 'border-transparent' : 'border-dashed border-line bg-[#f9faf6] text-[#a9b3a0]'
              }`}
            >
              {photoPreview ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img src={photoPreview} alt="" className="h-full w-full object-cover" />
              ) : (
                <>
                  <span className="material-symbols-outlined text-2xl">photo_camera</span>
                  <span className="text-[11px] font-bold">사진 선택</span>
                </>
              )}
            </button>
            {photoPreview && (
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                className="cursor-pointer rounded-[11px] bg-brand-soft px-4 py-2.5 font-bold text-brand-dark"
              >
                <span className="material-symbols-outlined text-base">photo_camera</span> 사진 교체
              </button>
            )}
          </div>

          <div className="mb-2.5 font-extrabold">3. 오늘의 기록</div>
          <textarea
            value={draft.content}
            onChange={(e) => setDraft({ ...draft, content: e.target.value })}
            placeholder="오늘 이 아이는 어떤 모습이었나요?"
            maxLength={2000}
            className="min-h-[130px] w-full resize-y rounded-[14px] border-[1.5px] border-line p-3.5 text-[15px] leading-[1.6] outline-none"
          />
          <div className="mt-[5px] text-right text-xs text-faint">{draft.content.length} / 2000</div>
          <button
            type="button"
            onClick={submit}
            disabled={submitting}
            className="mt-3 w-full cursor-pointer rounded-[14px] bg-brand p-[15px] text-base font-extrabold text-white disabled:opacity-60"
          >
            {submitting ? '저장 중...' : '기록하기'}
          </button>
        </div>
      )}

      {plantModalOpen && (
        <div onClick={() => setPlantModalOpen(false)} className="fixed inset-0 z-[60] flex items-center justify-center bg-[rgba(46,54,42,.4)] p-5">
          <div onClick={(e) => e.stopPropagation()} className="w-full max-w-[420px] animate-pop rounded-[20px] bg-white p-6">
            <h3 className="mb-1 text-[19px] font-extrabold">어떤 식물인가요?</h3>
            <p className="mb-4 text-[13.5px] text-sub">오늘 기록을 남길 식물을 골라주세요.</p>
            <div className="flex max-h-[360px] flex-col gap-2 overflow-y-auto">
              {plants.map((p) => {
                const visual = plantVisual(p.speciesName);
                const disabled = p.status === 'FAILED';
                return (
                  <button
                    key={p.id}
                    type="button"
                    disabled={disabled}
                    onClick={() => selectPlant(p)}
                    className={`flex cursor-pointer items-center gap-3 rounded-[13px] border-2 p-2.5 text-left ${
                      disabled
                        ? 'cursor-not-allowed border-transparent opacity-45'
                        : draft.plantId === p.id
                          ? 'border-brand bg-[#F3F8EA]'
                          : 'border-[#eceee5] bg-white hover:border-brand'
                    }`}
                  >
                    <span
                      className="flex h-[46px] w-[46px] flex-none items-center justify-center rounded-[10px] text-[22px]"
                      style={{ background: visual.grad }}
                    >
                      {visual.emoji}
                    </span>
                    <span className="flex-1 font-bold">{p.nickname}</span>
                    {disabled && <span className="text-xs font-bold text-faint">실패</span>}
                  </button>
                );
              })}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default function NewJournal() {
  return (<Suspense fallback={<div className="container" />}><NewJournalInner /></Suspense>);
}
