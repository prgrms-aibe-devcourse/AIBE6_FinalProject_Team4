'use client';
import { useEffect, useState, useRef, Suspense } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useStore } from '@/lib/store';
import { useUI } from '@/lib/ui';
import { ApiError } from '@/lib/api';
import { getMyPlants, PlantProfileData } from '@/lib/plant-api';
import { plantVisual, SEEDLING_ICON_SRC } from '@/lib/plant-visual';
import { createJournal, deleteJournalImage, uploadJournalImage } from '@/lib/journal-api';
import { localToday } from '@/lib/format';

const MAX_SIZE = 5 * 1024 * 1024;
const MAX_PHOTOS = 3;
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
// 오늘 하루 가이드 모달을 다시 안 보기로 선택했는지 기억하는 키 — 날짜 문자열(YYYY-MM-DD)을
// 저장해두고, 저장된 날짜가 오늘과 다르면(자정이 지나면) 다시 보여준다.
const PHOTO_GUIDE_DISMISS_KEY = 'kwb_photo_guide_dismissed_date';

// localToday()로 KST 기준 날짜를 쓴다 — new Date().toISOString()은 UTC라 KST 자정~오전 9시
// 사이에는 아직 전날로 계산돼, 자정이 지났는데도 최대 9시간 동안 모달이 안 뜨는 버그가 있었다.
function todayString() {
  return localToday();
}

interface Draft {
  plantId: number | null;
  content: string;
}

interface Photo {
  file: File;
  preview: string;
}

function NewJournalInner() {
  const router = useRouter();
  const params = useSearchParams();
  const preselect = params.get('plant');
  const { state, hydrated, refreshWallet } = useStore();
  const { showToast } = useUI();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [plants, setPlants] = useState<PlantProfileData[]>([]);
  const [plantsLoading, setPlantsLoading] = useState(true);
  const [draft, setDraft] = useState<Draft>({ plantId: preselect ? Number(preselect) : null, content: '' });
  const [photos, setPhotos] = useState<Photo[]>([]);
  const [representativeIndex, setRepresentativeIndex] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [saved, setSaved] = useState(false);
  const [plantModalOpen, setPlantModalOpen] = useState(false);
  const [guideModalOpen, setGuideModalOpen] = useState(false);
  const [guideDontShowToday, setGuideDontShowToday] = useState(false);

  useEffect(() => {
    if (!hydrated || !state.accessToken) return;
    const accessToken = state.accessToken;
    const controller = new AbortController();
    setPlantsLoading(true);

    getMyPlants({ accessToken, size: 100, signal: controller.signal })
      .then((data) => setPlants(data.content))
      .catch((requestError) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return;
        setPlants([]);
      })
      .finally(() => {
        if (!controller.signal.aborted) setPlantsLoading(false);
      });

    return () => controller.abort();
  }, [hydrated, state.accessToken]);

  // 사진 추가 버튼을 누르면 여기로 온다 — 오늘 이미 "다시 안 보기"를 선택했으면 가이드
  // 모달 없이 바로 파일 선택창을 띄우고, 아니면 매번 가이드 모달부터 보여준다.
  const openPhotoPicker = () => {
    if (photos.length >= MAX_PHOTOS) return;
    if (typeof window !== 'undefined' && window.localStorage.getItem(PHOTO_GUIDE_DISMISS_KEY) === todayString()) {
      fileInputRef.current?.click();
      return;
    }
    setGuideDontShowToday(false);
    setGuideModalOpen(true);
  };

  const confirmPhotoGuide = () => {
    if (guideDontShowToday && typeof window !== 'undefined') {
      window.localStorage.setItem(PHOTO_GUIDE_DISMISS_KEY, todayString());
    }
    setGuideModalOpen(false);
    fileInputRef.current?.click();
  };

  const pickPhotos = (files: FileList | null) => {
    if (!files || files.length === 0) return;
    const remaining = MAX_PHOTOS - photos.length;
    if (files.length > remaining) {
      if (fileInputRef.current) fileInputRef.current.value = '';
      return showToast(`사진은 최대 ${MAX_PHOTOS}장까지 첨부할 수 있어요. 남은 ${remaining}장 이하로 다시 선택해 주세요.`, 'err');
    }
    for (const file of files) {
      if (!ALLOWED_TYPES.includes(file.type)) {
        if (fileInputRef.current) fileInputRef.current.value = '';
        return showToast('jpg, png, webp 형식만 가능해요.', 'err');
      }
      if (file.size > MAX_SIZE) {
        if (fileInputRef.current) fileInputRef.current.value = '';
        return showToast('5MB 이하 사진만 올릴 수 있어요.', 'err');
      }
    }
    const added = Array.from(files).map((file) => ({ file, preview: URL.createObjectURL(file) }));
    setPhotos((prev) => [...prev, ...added]);
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  const removePhoto = (index: number) => {
    setPhotos((prev) => {
      URL.revokeObjectURL(prev[index].preview);
      return prev.filter((_, i) => i !== index);
    });
    setRepresentativeIndex((prev) => {
      if (index === prev) return 0;
      return index < prev ? prev - 1 : prev;
    });
  };

  const submit = async () => {
    if (photos.length === 0) return showToast('앗, 사진이 꼭 필요해요. 오늘의 모습을 한 장 담아주세요', 'err');
    if (draft.plantId === null) return showToast('먼저 어떤 식물인지 골라주세요', 'err');
    if (!state.accessToken) return;
    const accessToken = state.accessToken;

    setSubmitting(true);
    let uploaded: { imageUrl: string; imageHash: string }[] = [];
    try {
      const results = await Promise.allSettled(photos.map((photo) => uploadJournalImage(photo.file, accessToken)));
      const firstRejected = results.find((r): r is PromiseRejectedResult => r.status === 'rejected');
      if (firstRejected) {
        // 일부만 실패하면 이미 S3에 올라간 나머지 성공분을 정리해 orphan 이미지가 남지 않게 한다.
        results.forEach((r) => {
          if (r.status === 'fulfilled') deleteJournalImage(r.value.imageUrl, accessToken).catch(() => {});
        });
        throw firstRejected.reason;
      }
      uploaded = results.map((r) => (r as PromiseFulfilledResult<{ imageUrl: string; imageHash: string }>).value);
      let result;
      try {
        result = await createJournal(
          {
            plantProfileId: draft.plantId,
            content: draft.content,
            images: uploaded.map((img, i) => ({ ...img, representative: i === representativeIndex })),
          },
          accessToken,
        );
      } catch (createError) {
        uploaded.forEach((img) => deleteJournalImage(img.imageUrl, accessToken).catch(() => {}));
        throw createError;
      }
      await refreshWallet();
      if (result.journal.gachaReward.granted && result.journal.gachaReward.drawId) {
        router.replace(
          `/gacha/open/${result.journal.gachaReward.drawId}?returnTo=journals`,
        );
        return;
      }
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
    photos.forEach((photo) => URL.revokeObjectURL(photo.preview));
    setSaved(false);
    setDraft({ plantId: null, content: '' });
    setPhotos([]);
    setRepresentativeIndex(0);
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
          <span className="material-symbols-outlined text-[34px]">eco</span>
          <div className="mt-2 text-lg font-extrabold text-ink">일지가 저장됐어요!</div>
          <div className="mt-2 font-bold text-sub">오늘 보상을 이미 받았다면, 보너스 포인트는 추가 지급되지 않아요.</div>
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
                    className="flex h-[52px] w-[52px] flex-none items-center justify-center rounded-[11px]"
                    style={{ background: plantVisual(selectedPlant.speciesName).grad }}
                  >
                    <img src={SEEDLING_ICON_SRC} alt="" className="h-[26px] w-[26px]" />
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
          <div className="mb-3 text-[12.5px] text-[#a9b3a0]">jpg · png · webp / 5MB 이하 · 최대 {MAX_PHOTOS}장, 탭해서 대표 사진 선택</div>
          <input
            ref={fileInputRef}
            type="file"
            accept="image/jpeg,image/png,image/webp"
            multiple
            onChange={(e) => pickPhotos(e.target.files)}
            className="hidden"
          />
          <div className="mb-[26px]">
            <div className="flex flex-wrap gap-2.5">
              {photos.map((photo, i) => (
                <button
                  key={photo.preview}
                  type="button"
                  onClick={() => setRepresentativeIndex(i)}
                  className={`relative h-[104px] w-[104px] cursor-pointer overflow-hidden rounded-[14px] border-[2.5px] ${
                    i === representativeIndex ? 'border-brand' : 'border-transparent'
                  }`}
                >
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img src={photo.preview} alt="" className="h-full w-full object-cover" />
                  {i === representativeIndex && (
                    <span className="absolute left-1 top-1 flex h-5 w-5 items-center justify-center rounded-full bg-brand text-[11px] text-white">★</span>
                  )}
                  <span
                    role="button"
                    tabIndex={0}
                    onClick={(e) => {
                      e.stopPropagation();
                      removePhoto(i);
                    }}
                    className="absolute right-1 top-1 flex h-5 w-5 cursor-pointer items-center justify-center rounded-full bg-black/55 text-white"
                  >
                    <span className="material-symbols-outlined text-[14px]">close</span>
                  </span>
                </button>
              ))}
              {photos.length < MAX_PHOTOS && (
                <button
                  type="button"
                  onClick={openPhotoPicker}
                  className="flex h-[104px] w-[104px] cursor-pointer flex-col items-center justify-center gap-1 rounded-[14px] border-[1.5px] border-dashed border-line bg-[#f9faf6] text-[#a9b3a0]"
                >
                  <span className="material-symbols-outlined text-2xl">photo_camera</span>
                  <span className="text-[12px] font-bold">사진 추가</span>
                </button>
              )}
            </div>
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
                      className="flex h-[46px] w-[46px] flex-none items-center justify-center rounded-[10px]"
                      style={{ background: visual.grad }}
                    >
                      <img src={SEEDLING_ICON_SRC} alt="" className="h-[22px] w-[22px]" />
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

      {guideModalOpen && (
        <div onClick={() => setGuideModalOpen(false)} className="fixed inset-0 z-[60] flex items-center justify-center bg-[rgba(46,54,42,.4)] p-5">
          <div onClick={(e) => e.stopPropagation()} className="w-full max-w-[380px] animate-pop rounded-[20px] bg-white p-6">
            <h3 className="mb-1 flex items-center gap-1 text-[19px] font-extrabold">이렇게 찍어보세요 <span className="material-symbols-outlined align-middle text-base">photo_camera</span></h3>
            <p className="mb-4 text-[13.5px] text-sub">식물이 정중앙에 오도록 찍어주시면, 나중에 모아서 타임랩스 영상을 만들어드릴 때 훨씬 예쁘게 이어져요.</p>
            <div className="relative mx-auto mb-4 grid aspect-square w-full max-w-[220px] grid-cols-3 grid-rows-3 overflow-hidden rounded-2xl border-2 border-dashed border-brand bg-brand-soft">
              {Array.from({ length: 9 }).map((_, i) => (
                <div key={i} className="border border-white/50" />
              ))}
              <div className="absolute inset-0 flex items-center justify-center">
                {/* 이모지 렌더링이 OS/브라우저마다 다르게 보여서, 다육식물 사진 느낌으로 직접 그린 아이콘 */}
                <svg viewBox="0 0 100 100" className="h-[65%] w-[65%]" aria-hidden="true">
                  {/* 화분: 위쪽 밝은 테두리 + 아래쪽 본체 이중톤 */}
                  <path d="M32 96 L68 96 L64 74 L36 74 Z" fill="#DCC6AC" />
                  <rect x="33" y="66" width="34" height="9" rx="3" fill="#EAD9C6" />
                  {/* 가는 줄기 */}
                  <path d="M50 66 C 46 52, 42 44, 40 30" stroke="#B08968" strokeWidth="2.2" fill="none" strokeLinecap="round" />
                  <path d="M50 66 C 55 50, 60 38, 62 24" stroke="#B08968" strokeWidth="2.2" fill="none" strokeLinecap="round" />
                  <path d="M56 40 C 58 32, 58 26, 54 18" stroke="#B08968" strokeWidth="1.8" fill="none" strokeLinecap="round" />
                  {/* 줄기마다 마주나는 잎 */}
                  <g>
                    <ellipse cx="34" cy="30" rx="6" ry="10" transform="rotate(-35 34 30)" fill="#9BAE8D" />
                    <ellipse cx="46" cy="28" rx="6" ry="10" transform="rotate(35 46 28)" fill="#8CA07D" />
                    <ellipse cx="40" cy="46" rx="5.5" ry="9" transform="rotate(-30 40 46)" fill="#9BAE8D" />
                    <ellipse cx="50" cy="44" rx="5.5" ry="9" transform="rotate(30 50 44)" fill="#8CA07D" />
                    <ellipse cx="56" cy="22" rx="5.5" ry="9" transform="rotate(-25 56 22)" fill="#9BAE8D" />
                    <ellipse cx="66" cy="20" rx="5.5" ry="9" transform="rotate(25 66 20)" fill="#8CA07D" />
                    <ellipse cx="49" cy="14" rx="4.5" ry="7.5" transform="rotate(-15 49 14)" fill="#9BAE8D" />
                    <ellipse cx="58" cy="14" rx="4.5" ry="7.5" transform="rotate(15 58 14)" fill="#8CA07D" />
                  </g>
                </svg>
              </div>
            </div>
            <label className="mb-4 flex cursor-pointer items-center gap-2 text-[13px] font-bold text-[#6d7a68]">
              <input
                type="checkbox"
                checked={guideDontShowToday}
                onChange={(e) => setGuideDontShowToday(e.target.checked)}
                className="h-4 w-4 accent-brand"
              />
              오늘 하루 다시 보지 않기
            </label>
            <button
              type="button"
              onClick={confirmPhotoGuide}
              className="w-full cursor-pointer rounded-[13px] bg-brand p-3.5 text-base font-extrabold text-white"
            >
              사진 고르러 가기
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

export default function NewJournal() {
  return (<Suspense fallback={<div className="container" />}><NewJournalInner /></Suspense>);
}
