"use client";

import { GachaMyCosmetics } from "@/lib/gacha-api";

export type GachaWorkshopSection = "menu" | "dismantle" | "cosmetics";

export default function GachaWorkshopOverview({
  section,
  onSectionChange,
  data,
  onBack,
}: {
  section: GachaWorkshopSection;
  onSectionChange: (section: GachaWorkshopSection) => void;
  data: GachaMyCosmetics | null;
  onBack?: () => void;
}) {
  const setSection = onSectionChange;
  return (
    <>
      <div className="rounded-[30px] border border-[#527565] bg-gradient-to-br from-[#285646] via-[#3c6955] to-[#73703a] p-6 text-white shadow-[0_22px_55px_-32px_rgba(38,82,65,.85)] sm:p-8">
        {onBack ? (
          <button
            type="button"
            onClick={onBack}
            className="mb-6 inline-flex items-center gap-2 rounded-full bg-white px-3.5 py-2 text-sm font-black text-[#285646] shadow-sm transition hover:bg-[#fff4c9]"
          >
            <svg
              aria-hidden="true"
              viewBox="0 0 20 20"
              className="h-4 w-4"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="m12.5 4.5-5.5 5.5 5.5 5.5" />
              <path d="M7.5 10H17" />
            </svg>
            내 카드 갤러리
          </button>
        ) : null}
        <p className="text-xs font-black uppercase tracking-[0.22em] text-[#f2d675]">
          Collector atelier
        </p>
        <h2
          id="workshop-title"
          className="mt-2 text-3xl font-black tracking-[-0.04em] text-white"
        >
          컬렉터 아틀리에
        </h2>
        <p className="mt-2 max-w-2xl text-sm font-medium leading-6 text-[#eff5ef]">
          중복 카드를 조각으로 바꾸고, 컬렉션에 특별한 빛을 더해보세요. 카드별
          한 장은 언제나 남습니다.
        </p>
      </div>

      <div
        aria-label={`보유 카드 조각 ${data?.shards.balance ?? 0}개`}
        className="relative flex flex-wrap items-center gap-4 overflow-hidden rounded-[24px] border border-[#e4cf8b] bg-[#fff8e3] px-5 py-5 shadow-sm sm:flex-nowrap sm:px-6"
      >
        <div className="pointer-events-none absolute right-0 top-0 h-full w-40 bg-gradient-to-l from-[#fff7d8] to-transparent" />
        <div className="relative flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl border border-[#ebd178] bg-[#fff8dc]">
          <span className="absolute inset-2 rounded-full bg-[#f4ce4d]/25 blur-md motion-safe:animate-pulse" />
          <span className="relative text-[30px] leading-none text-[#d19c18] drop-shadow-[0_0_7px_rgba(222,176,44,.45)]">
            ✦
          </span>
          <span className="absolute right-1.5 top-1.5 text-[7px] text-[#8e6915] motion-safe:animate-pulse">
            ✦
          </span>
        </div>

        <div className="relative min-w-0 flex-1">
          <p className="text-xs font-black text-[#617064]">현재 보유 조각</p>
          <div className="mt-0.5 flex items-baseline gap-1.5">
            <strong className="text-3xl font-black tabular-nums text-ink">
              {(data?.shards.balance ?? 0).toLocaleString("ko-KR")}
            </strong>
            <span className="text-sm font-black text-brand">개</span>
          </div>
        </div>

        <div className="relative ml-auto border-l border-[#e2e8df] pl-5 text-right">
          <p className="text-[11px] font-bold text-sub">누적 획득</p>
          <p className="mt-0.5 text-sm font-black tabular-nums text-[#75601d]">
            {(data?.shards.lifetimeEarned ?? 0).toLocaleString("ko-KR")}개
          </p>
        </div>
      </div>

      {section === "menu" ? (
        <div className="space-y-5">
          <div className="flex items-end justify-between gap-3 px-1">
            <div>
              <p className="text-xs font-black uppercase tracking-[0.18em] text-[#8b6b16]">
                Select atelier
              </p>
              <h3 className="mt-1 text-lg font-black">작업을 선택하세요</h3>
            </div>
            <span className="hidden text-xs font-bold text-sub sm:block">
              선택한 작업 화면으로 바로 이동합니다
            </span>
          </div>
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2">
            <button
              type="button"
              onClick={() => setSection("dismantle")}
              className="group relative flex min-h-56 cursor-pointer flex-col items-start rounded-[28px] border border-[#c7d9c3] bg-[#edf5ea] p-6 text-left text-ink shadow-sm transition-all hover:-translate-y-1 hover:border-[#6f9474] hover:shadow-lg"
            >
              <span className="pointer-events-none absolute right-6 top-5 text-xs font-black tracking-[0.18em] text-[#718576]">
                01
              </span>
              <span
                aria-hidden="true"
                className="pointer-events-none flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-white text-[#315f3e] shadow-sm transition-colors group-hover:bg-[#315f3e] group-hover:text-white"
              >
                <svg
                  aria-hidden="true"
                  viewBox="0 0 24 24"
                  className="h-7 w-7"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <path d="m7 19-3-3 3-3" />
                  <path d="M4 16h11a4 4 0 0 0 4-4" />
                  <path d="m17 5 3 3-3 3" />
                  <path d="M20 8H9a4 4 0 0 0-4 4" />
                </svg>
              </span>
              <span className="pointer-events-none mt-5 min-w-0 flex-1">
                <span className="block text-xl font-black">카드 분해</span>
                <span className="mt-2 block text-sm leading-6 text-[#5d6d60]">
                  중복 카드를 조각으로 바꾸기
                </span>
              </span>
              <span className="pointer-events-none mt-5 shrink-0 rounded-full bg-[#315f3e] px-4 py-2 text-xs font-black text-white">
                시작하기 →
              </span>
            </button>
            <button
              type="button"
              onClick={() => setSection("cosmetics")}
              className="group relative flex min-h-56 cursor-pointer flex-col items-start rounded-[28px] border border-[#e6d18c] bg-[#fff5d8] p-6 text-left text-ink shadow-sm transition-all hover:-translate-y-1 hover:border-[#b69a47] hover:shadow-lg"
            >
              <span className="pointer-events-none absolute right-6 top-5 text-xs font-black tracking-[0.18em] text-[#93772a]">
                02
              </span>
              <span
                aria-hidden="true"
                className="pointer-events-none flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-white text-[#8b6b16] shadow-sm transition-colors group-hover:bg-[#8b6b16] group-hover:text-white"
              >
                <svg
                  aria-hidden="true"
                  viewBox="0 0 24 24"
                  className="h-7 w-7"
                  fill="currentColor"
                >
                  <path d="M12 2.5c.7 4.1 2.9 6.3 7 7-4.1.7-6.3 2.9-7 7-.7-4.1-2.9-6.3-7-7 4.1-.7 6.3-2.9 7-7Z" />
                  <path d="M19 15.5c.3 1.8 1.2 2.7 3 3-1.8.3-2.7 1.2-3 3-.3-1.8-1.2-2.7-3-3 1.8-.3 2.7-1.2 3-3Z" />
                </svg>
              </span>
              <span className="pointer-events-none mt-5 min-w-0 flex-1">
                <span className="block text-xl font-black">이펙트 상점</span>
                <span className="mt-2 block text-sm leading-6 text-[#75683f]">
                  칭호와 프로필 테두리 미리보기·해금
                </span>
              </span>
              <span className="pointer-events-none mt-5 shrink-0 rounded-full bg-[#8b6b16] px-4 py-2 text-xs font-black text-white">
                입장하기 →
              </span>
            </button>
          </div>
        </div>
      ) : (
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-[#d8e1d5] px-1 pb-5">
          <div className="flex flex-wrap gap-2">
            {onBack ? (
              <button
                type="button"
                onClick={onBack}
                className="inline-flex items-center gap-1.5 rounded-full border border-[#c7d9c3] bg-white px-4 py-2.5 text-sm font-black text-[#315f3e] transition hover:bg-[#f1f6ee]"
              >
                ← 내 카드 갤러리
              </button>
            ) : null}
            <button
              type="button"
              aria-label="작업 선택으로 돌아가기"
              onClick={() => setSection("menu")}
              className="inline-flex items-center gap-1.5 rounded-full bg-[#e7efe3] px-4 py-2.5 text-sm font-black text-[#315f3e] transition hover:bg-[#d9e8d5]"
            >
              ← 작업 선택
            </button>
          </div>
          <p className="text-sm font-black text-[#68766b]">
            현재 작업 · {section === "dismantle" ? "카드 분해" : "이펙트 상점"}
          </p>
        </div>
      )}
    </>
  );
}
