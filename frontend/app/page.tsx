'use client';
import { useEffect, useState } from 'react';
import Image from 'next/image';
import Link from 'next/link';
import { useStore, fmt } from '@/lib/store';
import { getMyPlants, PlantProfileData } from '@/lib/plant-api';
import { getJournals } from '@/lib/journal-api';
import { resolveImageUrl } from '@/lib/api';
import { dPlus, plantThumbnail, plantVisual, SEEDLING_ICON_SRC } from '@/lib/plant-visual';

const CONFETTI = [
  { left: '8%', dur: '1.4s', delay: '0s', icon: 'leaf' }, { left: '26%', dur: '1.7s', delay: '.2s', icon: 'sparkle' },
  { left: '46%', dur: '1.3s', delay: '.1s', icon: 'watermelon' }, { left: '64%', dur: '1.8s', delay: '.35s', icon: 'seedling' },
  { left: '82%', dur: '1.5s', delay: '.15s', icon: 'sparkle' }, { left: '92%', dur: '1.6s', delay: '.28s', icon: 'heart' },
];

const FEATURES = [
  { icon: 'seedling', title: '매일 기록하기', desc: '식물의 성장을 사진과 함께 일지로 남겨요' },
  { icon: 'sun', title: '포인트 쌓기', desc: '기록할 때마다 포인트가 차곡차곡 쌓여요' },
  {
    image: {
      src: 'https://4team-storage-495264909330-ap-northeast-2-an.s3.ap-northeast-2.amazonaws.com/cards/8/399f7998-3e24-5d1d-b69c-68da63b839ef.png',
      alt: '상추 쿠폰',
    },
    title: '가챠 카드 뽑기',
    desc: <><span>쌓인 포인트로 카드팩을 열고</span><br /><span>특별한 카드를 모아보세요</span></>,
  },
  { icon: 'watermelon', title: '진짜 열매 받기', desc: '모은 쿠폰을 진짜 과일·채소로 교환해요' },
];

function kstToday(): string {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date());
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${values.year}-${values.month}-${values.day}`;
}

export default function Home() {
  const { state, hydrated, balance } = useStore();
  const [plants, setPlants] = useState<PlantProfileData[]>([]);
  const [plantsLoading, setPlantsLoading] = useState(true);
  const [plantsError, setPlantsError] = useState('');
  const [wroteToday, setWroteToday] = useState(false);
  // 대표사진 URL은 있지만 실제 로드가 실패한(삭제됨 등) 식물 id — 이모지로 대신 보여준다.
  const [brokenThumbIds, setBrokenThumbIds] = useState<Set<number>>(new Set());

  useEffect(() => {
    if (!hydrated || !state.accessToken) return;
    const accessToken = state.accessToken;
    const controller = new AbortController();
    const today = kstToday();
    const [year, month] = today.split('-').map(Number);

    // 미리보기는 "오늘 돌봐야 할 식물"을 보여주는 게 목적이라 재배중인 것만 노출한다 —
    // 수확완료/실패 개수는 위 "내 식물 현황" 카드에서 이미 따로 보여준다.
    setPlantsLoading(true);
    setPlantsError('');
    getMyPlants({ accessToken, status: 'GROWING', signal: controller.signal })
      .then((plantPage) => {
        setPlants(plantPage.content.slice(0, 4));
      })
      .catch((requestError) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return;
        setPlants([]);
        // 조회 실패를 빈 배열로 처리하면 "재배중인 식물이 없어요"로 보여, 실제로는 있는데
        // 없다고 오해할 수 있다 — 로딩/에러 상태를 따로 두고 성공했을 때만 빈 상태 UI를 보여준다.
        setPlantsError('식물 목록을 불러오지 못했어요.');
      })
      .finally(() => {
        if (!controller.signal.aborted) setPlantsLoading(false);
      });

    getJournals({ year, month, size: 1 }, accessToken, controller.signal)
      .then((journalPage) => {
        setWroteToday(journalPage.content.some((journal) => journal.writtenDate === today));
      })
      .catch((requestError) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return;
        setWroteToday(false);
      });

    return () => controller.abort();
  }, [hydrated, state.accessToken]);

  if (!state.authed) {
    return (
      <div className="container animate-upIn">
        <div className="grid items-center gap-11 py-12 md:py-16 [grid-template-columns:repeat(auto-fit,minmax(300px,1fr))]">
          <div className="text-center md:text-left">
            <h1 className="mb-4 text-[34px] font-extrabold leading-[1.3] md:text-[42px] md:leading-[1.25]">
              식물을 키우고,<br />기록하고,<br /><span className="text-brand">진짜 열매</span>를 받아보세요
            </h1>
            <p className="mb-7 text-base leading-[1.6] text-sub md:text-[17px]">
              매일의 성장을 기록하면 포인트가 쌓이고,<br />쿠폰을 모으면 진짜 과일·채소로 바꿔드려요.
            </p>
            <div className="flex flex-wrap justify-center gap-3 md:justify-start">
              <Link href="/auth?view=signup" className="rounded-[14px] bg-brand px-7 py-[15px] text-base font-bold text-white shadow-[0_6px_18px_rgba(124,179,66,.35)] transition-colors duration-150 hover:bg-brand-dark hover:text-white">
                회원가입하고 시작하기
              </Link>
              <Link href="/auth?view=login" className="rounded-[14px] border-[1.5px] border-[#cfe0b6] bg-white px-7 py-[15px] text-base font-bold text-brand-dark transition-colors duration-150 hover:bg-brand-soft">
                로그인
              </Link>
            </div>
          </div>
          <div className="flex justify-center">
            <div className="relative flex h-[300px] w-[280px] items-center justify-center rounded-[28px] bg-gradient-to-b from-[#F3F7E9] to-[#E4EFCF] shadow-[0_20px_50px_rgba(124,179,66,.18)]">
              <img src="/icons/potted-plant.svg" alt="" className="animate-floaty h-[130px] w-[130px]" />
              <img src="/icons/sun.svg" alt="" className="absolute right-[30px] top-[26px] h-[40px] w-[40px]" />
              <img src="/icons/seedling.svg" alt="" className="absolute bottom-7 left-[26px] h-[30px] w-[30px]" />
            </div>
          </div>
        </div>

        <div className="grid gap-4 border-t border-line pb-8 pt-10 [grid-template-columns:repeat(auto-fit,minmax(200px,1fr))]">
          {FEATURES.map((f) => (
            <div key={f.title} className="rounded-[18px] bg-white p-5 text-center shadow-card">
              <div className="flex h-[51px] items-center justify-center text-[34px]">
                {f.image ? (
                  <Image
                    src={f.image.src}
                    alt={f.image.alt}
                    width={27}
                    height={34}
                    className="h-[34px] w-auto rounded-[2px] object-contain shadow-[0_2px_5px_rgba(120,82,12,.22)]"
                  />
                ) : (
                  <img src={`/icons/${f.icon}.svg`} alt="" className="h-[34px] w-[34px]" />
                )}
              </div>
              <div className="mt-2 font-extrabold">{f.title}</div>
              <div className="mt-1 text-[13px] text-sub">{f.desc}</div>
            </div>
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="container animate-upIn">
      <h1 className="mb-1 text-[27px] font-extrabold">안녕하세요, {state.user?.nickname}님! 오늘도 푸릇한 하루예요</h1>
      <p className="mb-6 text-sub">작은 기록이 모여 큰 수확이 돼요.</p>

      {state.readyCards > 0 && (
        <div className="relative mb-6 flex flex-wrap items-center gap-4 overflow-hidden rounded-[20px] bg-gradient-to-br from-[#FFE9A6] to-[#FFD54F] px-6 py-[22px] shadow-[0_8px_24px_rgba(255,213,79,.3)]">
          <img src="/icons/watermelon.svg" alt="" className="h-[46px] w-[46px]" />
          <div className="min-w-[200px] flex-1">
            <div className="text-lg font-extrabold text-[#6b5500]">교환 가능한 쿠폰이 {state.readyCards}종 있어요!</div>
            <div className="text-[14.5px] text-gold-text">진짜 열매로 바꿔볼까요?</div>
          </div>
          <Link href="/cards" className="flex items-center gap-1 rounded-xl bg-ink px-5 py-3 font-bold text-white transition-colors duration-150 hover:bg-[#2a332a] hover:text-white">교환하러 가기 <img src="/icons/party.svg" alt="" className="h-7 w-7" /></Link>
          {CONFETTI.map((c, i) => (
            <img
              key={i}
              src={`/icons/${c.icon}.svg`}
              alt=""
              className="absolute -top-2.5 h-7 w-7 animate-confettiFall"
              style={{ left: c.left, animationDuration: c.dur, animationDelay: c.delay, animationIterationCount: 'infinite' }}
            />
          ))}
        </div>
      )}

      <div className="mb-[30px] grid gap-4 [grid-template-columns:repeat(auto-fit,minmax(240px,1fr))]">
        <div className="rounded-[18px] bg-white p-[22px] shadow-card">
          <div className="text-[13px] font-bold text-sub">내 포인트</div>
          <div className="mb-0.5 mt-2 text-[31px] font-extrabold">{fmt(balance)}<span className="text-base text-faint">P</span></div>
          <Link href="/my/points/charge" className="mt-2.5 inline-block rounded-[10px] bg-gold-soft px-4 py-[9px] font-bold text-gold-text transition-colors duration-150 hover:bg-gold hover:text-gold-text">충전하기</Link>
        </div>

        <div className="rounded-[18px] bg-white p-[22px] shadow-card">
          <div className="text-[13px] font-bold text-sub">오늘의 일지</div>
          {wroteToday ? (
            <>
              <div className="my-3.5 text-[19px] font-extrabold text-brand">오늘 기록 완료 ✓</div>
              <Link href="/journals" className="inline-block rounded-[10px] bg-brand-soft px-4 py-[9px] font-bold text-brand-dark transition-colors duration-150 hover:bg-brand hover:text-white">일지 보기</Link>
            </>
          ) : (
            <>
              <div className="my-3 text-[15px] font-bold text-[#6d7a68]">오늘의 기록을 남기면<br />포인트를 드려요!</div>
              <Link href="/journals/new" className="inline-block rounded-[10px] bg-brand px-4 py-[9px] font-bold text-white transition-colors duration-150 hover:bg-brand-dark hover:text-white">오늘의 일지 쓰기</Link>
            </>
          )}
        </div>

        <div className="rounded-[18px] bg-white p-[22px] shadow-card">
          <div className="text-[13px] font-bold text-sub">내 식물 현황</div>
          <div className="mb-0.5 mt-2 text-[31px] font-extrabold">
            {state.growingCount} / {state.harvestedCount} / {state.failedCount}
          </div>
          <div className="mb-1.5 text-[11.5px] text-faint">재배중 · 수확완료 · 실패</div>
          <Link href="/plants" className="mt-1 inline-block rounded-[10px] bg-brand-soft px-4 py-[9px] font-bold text-brand-dark transition-colors duration-150 hover:bg-brand hover:text-white">내 식물 보기</Link>
        </div>
      </div>

      <div className="mb-3.5 flex items-center justify-between">
        <h2 className="text-xl font-extrabold">재배중인 식물</h2>
        <Link href="/plants" className="text-sm font-bold text-brand-dark">전체보기 →</Link>
      </div>
      {plantsLoading ? (
        <div className="rounded-[22px] bg-white py-14 text-center text-[15px] text-sub">식물 목록을 불러오고 있어요</div>
      ) : plantsError ? (
        <div className="rounded-[22px] bg-white px-5 py-14 text-center text-[15px] text-sub">{plantsError}</div>
      ) : plants.length === 0 ? (
        <div className="rounded-[22px] bg-white px-5 py-[50px] text-center shadow-card">
          <img src="/icons/seedling.svg" alt="" className="animate-floaty mx-auto h-[56px] w-[56px]" />
          {state.plantCount > 0 ? (
            <p className="mb-5 mt-3 text-[15px] font-bold text-[#6d7a68]">지금 재배중인 식물이 없어요.<br />새로운 식물을 시작해 볼까요?</p>
          ) : (
            <p className="mb-5 mt-3 text-[15px] font-bold text-[#6d7a68]">아직 함께하는 식물이 없네요.<br />첫 반려식물을 등록해 볼까요?</p>
          )}
          <Link href="/plants" className="inline-block rounded-xl bg-brand px-[22px] py-[11px] font-bold text-white hover:text-white">+ 새 식물 등록</Link>
        </div>
      ) : (
        <div className="grid gap-4 [grid-template-columns:repeat(auto-fill,minmax(200px,1fr))]">
          {plants.map((p) => {
            const thumb = plantThumbnail(p.thumbnailUrl, p.speciesName);
            const visual = plantVisual(p.speciesName);
            return (
              <Link key={p.id} href={`/plants/${p.id}`} className="block overflow-hidden rounded-[18px] bg-white text-ink shadow-card hover:text-ink">
                {thumb.type === 'image' && !brokenThumbIds.has(p.id) ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img
                    src={resolveImageUrl(thumb.url)}
                    alt=""
                    className="h-[120px] w-full object-cover"
                    onError={() => setBrokenThumbIds((prev) => new Set(prev).add(p.id))}
                  />
                ) : (
                  <div
                    className="flex h-[120px] items-center justify-center"
                    style={{ background: thumb.type === 'emoji' ? thumb.grad : visual.grad }}
                  >
                    {(thumb.type === 'emoji' ? thumb.icon : visual.icon) === 'spa' ? (
                      <img src={SEEDLING_ICON_SRC} alt="" className="h-[60px] w-[60px]" />
                    ) : (
                      <span className="material-symbols-outlined text-[60px]">
                        {thumb.type === 'emoji' ? thumb.icon : visual.icon}
                      </span>
                    )}
                  </div>
                )}
                <div className="p-3.5">
                  <div className="font-extrabold">{p.nickname}</div>
                  <div className="mt-0.5 text-[13px] text-sub">{p.speciesName} · D+{dPlus(p.startDate)}</div>
                </div>
              </Link>
            );
          })}
        </div>
      )}
    </div>
  );
}
