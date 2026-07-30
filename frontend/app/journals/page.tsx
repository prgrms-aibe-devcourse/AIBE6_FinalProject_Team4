'use client';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useStore } from '@/lib/store';
import { ApiError } from '@/lib/api';
import { getMyPlants, PlantProfileData } from '@/lib/plant-api';
import { getJournals, PlantJournalData } from '@/lib/journal-api';
import { formatDate } from '@/lib/format';

const TILE_H = ['190px', '150px', '210px', '160px', '200px', '170px'];

function representativeImage(journal: PlantJournalData): string | null {
  return journal.images.find((img) => img.representative)?.imageUrl || journal.images[0]?.imageUrl || null;
}

export default function JournalsPage() {
  const { state, hydrated } = useStore();
  const [plants, setPlants] = useState<PlantProfileData[]>([]);
  const [journals, setJournals] = useState<PlantJournalData[]>([]);
  const [filter, setFilter] = useState('all');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!hydrated || !state.accessToken) return;
    const accessToken = state.accessToken;
    const controller = new AbortController();
    setLoading(true);
    setError('');

    Promise.all([
      getMyPlants(accessToken, controller.signal),
      getJournals(
        { profileId: filter === 'all' ? undefined : Number(filter), size: 100 },
        accessToken,
        controller.signal,
      ),
    ])
      .then(([plantList, journalPage]) => {
        setPlants(plantList);
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
  }, [hydrated, state.accessToken, filter]);

  const filters = [['all', '전체'], ...plants.map((p) => [String(p.id), p.nickname])];

  return (
    <div className="container">
      <div className="mb-1.5 flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-[27px] font-extrabold">성장 일지</h1>
        <Link href="/journals/new" className="rounded-xl bg-brand px-5 py-3 text-[15px] font-bold text-white hover:text-white">+ 오늘의 일지 쓰기</Link>
      </div>
      <p className="mb-5 text-sub">한 장 한 장이 모여 이 아이의 이야기가 돼요.</p>

      <div className="mb-6 flex flex-wrap items-center gap-2.5">
        {filters.map(([k, label]) => (
          <button
            key={k}
            type="button"
            onClick={() => setFilter(k)}
            className={`cursor-pointer rounded-full border-[1.5px] px-[15px] py-2 text-sm font-bold ${
              filter === k ? 'border-brand bg-brand text-white' : 'border-line bg-white text-[#6d7a68]'
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {loading ? (
        <div className="px-5 py-[60px] text-center text-sub">일지를 불러오고 있어요 🌿</div>
      ) : error ? (
        <div className="px-5 py-[60px] text-center text-sub">{error}</div>
      ) : journals.length === 0 ? (
        <div className="px-5 py-[60px] text-center text-sub">이 조건의 일지가 아직 없어요. 오늘의 기록을 남겨볼까요? 🌱</div>
      ) : (
        <div className="gap-[18px] [column-gap:18px] [columns:auto_250px]">
          {journals.map((j, i) => {
            const image = representativeImage(j);
            return (
              <Link
                key={j.id}
                href={`/journals/${j.id}`}
                className="mb-[18px] block overflow-hidden rounded-[18px] bg-white text-ink shadow-card [break-inside:avoid] hover:text-ink"
              >
                <div
                  className="relative flex items-center justify-center overflow-hidden bg-brand-soft text-[62px]"
                  style={{ height: TILE_H[i % TILE_H.length] }}
                >
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
    </div>
  );
}
