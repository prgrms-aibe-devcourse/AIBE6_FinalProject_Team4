"use client";

import Link from "next/link";
import Image from "next/image";
import { useState } from "react";
import GachaPackStage from "@/components/gacha/GachaPackStage";
import GachaShuffleStage from "@/components/gacha/GachaShuffleStage";
import GachaRevealCard from "@/features/gacha/GachaRevealCard";
import { useGachaOpenDraw } from "@/features/gacha/use-gacha-open-draw";
import { useGachaOpenNavigation } from "@/features/gacha/use-gacha-open-navigation";
import { useStore } from "@/lib/store";

export default function GachaOpenPage({
  params,
  searchParams,
}: {
  params: { drawId: string };
  searchParams?: { returnTo?: string };
}) {
  const drawId = Number(params.drawId);
  const { state, hydrated } = useStore();
  const { moveBack, moveToCollection } = useGachaOpenNavigation(
    searchParams?.returnTo === "journals" ? "journals" : undefined,
  );
  const [muted, setMuted] = useState(false);
  const { detail, stage, setStage, revealedIndex, error, load, revealNext } =
    useGachaOpenDraw({
      drawId,
      accessToken: state.accessToken,
      hydrated,
    });

  const confirm = () => {
    moveToCollection();
  };

  if (!hydrated) {
    return (
      <div className="container py-20 text-center text-sub">불러오는 중...</div>
    );
  }

  if (!state.accessToken) {
    return (
      <div className="container py-20 text-center">
        <h1 className="text-2xl font-extrabold">로그인이 필요합니다</h1>
        <Link
          href="/auth?next=/gacha"
          className="mt-5 inline-block rounded-xl bg-brand px-5 py-3 font-bold text-white"
        >
          로그인하기
        </Link>
      </div>
    );
  }

  if (error) {
    return (
      <div className="container py-20 text-center">
        <p className="font-bold text-danger">{error}</p>
        <button
          type="button"
          onClick={() => void load()}
          className="mt-5 rounded-xl bg-brand px-5 py-3 font-bold text-white"
        >
          다시 시도
        </button>
      </div>
    );
  }

  if (!detail || stage === "loading") {
    return (
      <div className="container flex min-h-[65vh] flex-col items-center justify-center text-center">
        <div className="h-12 w-12 animate-spin rounded-full border-4 border-[#dfe6d8] border-t-brand" />
        <h1 className="mt-5 text-xl font-extrabold">
          카드 5장을 준비하고 있어요
        </h1>
        <p className="mt-2 text-sm text-sub">잠시만 기다려 주세요.</p>
      </div>
    );
  }

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-[#0d140f] text-white">
      <div className="pointer-events-none fixed inset-0 bg-[radial-gradient(circle_at_50%_15%,rgba(91,130,71,.32),transparent_38%),linear-gradient(180deg,rgba(255,255,255,.025),transparent_35%)]" />
      <div className="relative mx-auto flex min-h-full max-w-[1100px] flex-col px-4 py-5">
        {stage !== "summary" && (
          <div className="flex items-center justify-end">
            <button
              type="button"
              onClick={() => setMuted((value) => !value)}
              className="rounded-full bg-white/10 px-3 py-2 text-xs font-bold"
            >
              {muted ? "🔇 음소거" : "🔊 사운드"}
            </button>
          </div>
        )}

        {stage !== "summary" && (
          <button
            type="button"
            onClick={() => setStage("summary")}
            className="absolute right-4 top-16 rounded-full border border-white/15 bg-black/25 px-3 py-2 text-xs font-bold text-white/75 backdrop-blur transition hover:bg-white/10 hover:text-white"
          >
            연출 건너뛰기
          </button>
        )}

        <div className="flex flex-1 flex-col items-center justify-center py-12">
          {stage === "pack" && (
            <GachaPackStage muted={muted} onOpen={revealNext} />
          )}

          {stage === "shuffle" && (
            <GachaShuffleStage muted={muted} onComplete={revealNext} />
          )}

          {stage === "reveal" && (
            <GachaRevealCard
              key={detail.items[revealedIndex].sequence}
              item={detail.items[revealedIndex]}
              index={revealedIndex}
              total={detail.items.length}
              muted={muted}
              onNext={revealNext}
            />
          )}

          {stage === "summary" && (
            <div className="w-full">
              <div className="text-center">
                <p className="text-sm font-bold text-[#d7c266]">PACK RESULT</p>
                <h1 className="mt-1 text-3xl font-black">오늘 만난 카드</h1>
              </div>
              <div className="mx-auto mt-7 grid max-w-[900px] grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-5">
                {detail.items.map((item) => (
                  <div
                    key={item.sequence}
                    className="rounded-2xl border border-white/10 bg-white/[.07] p-2.5 backdrop-blur"
                  >
                    <div className="relative aspect-[1122/1402] overflow-hidden rounded-xl bg-black/20">
                      {item.imageUrl && (
                        <Image
                          src={item.imageUrl}
                          alt={item.name}
                          fill
                          className="object-contain"
                        />
                      )}
                      {item.new && (
                        <span className="absolute left-2 top-2 rounded-full bg-[#ffda52] px-2 py-1 text-[10px] font-black text-[#4e3a00]">
                          NEW
                        </span>
                      )}
                    </div>
                    <p className="mt-2 truncate text-sm font-extrabold">
                      {item.name}
                    </p>
                    <p className="mt-0.5 text-xs text-white/60">
                      현재 {item.ownedCountAfter}장
                    </p>
                    {item.nextMilestone && (
                      <p className="mt-1 text-[10px] text-[#dfca72]">
                        다음 {item.nextMilestone}장
                      </p>
                    )}
                  </div>
                ))}
              </div>
              <div className="mt-8 flex flex-wrap justify-center gap-3">
                <button
                  type="button"
                  onClick={() => void moveBack()}
                  className="rounded-xl border border-white/25 px-5 py-3 text-sm font-bold"
                >
                  뒤로가기
                </button>
                <button
                  type="button"
                  onClick={confirm}
                  className="rounded-xl bg-white px-7 py-3 text-sm font-black text-[#253822]"
                >
                  내 카드 보기
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
