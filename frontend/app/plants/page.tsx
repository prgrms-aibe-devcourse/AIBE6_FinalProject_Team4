'use client';
import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useStore } from '@/lib/store';
import { useUI } from '@/lib/ui';
import { BADGE } from '@/lib/data';
import { ApiError, resolveImageUrl } from '@/lib/api';
import { createPlant, deletePlantImage, getMyPlants, PlantProfileData, uploadPlantImage } from '@/lib/plant-api';
import { getSpecies, PlantSpeciesData } from '@/lib/species-api';
import { dPlus, EMOJI_THUMBNAIL_PREFIX, formatDate, plantThumbnail, plantVisual, PROFILE_EMOJI_OPTIONS } from '@/lib/plant-visual';

const FILTERS = [['all', '전체'], ['GROWING', '재배중'], ['HARVESTED', '수확완료'], ['FAILED', '실패']];
const MAX_PHOTO_SIZE = 5 * 1024 * 1024;
const ALLOWED_PHOTO_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

const FIELD = 'w-full rounded-xl border-[1.5px] border-line px-[13px] py-3 outline-none';
const LABEL = 'text-[13px] font-bold text-[#6d7a68]';

function nickValid(v: string) {
  if (!v) return { ok: false, msg: '별명을 입력해 주세요.' };
  if (v.length > 50) return { ok: false, msg: '50자 이내로 지어주세요.' };
  if (/[^가-힣a-zA-Z0-9 ]/.test(v)) return { ok: false, msg: '특수문자 없이 예쁜 이름으로 지어주세요 🌱' };
  return { ok: true, msg: '좋은 이름이에요! 🌿' };
}

const today = () => new Date().toISOString().slice(0, 10);

export default function PlantsPage() {
  const { state, hydrated, set } = useStore();
  const { showToast, askConfirm } = useUI();
  const [plants, setPlants] = useState<PlantProfileData[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [speciesList, setSpeciesList] = useState<PlantSpeciesData[]>([]);
  const [speciesLoading, setSpeciesLoading] = useState(true);
  const [speciesError, setSpeciesError] = useState('');
  const [filter, setFilter] = useState('all');
  const [open, setOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [reg, setReg] = useState<{ nick: string; speciesId: number | null; photoIdx: number; startDate: string }>({
    nick: '', speciesId: null, photoIdx: 0, startDate: today(),
  });
  const [query, setQuery] = useState('');
  const [photoMode, setPhotoMode] = useState<'upload' | 'emoji'>('upload');
  const [photoFile, setPhotoFile] = useState<File | null>(null);
  const [photoPreview, setPhotoPreview] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!hydrated || !state.accessToken) return;
    const accessToken = state.accessToken;
    const controller = new AbortController();
    setLoading(true);
    setError('');

    getMyPlants(accessToken, controller.signal)
      .then((data) => setPlants(data))
      .catch((requestError) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return;
        setPlants([]);
        setError(
          requestError instanceof ApiError
            ? requestError.message
            : '식물 목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.',
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [hydrated, state.accessToken]);

  useEffect(() => {
    if (!hydrated || !state.accessToken) return;
    const accessToken = state.accessToken;
    const controller = new AbortController();
    setSpeciesLoading(true);
    setSpeciesError('');

    getSpecies(accessToken, controller.signal)
      .then((data) => setSpeciesList(data))
      .catch((requestError) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return;
        setSpeciesList([]);
        setSpeciesError(
          requestError instanceof ApiError
            ? requestError.message
            : '식물 종 목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.',
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setSpeciesLoading(false);
      });

    return () => controller.abort();
  }, [hydrated, state.accessToken]);

  const list = plants.filter((p) => filter === 'all' || p.status === filter);
  const regV = nickValid(reg.nick);
  const spResults = speciesList.filter((sp) => !query.trim() || sp.name.includes(query.trim()));

  const handleSpeciesQueryChange = (value: string) => {
    setQuery(value);
    const selected = speciesList.find((sp) => sp.id === reg.speciesId);
    if (selected && selected.name !== value) {
      setReg({ ...reg, speciesId: null });
    }
  };

  const clearSpeciesSelection = () => {
    setReg({ ...reg, speciesId: null });
    setQuery('');
  };

  const pickPhoto = (file: File | null) => {
    if (!file) return;
    if (!ALLOWED_PHOTO_TYPES.includes(file.type)) {
      return showToast('jpg, png, webp 형식만 가능해요.', 'err');
    }
    if (file.size > MAX_PHOTO_SIZE) {
      return showToast('5MB 이하 사진만 올릴 수 있어요.', 'err');
    }
    if (photoPreview) URL.revokeObjectURL(photoPreview);
    setPhotoFile(file);
    setPhotoPreview(URL.createObjectURL(file));
  };

  const resetRegisterForm = () => {
    if (photoPreview) URL.revokeObjectURL(photoPreview);
    setReg({ nick: '', speciesId: null, photoIdx: 0, startDate: today() });
    setQuery('');
    setPhotoMode('upload');
    setPhotoFile(null);
    setPhotoPreview(null);
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  const closeRegisterModal = () => {
    const hasInput = reg.nick.trim() !== '' || reg.speciesId !== null || photoFile !== null;
    if (!hasInput) {
      setOpen(false);
      return;
    }
    askConfirm({
      icon: 'delete',
      title: '입력을 취소할까요?',
      body: '입력한 내용이 사라지고 되돌릴 수 없어요.',
      ok: '닫기',
      danger: true,
      onOk: () => { setOpen(false); resetRegisterForm(); },
    });
  };

  const submit = async () => {
    if (!state.accessToken) return;
    if (!regV.ok) return showToast(regV.msg, 'err');
    if (!reg.speciesId) return showToast('식물 종을 골라주세요 🌱', 'err');
    if (!reg.startDate) return showToast('재배 시작일을 선택해 주세요.', 'err');

    setSubmitting(true);
    try {
      let thumbnailUrl: string | undefined;
      let uploadedThisAttempt = false;
      if (photoMode === 'upload' && photoFile) {
        const uploaded = await uploadPlantImage(photoFile, state.accessToken);
        thumbnailUrl = uploaded.imageUrl;
        uploadedThisAttempt = true;
      } else if (photoMode === 'emoji') {
        thumbnailUrl = EMOJI_THUMBNAIL_PREFIX + PROFILE_EMOJI_OPTIONS[reg.photoIdx][0];
      }
      let created;
      try {
        created = await createPlant(
          {
            speciesId: reg.speciesId,
            nickname: reg.nick,
            startDate: reg.startDate,
            ...(thumbnailUrl ? { thumbnailUrl } : {}),
          },
          state.accessToken,
        );
      } catch (createError) {
        if (uploadedThisAttempt && thumbnailUrl) {
          deletePlantImage(thumbnailUrl, state.accessToken).catch(() => {});
        }
        throw createError;
      }
      setPlants([created, ...plants]);
      set((s) => ({ growingCount: s.growingCount + 1, plantCount: s.plantCount + 1 }));
      setOpen(false);
      resetRegisterForm();
      showToast(`'${created.nickname}'와의 여정이 시작됐어요! 🌿`);
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError ? requestError.message : '등록에 실패했어요. 잠시 후 다시 시도해 주세요.',
        'err',
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="container">
      <h1 className="mb-1 text-[27px] font-extrabold">내 식물</h1>
      <p className="mb-5 text-sub">함께 자라는 친구들을 한눈에 살펴보세요.</p>

      <div className="mb-[22px] flex flex-wrap gap-[9px]">
        {FILTERS.map(([k, label]) => (
          <button
            key={k}
            type="button"
            onClick={() => setFilter(k)}
            className={`cursor-pointer rounded-full border-[1.5px] px-4 py-2 text-sm font-bold ${
              filter === k ? 'border-brand bg-brand text-white' : 'border-line bg-white text-[#6d7a68]'
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {loading ? (
        <div className="rounded-[22px] bg-white py-14 text-center text-[15px] text-sub">식물 목록을 불러오고 있어요 🌱</div>
      ) : error ? (
        <div className="rounded-[22px] bg-white px-5 py-14 text-center text-[15px] text-sub">{error}</div>
      ) : list.length === 0 ? (
        <div className="rounded-[22px] bg-white px-5 py-[70px] text-center shadow-card">
          <div className="animate-floaty text-[70px]">🌱</div>
          <p className="mb-5 mt-4 text-[17px] font-bold text-[#6d7a68]">아직 함께하는 식물이 없네요.<br />첫 반려식물을 등록해 볼까요?</p>
          <button type="button" onClick={() => setOpen(true)} className="cursor-pointer rounded-xl bg-brand px-[26px] py-[13px] font-bold text-white">+ 새 식물 등록</button>
        </div>
      ) : (
        <div className="grid gap-[18px] [grid-template-columns:repeat(auto-fill,minmax(220px,1fr))]">
          {list.map((p) => {
            const b = (BADGE as Record<string, { label: string; bg: string; color: string }>)[p.status];
            const thumb = plantThumbnail(p.thumbnailUrl, p.speciesName);
            return (
              <Link key={p.id} href={`/plants/${p.id}`} className="relative block overflow-hidden rounded-[18px] bg-white text-ink shadow-card hover:text-ink">
                {thumb.type === 'image' ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={resolveImageUrl(thumb.url)} alt="" className="h-[150px] w-full object-cover" />
                ) : (
                  <div className="flex h-[150px] items-center justify-center text-[72px]" style={{ background: thumb.grad }}>{thumb.emoji}</div>
                )}
                <div className="absolute left-3 top-3 rounded-full px-[11px] py-[5px] text-xs font-extrabold" style={{ background: b.bg, color: b.color }}>{b.label}</div>
                <div className="p-[15px]">
                  <div className="text-base font-extrabold">{p.nickname}</div>
                  <div className="mt-0.5 text-[13px] text-sub">{p.speciesName}</div>
                  <div className="mt-1.5 text-[13px] text-faint">
                    <span className="material-symbols-outlined text-[15px]">calendar_month</span> {formatDate(p.startDate)} · D+{dPlus(p.startDate)}
                  </div>
                </div>
              </Link>
            );
          })}
        </div>
      )}

      <button
        type="button"
        onClick={() => setOpen(true)}
        className="fixed bottom-[84px] right-6 z-30 cursor-pointer rounded-full bg-brand px-[22px] py-[15px] font-extrabold text-white shadow-[0_10px_26px_rgba(124,179,66,.45)]"
      >
        + 새 식물 등록
      </button>

      {open && (
        <div onClick={() => !submitting && closeRegisterModal()} className="fixed inset-0 z-[60] flex items-start justify-center overflow-auto bg-[rgba(46,54,42,.4)] px-5 py-10">
          <div onClick={(e) => e.stopPropagation()} className="w-full max-w-[460px] animate-pop rounded-[22px] bg-white p-[26px]">
            <h3 className="mb-1 text-xl font-extrabold">새 식물 등록 🌿</h3>
            <p className="mb-5 text-[13.5px] text-sub">새 친구의 정보를 알려주세요.</p>

            <label className={LABEL}>별명 <span className="text-[#e5533b]">*</span></label>
            <input
              value={reg.nick}
              onChange={(e) => setReg({ ...reg, nick: e.target.value })}
              placeholder="예: 토실이"
              maxLength={50}
              className={`mb-[5px] mt-1.5 w-full rounded-xl border-[1.5px] px-[13px] py-3 outline-none ${
                reg.nick ? (regV.ok ? 'border-[#AED581]' : 'border-[#f0c9a0]') : 'border-line'
              }`}
            />
            <div className={`mb-4 text-xs ${reg.nick ? (regV.ok ? 'text-brand' : 'text-[#e08a3c]') : 'text-faint'}`}>
              {reg.nick ? regV.msg : '특수문자 없이 50자 이내로 지어주세요.'}
            </div>

            <label className={LABEL}>식물 종 <span className="text-[#e5533b]">*</span></label>
            <input
              value={query}
              onChange={(e) => handleSpeciesQueryChange(e.target.value)}
              placeholder="종을 검색하세요 (예: 토마토)"
              className={`${FIELD} mb-2.5 mt-1.5`}
            />
            {speciesLoading ? (
              <div className="mb-[18px] text-[13.5px] text-faint">종 목록을 불러오고 있어요 🌱</div>
            ) : speciesError ? (
              <div className="mb-[18px] text-[13.5px] text-faint">{speciesError}</div>
            ) : spResults.length === 0 && query.trim() ? (
              <div className="mb-[18px] text-[13.5px] text-faint">
                일치하는 종이 없어요. 다른 이름으로 검색해 보세요.
              </div>
            ) : (
              <div className="mb-[18px] flex flex-wrap gap-2">
                {spResults.map((sp) => {
                  const selected = reg.speciesId === sp.id;
                  return (
                    <button
                      key={sp.id}
                      type="button"
                      onClick={() => { setReg({ ...reg, speciesId: sp.id }); setQuery(sp.name); }}
                      className={`cursor-pointer rounded-[10px] border-[1.5px] px-[13px] py-2 text-[13.5px] font-bold ${
                        selected ? 'border-brand bg-[#F3F8EA] text-ink' : 'border-[#eceee5] bg-white text-[#6d7a68]'
                      }`}
                    >
                      {plantVisual(sp.name).emoji} {sp.name}
                      {selected && (
                        <span
                          role="button"
                          tabIndex={0}
                          onClick={(e) => { e.stopPropagation(); clearSpeciesSelection(); }}
                          className="ml-1.5 text-[#a9b3a0]"
                        >
                          ×
                        </span>
                      )}
                    </button>
                  );
                })}
              </div>
            )}

            <label className={LABEL}>재배 시작일</label>
            <input
              type="date"
              value={reg.startDate}
              onChange={(e) => setReg({ ...reg, startDate: e.target.value })}
              className={`${FIELD} mb-[18px] mt-1.5`}
            />

            <div className="flex items-center justify-between">
              <label className={LABEL}>대표 사진</label>
              <button
                type="button"
                onClick={() => setPhotoMode(photoMode === 'upload' ? 'emoji' : 'upload')}
                className="cursor-pointer text-xs font-bold text-brand-dark"
              >
                {photoMode === 'upload' ? '이모지로 대신할게요' : '사진 업로드로 전환'}
              </button>
            </div>

            {photoMode === 'upload' ? (
              <div className="mt-2">
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
              <>
                <div className="mb-1.5 mt-2 flex flex-wrap gap-2.5">
                  {PROFILE_EMOJI_OPTIONS.map(([emoji, grad], i) => (
                    <button
                      key={i}
                      type="button"
                      onClick={() => setReg({ ...reg, photoIdx: i })}
                      className={`flex h-16 w-16 cursor-pointer items-center justify-center rounded-xl border-[3px] text-[28px] ${
                        reg.photoIdx === i ? 'border-brand' : 'border-transparent'
                      }`}
                      style={{ background: grad }}
                    >
                      {emoji}
                    </button>
                  ))}
                </div>
                <div className="text-xs text-[#a9b3a0]">마음에 드는 색상과 이모지를 골라주세요.</div>
              </>
            )}

            <button type="button" onClick={submit} disabled={submitting} className="mt-[22px] w-full cursor-pointer rounded-[13px] bg-brand p-3.5 text-base font-extrabold text-white disabled:opacity-60">
              {submitting ? '등록 중...' : '등록하기'}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
