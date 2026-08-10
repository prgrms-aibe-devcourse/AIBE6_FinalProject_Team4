'use client';
import { ApiError, resolveImageUrl } from '@/lib/api';
import { formatDate } from '@/lib/format';
import { getJournals, PlantJournalData } from '@/lib/journal-api';
import { getMyPlants, PlantProfileData } from '@/lib/plant-api';
import { useStore } from '@/lib/store';
import Link from 'next/link';
import { useEffect, useRef, useState } from 'react';

function representativeImage(journal: PlantJournalData): string | null {
  const url = journal.images.find((img) => img.representative)?.imageUrl || journal.images[0]?.imageUrl || null;
  return url ? resolveImageUrl(url) : null;
}

const currentMonth = () => new Date().toISOString().slice(0, 7);

export default function JournalsPage() {
  const { state, hydrated } = useStore();
  const [plants, setPlants] = useState<PlantProfileData[]>([]);
  const [journals, setJournals] = useState<PlantJournalData[]>([]);
  const [selectedProfileIds, setSelectedProfileIds] = useState<number[]>([]); // [] = 전체
  const [filterModalOpen, setFilterModalOpen] = useState(false);
  const [monthFilter, setMonthFilter] = useState(currentMonth); // "" = 전체, else "YYYY-MM"
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const monthInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!hydrated || !state.accessToken) return;
    const accessToken = state.accessToken;
    const controller = new AbortController();
    setLoading(true);
    setError('');

    const [year, month] = monthFilter ? monthFilter.split('-').map(Number) : [undefined, undefined];

    Promise.all([
      getMyPlants({ accessToken, size: 100, signal: controller.signal }),
      getJournals({ year, month, size: 100 }, accessToken, controller.signal),
    ])
      .then(([plantPage, journalPage]) => {
        setPlants(plantPage.content);
        setJournals(journalPage.content);
      })
      .catch((requestError) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return;
        setJournals([]);
        setError(
          requestError instanceof ApiError
            ? requestError.message
            : '일지를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.',
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [hydrated, state.accessToken, monthFilter]);

  const toggleProfile = (profileId: number) => {
    setSelectedProfileIds((prev) =>
      prev.includes(profileId) ? prev.filter((id) => id !== profileId) : [...prev, profileId],
    );
  };

  const visibleJournals = selectedProfileIds.length === 0
    ? journals
    : journals.filter((j) => selectedProfileIds.includes(j.plantProfileId));

  // 실패한 식물은 더 이상 새 일지를 못 쓰므로(journals/new의 selectPlant 참고) 오늘 포인트 요약 대상에서 뺀다.
  const eligiblePlants = plants.filter((p) => p.status !== 'FAILED');
  const rewardedTodayCount = eligiblePlants.filter((p) => p.journalRewardGrantedToday).length;
  const todayRewardSummary =
    eligiblePlants.length === 0
      ? null
      : rewardedTodayCount === eligiblePlants.length
        ? '오늘 모든 식물의 포인트를 다 받았어요! 🎉'
        : rewardedTodayCount > 0
          ? `오늘 ${eligiblePlants.length}개 중 ${rewardedTodayCount}개 식물에서 포인트를 받았어요 ☀️`
          : '아직 오늘 포인트를 받은 식물이 없어요. 일지를 남겨보세요 🌱';

  return (
    <div className="container">
      <div className="mb-1.5 flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-[27px] font-extrabold">성장 일지</h1>
        <Link href="/journals/new" className="rounded-xl bg-brand px-5 py-3 text-[15px] font-bold text-white hover:text-white">+ 오늘의 일지 쓰기</Link>
      </div>
      <p className="mb-5 text-sub">매일 한 장씩 모여서 하나의 이야기가 돼요.</p>

      {todayRewardSummary && (
        <div className="mb-5 rounded-[14px] bg-brand-soft px-4 py-3 text-[13.5px] font-bold text-brand-dark">
          {todayRewardSummary}
        </div>
      )}

      <div className="mb-6 flex flex-wrap items-center gap-2.5">
        <button
          type="button"
          onClick={() => setSelectedProfileIds([])}
          className={`cursor-pointer rounded-full border-[1.5px] px-[15px] py-2 text-sm font-bold ${
            selectedProfileIds.length === 0 ? 'border-brand bg-brand text-white' : 'border-line bg-white text-[#6d7a68]'
          }`}
        >
          전체
        </button>
        <button
          type="button"
          onClick={() => setFilterModalOpen(true)}
          className={`flex cursor-pointer items-center gap-1.5 rounded-full border-[1.5px] px-[15px] py-2 text-sm font-bold ${
            selectedProfileIds.length > 0 ? 'border-brand bg-[#F3F8EA] text-brand-dark' : 'border-line bg-white text-[#6d7a68]'
          }`}
        >
          <span className="material-symbols-outlined text-base">tune</span>
          필터{selectedProfileIds.length > 0 ? ` (${selectedProfileIds.length})` : ''}
        </button>
        <div className="flex-1" />
        <div
          onClick={() => monthInputRef.current?.showPicker?.()}
          className="flex cursor-pointer select-none items-center gap-2 rounded-[11px] border-[1.5px] border-line bg-white px-[13px] py-2 text-sm font-bold text-[#6d7a68]"
        >
          <input
            ref={monthInputRef}
            type="month"
            value={monthFilter}
            onChange={(e) => setMonthFilter(e.target.value)}
            className="w-full cursor-pointer select-none bg-transparent outline-none pointer-events-none"
          />
        </div>
      </div>

      {loading ? (
        <div className="px-5 py-[60px] text-center text-sub">일지를 불러오고 있어요 🌿</div>
      ) : error ? (
        <div className="px-5 py-[60px] text-center text-sub">{error}</div>
      ) : visibleJournals.length === 0 ? (
        <div className="px-5 py-[60px] text-center text-sub">이 조건의 일지가 아직 없어요. 오늘의 기록을 남겨볼까요? 🌱</div>
      ) : (
        <div className="grid gap-[18px] [grid-template-columns:repeat(auto-fill,minmax(250px,1fr))]">
          {visibleJournals.map((j) => {
            const image = representativeImage(j);
            return (
              <Link
                key={j.id}
                href={`/journals/${j.id}`}
                className="block overflow-hidden rounded-[18px] bg-white text-ink shadow-card hover:text-ink"
              >
                <div className="relative flex h-[190px] items-center justify-center overflow-hidden bg-brand-soft text-[62px]">
                  {image ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={image} alt="" className="h-full w-full object-cover" />
                  ) : (
                    '🌿'
                  )}
                </div>
                <div className="p-3.5">
                  <div className="mb-[7px] flex items-center gap-1.5">
                    <span className="rounded-full bg-brand-soft px-[9px] py-[3px] text-xs font-extrabold text-brand-dark">{j.plantProfileNickname}</span>
                    <span className="text-xs text-faint">{formatDate(j.writtenDate)}</span>
                  </div>
                  <div className="text-sm leading-[1.55] text-[#4a5647]">{j.content.length > 48 ? j.content.slice(0, 48) + '…' : j.content}</div>
                </div>
              </Link>
            );
          })}
        </div>
      )}

      {filterModalOpen && (
        <div onClick={() => setFilterModalOpen(false)} className="fixed inset-0 z-[60] flex items-center justify-center bg-[rgba(46,54,42,.4)] p-5">
          <div onClick={(e) => e.stopPropagation()} className="w-full max-w-[420px] animate-pop rounded-[20px] bg-white p-6">
            <h3 className="mb-1 text-[19px] font-extrabold">식물 필터</h3>
            <p className="mb-4 text-[13.5px] text-sub">(중복 가능) 보고 싶은 식물을 선택하세요.</p>
            <div className="mb-5 flex flex-wrap gap-2">
              {plants.map((p) => (
                <button
                  key={p.id}
                  type="button"
                  onClick={() => toggleProfile(p.id)}
                  className={`cursor-pointer rounded-full border-[1.5px] px-[15px] py-2 text-sm font-bold ${
                    selectedProfileIds.includes(p.id) ? 'border-brand bg-brand text-white' : 'border-line bg-white text-[#6d7a68]'
                  }`}
                >
                  {p.nickname}
                </button>
              ))}
            </div>
            <div className="flex gap-2.5">
              <button
                type="button"
                onClick={() => setFilterModalOpen(false)}
                className="flex-1 cursor-pointer rounded-xl bg-brand p-[13px] font-extrabold text-white"
              >
                적용
              </button>
              <button
                type="button"
                onClick={() => setSelectedProfileIds([])}
                className="cursor-pointer rounded-xl border-[1.5px] border-line bg-white px-5 py-[13px] font-bold text-sub"
              >
                초기화
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
