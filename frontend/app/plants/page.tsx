'use client';
import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useStore } from '@/lib/store';
import { useUI } from '@/lib/ui';
import { BADGE } from '@/lib/data';
import { ApiError, resolveImageUrl } from '@/lib/api';
import { createPlant, deletePlantImage, getMyPlants, PlantProfileData, PlantStatus, updatePlant, uploadPlantImage } from '@/lib/plant-api';
import { getProfileIdsWrittenToday } from '@/lib/journal-api';
import { getSpecies, PlantSpeciesData } from '@/lib/species-api';
import { dPlus, EMOJI_THUMBNAIL_PREFIX, formatDate, plantThumbnail, plantVisual, PROFILE_EMOJI_OPTIONS } from '@/lib/plant-visual';

const FILTERS = [['all', '전체'], ['GROWING', '재배중'], ['HARVESTED', '수확완료'], ['FAILED', '실패']];
const BULK_STATUS_OPTIONS: [PlantStatus, string][] = [['GROWING', '재배중'], ['HARVESTED', '수확완료'], ['FAILED', '실패']];
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
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
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
  const [todayWrittenIds, setTodayWrittenIds] = useState<Set<number>>(new Set());
  const [todayWrittenError, setTodayWrittenError] = useState(false);
  const [writtenTodayOnly, setWrittenTodayOnly] = useState(false);
  const [selectMode, setSelectMode] = useState(false);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [query, setQuery] = useState('');
  const [photoMode, setPhotoMode] = useState<'upload' | 'emoji'>('upload');
  const [photoFile, setPhotoFile] = useState<File | null>(null);
  const [photoPreview, setPhotoPreview] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const writtenTodayFilterActive = writtenTodayOnly && !todayWrittenError;

  const loadPlants = useCallback(() => {
    if (!hydrated || !state.accessToken) return () => {};
    const accessToken = state.accessToken;
    const controller = new AbortController();
    setLoading(true);
    setError('');

    // "오늘 일지 안 쓴 것만 보기"는 항상 GROWING만 대상으로 하므로 그 상태를 서버 필터로 요청한다.
    const status = writtenTodayFilterActive ? 'GROWING' : filter === 'all' ? undefined : (filter as PlantStatus);

    getMyPlants({ accessToken, status, page, signal: controller.signal })
      .then((data) => {
        setPlants(data.content);
        setTotalPages(data.totalPages);
      })
      .catch((requestError) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return;
        setPlants([]);
        setTotalPages(0);
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
  }, [hydrated, state.accessToken, filter, writtenTodayFilterActive, page]);

  useEffect(() => loadPlants(), [loadPlants]);

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

  useEffect(() => {
    if (!hydrated || !state.accessToken) return;
    const accessToken = state.accessToken;
    const controller = new AbortController();
    setTodayWrittenError(false);

    getProfileIdsWrittenToday(accessToken, controller.signal)
      .then((ids) => setTodayWrittenIds(new Set(ids)))
      .catch((requestError) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return;
        // 조회 실패를 빈 Set으로 처리하면 "아무도 안 씀"이 돼서 필터를 켰을 때 재배중 전체가
        // 미작성으로 잘못 표시된다 — 대신 에러 상태를 따로 두고 필터 자체를 못 쓰게 막는다.
        setTodayWrittenError(true);
      });

    return () => controller.abort();
  }, [hydrated, state.accessToken]);

  // status 필터는 이제 서버에 요청하므로 plants는 이미 필터링된 현재 페이지 결과다.
  // "오늘 일지 안 쓴 것만 보기"만 그 위에 클라이언트에서 한 번 더 좁힌다(현재 페이지 범위 내에서).
  const list = writtenTodayFilterActive
    ? plants.filter((p) => !todayWrittenIds.has(p.id))
    : plants;

  // 필터로 화면에서 사라진 식물이 선택 상태로 계속 남아, 안 보이는 항목까지 일괄 변경 대상이
  // 되는 걸 막는다 — 보이는 목록(list)이 바뀌면 선택도 그 목록 기준으로 다시 걸러준다.
  useEffect(() => {
    const visibleIds = new Set(
      (writtenTodayFilterActive ? plants.filter((p) => !todayWrittenIds.has(p.id)) : plants).map((p) => p.id),
    );
    setSelectedIds((prev) => {
      const next = new Set([...prev].filter((id) => visibleIds.has(id)));
      return next.size === prev.size ? prev : next;
    });
  }, [writtenTodayFilterActive, plants, todayWrittenIds]);
  const regV = nickValid(reg.nick);
  const spResults = speciesList.filter((sp) => !query.trim() || sp.name.includes(query.trim()));

  const handleSpeciesQueryChange = (value: string) => {
    setQuery(value);
    const selected = speciesList.find((sp) => sp.id === reg.speciesId);
    if (selected && selected.name !== value) {
      setReg({ ...reg, speciesId: null });
    }
  };

  const toggleSelected = (plantId: number) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(plantId)) next.delete(plantId);
      else next.add(plantId);
      return next;
    });
  };

  const applyBulkStatus = (status: PlantStatus, statusLabel: string) => {
    if (!state.accessToken || selectedIds.size === 0) return;
    const accessToken = state.accessToken;
    const targetIds = Array.from(selectedIds);

    askConfirm({
      icon: 'check',
      title: `선택한 ${targetIds.length}개 식물을 '${statusLabel}'(으)로 변경할까요?`,
      ok: '변경',
      onOk: async () => {
        const results = await Promise.allSettled(
          targetIds.map((id) => updatePlant(id, { status }, accessToken)),
        );
        const succeededIds = new Set<number>();
        let failCount = 0;
        results.forEach((result, i) => {
          if (result.status === 'fulfilled') succeededIds.add(targetIds[i]);
          else failCount += 1;
        });
        // 상태 필터가 서버 쪽으로 넘어갔으므로, 현재 필터에 더 이상 맞지 않는 항목은
        // 재조회해야 화면에서 사라진다 — 로컬 상태만 바꿔서는 부정확하다. 이 변경으로 현재
        // 페이지의 항목이 전부 필터에서 빠지면 totalPages가 줄어 지금 page가 범위 밖이
        // 될 수 있으므로, 항상 0페이지로 돌아가 안전한 상태에서 다시 불러온다.
        if (succeededIds.size > 0) {
          if (page === 0) loadPlants();
          else setPage(0);
        }
        showToast(
          failCount === 0
            ? `${succeededIds.size}개 식물 상태를 변경했어요.`
            : `${succeededIds.size}개 변경 완료, ${failCount}개는 실패했어요.`,
          failCount === 0 ? 'ok' : 'err',
        );
        setSelectedIds(new Set());
        setSelectMode(false);
      },
    });
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
      // 새 식물은 항상 GROWING으로 생성되므로, 다른 상태 필터를 보고 있었다면 새로 생긴
      // 식물이 안 보일 수 있다 — 전체 필터·첫 페이지로 돌아가 방금 등록한 걸 바로 보여준다.
      // writtenTodayOnly가 아니라 writtenTodayFilterActive를 봐야 한다: 에러로 이미 무력화된
      // 상태(writtenTodayOnly=true, todayWrittenError=true)에서는 이 값이 이미 false라
      // setWrittenTodayOnly(false)를 호출해도 loadPlants의 deps가 안 바뀌어 effect가 재실행되지
      // 않는다 — 원시 상태로 판단하면 이 경우를 "이미 기본 화면"으로 놓쳐 재조회가 누락된다.
      const alreadyAtDefaultView = filter === 'all' && !writtenTodayFilterActive && page === 0;
      setFilter('all');
      setWrittenTodayOnly(false);
      setPage(0);
      if (alreadyAtDefaultView) loadPlants();
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

      <div className="mb-3 flex flex-wrap items-center justify-between gap-[9px]">
        <div className="flex flex-wrap items-center gap-[9px]">
          {FILTERS.map(([k, label]) => (
            <button
              key={k}
              type="button"
              onClick={() => {
                // 반대 방향도 마찬가지: 상태 pill을 직접 고르면 "오늘 일지 안 쓴 것만 보기"가
                // 켜져 있어도 그 상태를 그대로 보고 싶다는 뜻이므로, 항상 GROWING만 강제하던
                // 필터는 해제한다.
                setFilter(k);
                setWrittenTodayOnly(false);
                setPage(0);
              }}
              className={`cursor-pointer rounded-full border-[1.5px] px-4 py-2 text-sm font-bold ${
                filter === k ? 'border-brand bg-brand text-white' : 'border-line bg-white text-[#6d7a68]'
              }`}
            >
              {label}
            </button>
          ))}
        </div>
        <button
          type="button"
          onClick={() => {
            setSelectMode(!selectMode);
            setSelectedIds(new Set());
          }}
          className={`cursor-pointer rounded-full border-[1.5px] px-4 py-2 text-sm font-bold ${
            selectMode ? 'border-brand bg-brand text-white' : 'border-line bg-white text-[#6d7a68]'
          }`}
        >
          {selectMode ? '선택 취소' : '선택'}
        </button>
      </div>

      <div className="mb-[22px] flex flex-wrap items-center gap-2">
        <label
          className={`flex w-fit items-center gap-2 text-[13.5px] font-bold text-[#6d7a68] ${
            todayWrittenError ? 'cursor-not-allowed opacity-60' : 'cursor-pointer'
          }`}
        >
          <input
            type="checkbox"
            checked={writtenTodayOnly}
            disabled={todayWrittenError}
            onChange={(e) => {
              // 이 필터가 켜지면 상태 필터(재배중/수확완료/실패)와 무관하게 항상 GROWING만
              // 대상이 되므로, 상태 pill이 다른 걸 가리키고 있으면(예: '실패' 선택 중) 화면에
              // 실제로 뭘 보고 있는지 헷갈린다 — 필터를 켜는 순간 상태 pill도 '전체'로 맞춘다.
              setWrittenTodayOnly(e.target.checked);
              if (e.target.checked) setFilter('all');
              setPage(0);
            }}
            className="h-4 w-4 accent-brand"
          />
          오늘 일지 안 쓴 것만 보기
        </label>
        {todayWrittenError && (
          <span className="text-[12.5px] font-semibold text-danger">
            일지 작성 여부를 확인하지 못했어요. 잠시 후 다시 시도해 주세요.
          </span>
        )}
      </div>

      {loading ? (
        <div className="rounded-[22px] bg-white py-14 text-center text-[15px] text-sub">식물 목록을 불러오고 있어요 🌱</div>
      ) : error ? (
        <div className="rounded-[22px] bg-white px-5 py-14 text-center text-[15px] text-sub">{error}</div>
      ) : list.length === 0 && writtenTodayFilterActive ? (
        <div className="rounded-[22px] bg-white px-5 py-[70px] text-center shadow-card">
          <div className="animate-floaty text-[70px]">🌿</div>
          {/* 서버가 status=GROWING으로 페이징해 주므로 이 페이지 뒤에 더 있을 수 있다 —
              "안 쓴 식물이 없다"는 전체에 대한 단정이 아니라 이 페이지 한정임을 명시한다. */}
          <p className="mt-4 text-[17px] font-bold text-[#6d7a68]">
            {totalPages > 1 ? '이 페이지에는 오늘 일지 안 쓴 식물이 없어요 🌿' : '오늘 일지 안 쓴 식물이 없어요 🌿'}
          </p>
        </div>
      ) : list.length === 0 && filter !== 'all' ? (
        // 진짜로 식물이 하나도 없는 것과, 상태 필터에 맞는 게 지금 없는 것은 다른 상황이다 —
        // 후자는 "등록하기" 유도가 아니라 필터에 걸린 것뿐이라는 걸 알려줘야 한다.
        <div className="rounded-[22px] bg-white px-5 py-[70px] text-center shadow-card">
          <div className="animate-floaty text-[70px]">🌱</div>
          <p className="mt-4 text-[17px] font-bold text-[#6d7a68]">
            {FILTERS.find(([key]) => key === filter)?.[1]} 상태의 식물이 없어요.
          </p>
        </div>
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
            const selected = selectedIds.has(p.id);
            return (
              <Link
                key={p.id}
                href={`/plants/${p.id}`}
                onClick={(e) => {
                  if (!selectMode) return;
                  e.preventDefault();
                  toggleSelected(p.id);
                }}
                className={`relative block overflow-hidden rounded-[18px] bg-white text-ink shadow-card hover:text-ink ${
                  selectMode && selected ? 'ring-[3px] ring-brand' : ''
                }`}
              >
                {thumb.type === 'image' ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={resolveImageUrl(thumb.url)} alt="" className="h-[150px] w-full object-cover" />
                ) : (
                  <div className="flex h-[150px] items-center justify-center text-[72px]" style={{ background: thumb.grad }}>{thumb.emoji}</div>
                )}
                <div className="absolute left-3 top-3 rounded-full px-[11px] py-[5px] text-xs font-extrabold" style={{ background: b.bg, color: b.color }}>{b.label}</div>
                {selectMode && (
                  <div
                    className={`absolute right-3 top-3 flex h-6 w-6 items-center justify-center rounded-full border-2 text-sm font-extrabold ${
                      selected ? 'border-brand bg-brand text-white' : 'border-white bg-white/70 text-transparent'
                    }`}
                  >
                    ✓
                  </div>
                )}
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

      {/* "오늘 일지 안 쓴 것만 보기"도 서버가 status=GROWING으로 페이징해 주므로, 이 모드에서도
          페이저를 그대로 노출해야 뒤 페이지의 미작성 항목에 닿을 수 있다. */}
      {!loading && !error && totalPages > 1 && (
        <div className="mt-7 flex items-center justify-center gap-3">
          <button
            type="button"
            disabled={page === 0}
            onClick={() => setPage((current) => Math.max(0, current - 1))}
            className="cursor-pointer rounded-xl border border-line bg-white px-4 py-2 font-bold disabled:cursor-not-allowed disabled:opacity-40"
          >
            이전
          </button>
          <span className="text-sm font-bold text-sub">
            {page + 1} / {totalPages}
          </span>
          <button
            type="button"
            disabled={page + 1 >= totalPages}
            onClick={() => setPage((current) => current + 1)}
            className="cursor-pointer rounded-xl border border-line bg-white px-4 py-2 font-bold disabled:cursor-not-allowed disabled:opacity-40"
          >
            다음
          </button>
        </div>
      )}

      {!selectMode && (
        <button
          type="button"
          onClick={() => setOpen(true)}
          className="fixed bottom-[84px] right-6 z-30 cursor-pointer rounded-full bg-brand px-[22px] py-[15px] font-extrabold text-white shadow-[0_10px_26px_rgba(124,179,66,.45)]"
        >
          + 새 식물 등록
        </button>
      )}

      {selectMode && selectedIds.size > 0 && (
        <div className="fixed inset-x-0 bottom-0 z-30 flex items-center justify-between gap-3 bg-white px-5 py-4 shadow-[0_-6px_20px_rgba(0,0,0,.08)]">
          <span className="text-sm font-extrabold text-[#6d7a68]">{selectedIds.size}개 선택됨</span>
          <div className="flex flex-wrap gap-2">
            {BULK_STATUS_OPTIONS.map(([status, label]) => (
              <button
                key={status}
                type="button"
                onClick={() => applyBulkStatus(status, label)}
                className="cursor-pointer rounded-xl border-[1.5px] border-line bg-white px-3.5 py-2 text-[13px] font-bold text-[#6d7a68]"
              >
                {label}
              </button>
            ))}
          </div>
        </div>
      )}

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
