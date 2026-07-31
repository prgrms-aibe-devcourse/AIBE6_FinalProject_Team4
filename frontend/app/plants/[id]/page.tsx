'use client';
import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useStore } from '@/lib/store';
import { useUI } from '@/lib/ui';
import { BADGE } from '@/lib/data';
import { ApiError, resolveImageUrl } from '@/lib/api';
import { deletePlant, deletePlantImage, getPlant, PlantProfileData, PlantStatus, updatePlant, uploadPlantImage } from '@/lib/plant-api';
import { dPlus, EMOJI_THUMBNAIL_PREFIX, formatDate, plantThumbnail, PROFILE_EMOJI_OPTIONS } from '@/lib/plant-visual';
import { getJournals, PlantJournalData } from '@/lib/journal-api';

const MAX_PHOTO_SIZE = 5 * 1024 * 1024;
const ALLOWED_PHOTO_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

function representativeImage(journal: PlantJournalData): string | null {
  const url = journal.images.find((img) => img.representative)?.imageUrl || journal.images[0]?.imageUrl || null;
  return url ? resolveImageUrl(url) : null;
}

// photoPreview는 서버 이미지 URL(재사용, 해제하면 안 됨)이나 로컬 blob 미리보기(해제해야 함) 둘 다 담을 수 있다.
function revokeIfBlobUrl(url: string | null) {
  if (url?.startsWith('blob:')) URL.revokeObjectURL(url);
}

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
  const [journals, setJournals] = useState<PlantJournalData[]>([]);
  const [photoMode, setPhotoMode] = useState<'upload' | 'emoji'>('upload');
  const [photoFile, setPhotoFile] = useState<File | null>(null);
  const [photoPreview, setPhotoPreview] = useState<string | null>(null);
  const [selectedEmojiIdx, setSelectedEmojiIdx] = useState(0);
  const fileInputRef = useRef<HTMLInputElement>(null);

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

  useEffect(() => {
    if (!hydrated || !state.accessToken) return;
    const accessToken = state.accessToken;
    const controller = new AbortController();

    getJournals({ profileId: id, size: 50 }, accessToken, controller.signal)
      .then((page) => setJournals(page.content))
      .catch((requestError) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return;
        setJournals([]);
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

  const openStatusModal = () => {
    if (!plant) return;
    revokeIfBlobUrl(photoPreview);
    setEditNick(plant.nickname);
    setEditStatus(plant.status);
    const thumb = plantThumbnail(plant.thumbnailUrl, plant.speciesName);
    if (thumb.type === 'image') {
      setPhotoMode('upload');
      setPhotoPreview(resolveImageUrl(thumb.url));
    } else {
      setPhotoMode('emoji');
      const idx = PROFILE_EMOJI_OPTIONS.findIndex(([emoji]) => emoji === thumb.emoji);
      setSelectedEmojiIdx(idx >= 0 ? idx : 0);
      setPhotoPreview(null);
    }
    setPhotoFile(null);
    setStatusOpen(true);
  };

  const closeStatusModal = () => {
    if (!plant) {
      setStatusOpen(false);
      return;
    }
    const original = plantThumbnail(plant.thumbnailUrl, plant.speciesName);
    const originalMode = original.type === 'image' ? 'upload' : 'emoji';
    const photoChanged =
      photoFile !== null ||
      photoMode !== originalMode ||
      (photoMode === 'emoji' && original.type === 'emoji' && PROFILE_EMOJI_OPTIONS[selectedEmojiIdx][0] !== original.emoji);
    const hasChanges = editNick !== plant.nickname || editStatus !== plant.status || photoChanged;

    if (!hasChanges) {
      revokeIfBlobUrl(photoPreview);
      setStatusOpen(false);
      return;
    }
    askConfirm({
      icon: 'delete',
      title: '변경사항을 취소할까요?',
      body: '수정한 내용이 사라지고 되돌릴 수 없어요.',
      ok: '닫기',
      danger: true,
      onOk: () => { revokeIfBlobUrl(photoPreview); setStatusOpen(false); },
    });
  };

  const pickPhoto = (file: File | null) => {
    if (!file) return;
    if (!ALLOWED_PHOTO_TYPES.includes(file.type)) {
      return showToast('jpg, png, webp 형식만 가능해요.', 'err');
    }
    if (file.size > MAX_PHOTO_SIZE) {
      return showToast('5MB 이하 사진만 올릴 수 있어요.', 'err');
    }
    revokeIfBlobUrl(photoPreview);
    setPhotoFile(file);
    setPhotoPreview(URL.createObjectURL(file));
  };

  const saveStatus = async () => {
    if (!plant || !state.accessToken) return;
    setSaving(true);
    try {
      let thumbnailUrl: string | undefined;
      let uploadedThisAttempt = false;
      if (photoMode === 'upload' && photoFile) {
        const uploaded = await uploadPlantImage(photoFile, state.accessToken);
        thumbnailUrl = uploaded.imageUrl;
        uploadedThisAttempt = true;
      } else if (photoMode === 'emoji') {
        thumbnailUrl = EMOJI_THUMBNAIL_PREFIX + PROFILE_EMOJI_OPTIONS[selectedEmojiIdx][0];
      }
      let updated;
      try {
        updated = await updatePlant(
          plant.id,
          {
            nickname: editNick || plant.nickname,
            status: editStatus,
            ...(thumbnailUrl ? { thumbnailUrl } : {}),
          },
          state.accessToken,
        );
      } catch (updateError) {
        if (uploadedThisAttempt && thumbnailUrl) {
          deletePlantImage(thumbnailUrl, state.accessToken).catch(() => {});
        }
        throw updateError;
      }
      setPlant(updated);
      setStatusOpen(false);
      setPhotoFile(null);
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
        <button type="button" onClick={() => router.back()} className="cursor-pointer rounded-[10px] border-[1.5px] border-line bg-white px-3 py-2 text-sm font-semibold text-sub hover:bg-brand-soft hover:text-brand-dark">← 뒤로</button>
        <div className="mt-4 rounded-[22px] bg-white py-14 text-center text-[15px] text-sub">{error || '식물을 찾을 수 없어요.'}</div>
      </div>
    );
  }

  const b = (BADGE as Record<string, { label: string; bg: string; color: string }>)[plant.status];
  const thumb = plantThumbnail(plant.thumbnailUrl, plant.speciesName);

  return (
    <div className="container">
      <button type="button" onClick={() => router.back()} className="cursor-pointer rounded-[10px] border-[1.5px] border-line bg-white px-3 py-2 text-sm font-semibold text-sub hover:bg-brand-soft hover:text-brand-dark">← 뒤로</button>

      <div className="relative mt-4 grid items-start gap-[26px] [grid-template-columns:repeat(auto-fit,minmax(280px,1fr))]">
        <button
          type="button"
          onClick={remove}
          className="absolute right-0 top-0 z-10 cursor-pointer text-xs font-bold text-[#b5502f]"
        >
          삭제
        </button>
        {thumb.type === 'image' ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={resolveImageUrl(thumb.url)} alt="" className="h-[300px] w-full rounded-[22px] object-cover" />
        ) : (
          <div className="flex h-[300px] items-center justify-center overflow-hidden rounded-[22px] text-[140px]" style={{ background: thumb.grad }}>{thumb.emoji}</div>
        )}
        <div>
          <div className="mb-2.5 inline-block rounded-full px-3 py-[5px] text-xs font-extrabold" style={{ background: b.bg, color: b.color }}>{b.label}</div>
          <h1 className="mb-1.5 text-[28px] font-extrabold">{plant.nickname}</h1>
          <div className="flex items-center gap-1.5 text-[15px] text-sub">
            {plant.speciesName}
          </div>
          <div className="mt-2 text-sm text-faint">
            <span className="material-symbols-outlined text-[15px]">calendar_month</span> {formatDate(plant.startDate)} 시작 · D+{dPlus(plant.startDate)}
          </div>

          <div className="mt-5 flex flex-wrap gap-2.5">
            <button
              type="button"
              onClick={openStatusModal}
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
        {journals.length === 0 ? (
          <div className="rounded-2xl bg-white px-3.5 py-8 text-center text-sm text-sub shadow-card">아직 남긴 일지가 없어요.</div>
        ) : (
          journals.map((j) => {
            const image = representativeImage(j);
            return (
              <Link key={j.id} href={`/journals/${j.id}`} className="flex items-center gap-3.5 rounded-2xl bg-white p-3.5 text-ink shadow-card hover:text-ink">
                <div className="flex h-[66px] w-[66px] flex-none items-center justify-center overflow-hidden rounded-xl bg-brand-soft text-[32px]">
                  {image ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={image} alt="" className="h-full w-full object-cover" />
                  ) : (
                    '🌿'
                  )}
                </div>
                <div className="flex-1">
                  <div className="text-[13px] text-faint">{formatDate(j.writtenDate)}</div>
                  <div className="mt-[3px] text-[14.5px] text-[#4a5647]">{j.content.length > 40 ? j.content.slice(0, 40) + '…' : j.content}</div>
                </div>
              </Link>
            );
          })
        )}
      </div>

      {statusOpen && (
        <div onClick={() => !saving && closeStatusModal()} className="fixed inset-0 z-[60] flex items-center justify-center bg-[rgba(46,54,42,.4)] p-5">
          <div onClick={(e) => e.stopPropagation()} className="w-full max-w-[400px] animate-pop rounded-[20px] bg-white p-6">
            <h3 className="mb-4 text-lg font-extrabold">상태·정보 수정</h3>
            <label className="text-[13px] font-bold text-[#6d7a68]">별명</label>
            <input
              value={editNick}
              onChange={(e) => setEditNick(e.target.value)}
              className="mb-4 mt-1.5 w-full rounded-xl border-[1.5px] border-line px-[13px] py-3 outline-none"
            />

            <div className="flex items-center justify-between">
              <label className="text-[13px] font-bold text-[#6d7a68]">대표 사진</label>
              <button
                type="button"
                onClick={() => setPhotoMode(photoMode === 'upload' ? 'emoji' : 'upload')}
                className="cursor-pointer text-xs font-bold text-brand-dark"
              >
                {photoMode === 'upload' ? '이모지로 대신할게요' : '사진 업로드로 전환'}
              </button>
            </div>

            {photoMode === 'upload' ? (
              <div className="mb-4 mt-2">
                <input
                  ref={fileInputRef}
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  onChange={(e) => pickPhoto(e.target.files?.[0] ?? null)}
                  className="hidden"
                />
                <div className="flex items-center gap-4">
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
                <div className="mt-2 text-xs text-[#a9b3a0]">jpg · png · webp / 5MB 이하</div>
              </div>
            ) : (
              <div className="mb-4 mt-2 flex flex-wrap gap-2.5">
                {PROFILE_EMOJI_OPTIONS.map(([emoji, grad], i) => (
                  <button
                    key={i}
                    type="button"
                    onClick={() => setSelectedEmojiIdx(i)}
                    className={`flex h-16 w-16 cursor-pointer items-center justify-center rounded-xl border-[3px] text-[28px] ${
                      selectedEmojiIdx === i ? 'border-brand' : 'border-transparent'
                    }`}
                    style={{ background: grad }}
                  >
                    {emoji}
                  </button>
                ))}
              </div>
            )}

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
