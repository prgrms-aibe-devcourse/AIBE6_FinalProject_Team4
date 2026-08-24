"use client";

import Link from "next/link";
import { useState } from "react";
import GachaBatchResultGrid from "@/components/gacha/GachaBatchResultGrid";
import GachaPackStage from "@/components/gacha/GachaPackStage";
import GachaShuffleStage from "@/components/gacha/GachaShuffleStage";
import { removeGachaBatch } from "@/features/gacha/batch-session";
import { useGachaBatchOpen } from "@/features/gacha/use-gacha-batch-open";
import { useGachaOpenNavigation } from "@/features/gacha/use-gacha-open-navigation";
import { useStore } from "@/lib/store";

export default function GachaBatchOpenPage({
  params,
}: {
  params: { batchKey: string };
}) {
  const { state, hydrated } = useStore();
  const { moveBack, moveToCollection } = useGachaOpenNavigation();
  const [muted, setMuted] = useState(false);
  const {
    drawIds,
    stage,
    setStage,
    error,
    groupedResults,
    completedCount,
    totalCardCount,
    showSummary,
    retry,
  } = useGachaBatchOpen({
    batchKey: params.batchKey,
    accessToken: state.accessToken,
    hydrated,
    muted,
  });

  const confirm = () => {
    removeGachaBatch(params.batchKey);
    moveToCollection();
  };

  const goBack = async () => {
    removeGachaBatch(params.batchKey);
    await moveBack();
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
        {drawIds && (
          <button
            type="button"
            onClick={retry}
            className="mt-5 rounded-xl bg-brand px-5 py-3 font-bold text-white"
          >
            다시 시도
          </button>
        )}
      </div>
    );
  }

  if (!drawIds || stage === "loading") {
    return (
      <div className="container flex min-h-[65vh] flex-col items-center justify-center text-center">
        <div className="h-12 w-12 animate-spin rounded-full border-4 border-[#dfe6d8] border-t-brand" />
        <h1 className="mt-5 text-xl font-extrabold">
          {drawIds?.length ?? 0}팩을 열고 있어요
        </h1>
        <p className="mt-2 text-sm text-sub">
          {completedCount}/{drawIds?.length ?? 0}팩 준비 완료
        </p>
      </div>
    );
  }

  return (
    <main className="relative min-h-screen overflow-x-hidden overflow-y-auto bg-[#0d140f] text-white">
      <div className="pointer-events-none fixed inset-0 bg-[radial-gradient(circle_at_50%_12%,rgba(116,145,76,.32),transparent_38%),linear-gradient(180deg,rgba(255,255,255,.025),transparent_35%)]" />
      <div className="relative mx-auto flex min-h-screen max-w-[1180px] flex-col px-4 py-5">
        {stage !== "summary" && (
          <div className="flex items-center justify-end gap-2">
            <button
              type="button"
              onClick={() => setMuted((value) => !value)}
              className="flex items-center gap-1 rounded-full bg-white/10 px-3 py-2 text-xs font-bold"
            >
              <span className="material-symbols-outlined text-sm">{muted ? "volume_off" : "volume_up"}</span>
              {muted ? "음소거" : "사운드"}
            </button>
            <button
              type="button"
              onClick={() => setStage("summary")}
              className="rounded-full border border-white/15 bg-black/25 px-3 py-2 text-xs font-bold text-white/75 backdrop-blur transition hover:bg-white/10 hover:text-white"
            >
              연출 건너뛰기
            </button>
          </div>
        )}

        <div className="flex flex-1 flex-col items-center justify-center py-10">
          {stage === "pack" && (
            <GachaPackStage
              muted={muted}
              packCount={drawIds.length}
              onOpen={() => setStage("shuffle")}
            />
          )}

          {stage === "shuffle" && (
            <GachaShuffleStage
              muted={muted}
              packCount={drawIds.length}
              onComplete={showSummary}
              completeLabel="전체 결과 확인하기"
            />
          )}

          {stage === "summary" && (
            <section
              className="w-full py-4"
              aria-labelledby="batch-result-title"
            >
              <div className="text-center">
                <p className="text-sm font-bold tracking-[0.2em] text-[#d7c266]">
                  MULTI PACK RESULT
                </p>
                <h1
                  id="batch-result-title"
                  className="mt-2 text-3xl font-black sm:text-4xl"
                >
                  {drawIds.length}팩 개봉 완료
                </h1>
                <p className="mt-2 text-sm text-white/60">
                  총 {totalCardCount}장 · {groupedResults.length}종 · 높은
                  등급순
                </p>
              </div>

              <GachaBatchResultGrid results={groupedResults} />

              <div className="mt-10 flex flex-wrap justify-center gap-3">
                <button
                  type="button"
                  onClick={() => void goBack()}
                  className="rounded-full border border-white/20 px-8 py-3.5 font-black text-white/80 transition hover:-translate-y-0.5 hover:bg-white/10"
                >
                  뒤로가기
                </button>
                <button
                  type="button"
                  onClick={confirm}
                  className="rounded-full bg-white px-8 py-3.5 font-black text-[#253822] shadow-lg transition hover:-translate-y-0.5"
                >
                  내 카드 보기
                </button>
              </div>
            </section>
          )}
        </div>
      </div>
    </main>
  );
}
