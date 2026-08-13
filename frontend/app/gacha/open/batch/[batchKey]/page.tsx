"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type MouseEvent,
} from "react";
import GachaBatchResultGrid from "@/components/gacha/GachaBatchResultGrid";
import GachaPackStage from "@/components/gacha/GachaPackStage";
import GachaShuffleStage from "@/components/gacha/GachaShuffleStage";
import { playRarityRevealSound } from "@/features/gacha/audio";
import {
  loadGachaBatch,
  removeGachaBatch,
} from "@/features/gacha/batch-session";
import { groupGachaDrawResults } from "@/features/gacha/result";
import { usePreventBackNavigation } from "@/features/gacha/use-prevent-back-navigation";
import { ApiError } from "@/lib/api";
import {
  GachaDrawDetail,
  GachaRarity,
  getGachaDraw,
  markGachaDrawViewed,
} from "@/lib/gacha-api";
import { useStore } from "@/lib/store";

const REQUEST_CHUNK_SIZE = 10;
const RARITY_ORDER: Record<GachaRarity, number> = {
  COMMON: 1,
  RARE: 2,
  SUPER_RARE: 3,
  HYPER_RARE: 4,
  GOLDEN_RARE: 5,
};

type Stage = "loading" | "pack" | "shuffle" | "summary";

async function mapInChunks<T, R>(
  values: T[],
  mapper: (value: T) => Promise<R>,
): Promise<R[]> {
  const results: R[] = [];
  for (let index = 0; index < values.length; index += REQUEST_CHUNK_SIZE) {
    const chunk = values.slice(index, index + REQUEST_CHUNK_SIZE);
    results.push(...(await Promise.all(chunk.map(mapper))));
  }
  return results;
}

export default function GachaBatchOpenPage({
  params,
}: {
  params: { batchKey: string };
}) {
  const router = useRouter();
  const { state, hydrated, refreshNotifications } = useStore();
  const [drawIds, setDrawIds] = useState<number[] | null>(null);
  const [details, setDetails] = useState<GachaDrawDetail[]>([]);
  const [stage, setStage] = useState<Stage>("loading");
  const [error, setError] = useState("");
  const [muted, setMuted] = useState(false);

  const moveToJournals = async (event: MouseEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    await refreshNotifications();
    router.push("/journals");
  };

  useEffect(() => {
    if (!hydrated) return;
    const storedDrawIds = loadGachaBatch(params.batchKey);
    if (!storedDrawIds) {
      setError(
        "다중 팩 개봉 정보를 찾을 수 없습니다. 생성한 브라우저 탭에서 다시 시도해 주세요.",
      );
      return;
    }
    setDrawIds(storedDrawIds);
  }, [hydrated, params.batchKey]);

  const load = useCallback(
    async (signal?: AbortSignal) => {
      if (!state.accessToken || !drawIds) return;
      try {
        let loaded = await mapInChunks(drawIds, (drawId) =>
          getGachaDraw(drawId, state.accessToken!, signal),
        );
        const invalid = loaded.some(
          (detail) =>
            detail.status === "COMPLETED" && detail.items.length !== 5,
        );
        if (invalid) {
          setError("일부 팩의 확정 결과가 올바르지 않습니다.");
          return;
        }
        if (loaded.some((detail) => detail.status === "MANUAL_REVIEW")) {
          setError(
            "일부 팩을 자동 처리하지 못했습니다. 관리자 확인 후 다시 열어 주세요.",
          );
          return;
        }
        if (loaded.some((detail) => detail.status === "REFUNDED")) {
          setError("준비하지 못한 팩의 사용 포인트를 돌려드렸어요.");
          return;
        }

        const allCompleted = loaded.every(
          (detail) => detail.status === "COMPLETED",
        );
        if (allCompleted) {
          const unviewed = loaded.filter((detail) => !detail.resultViewedAt);
          if (unviewed.length > 0) {
            await mapInChunks(unviewed, (detail) =>
              markGachaDrawViewed(detail.drawId, state.accessToken!),
            );
            const viewedDrawIds = new Set(
              unviewed.map((detail) => detail.drawId),
            );
            const viewedAt = new Date().toISOString();
            loaded = loaded.map((detail) =>
              viewedDrawIds.has(detail.drawId)
                ? { ...detail, resultViewedAt: viewedAt }
                : detail,
            );
          }
        }

        setDetails(loaded);
        setError("");
        if (allCompleted) {
          setStage((current) => (current === "loading" ? "pack" : current));
        }
      } catch (cause) {
        if (cause instanceof DOMException && cause.name === "AbortError")
          return;
        setError(
          cause instanceof ApiError
            ? cause.message
            : "다중 팩 결과를 불러오지 못했습니다.",
        );
      }
    },
    [drawIds, state.accessToken],
  );

  useEffect(() => {
    if (!drawIds || !state.accessToken) return;
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, [drawIds, load, state.accessToken]);

  useEffect(() => {
    if (
      !drawIds ||
      details.length === 0 ||
      details.every((detail) => detail.status === "COMPLETED") ||
      error
    ) {
      return;
    }
    const controller = new AbortController();
    const timer = window.setTimeout(() => void load(controller.signal), 1500);
    return () => {
      controller.abort();
      window.clearTimeout(timer);
    };
  }, [details, drawIds, error, load]);

  usePreventBackNavigation(
    hydrated &&
      Boolean(state.accessToken) &&
      Boolean(drawIds?.length) &&
      !error,
  );

  const groupedResults = useMemo(
    () => groupGachaDrawResults(details),
    [details],
  );
  const completedCount = details.filter(
    (detail) => detail.status === "COMPLETED",
  ).length;
  const totalCardCount = details.reduce(
    (sum, detail) => sum + detail.items.length,
    0,
  );

  const confirm = () => {
    removeGachaBatch(params.batchKey);
    router.replace("/gacha?tab=mine");
  };

  const showSummary = () => {
    const highestRarity = details
      .flatMap((detail) => detail.items)
      .reduce<GachaRarity>(
        (highest, item) =>
          RARITY_ORDER[item.finalRarity] > RARITY_ORDER[highest]
            ? item.finalRarity
            : highest,
        "COMMON",
      );
    playRarityRevealSound(highestRarity, muted);
    setStage("summary");
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
            onClick={() => {
              setError("");
              void load();
            }}
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
        <div className="flex items-center justify-end gap-2">
          <button
            type="button"
            onClick={() => setMuted((value) => !value)}
            className="rounded-full bg-white/10 px-3 py-2 text-xs font-bold"
          >
            {muted ? "🔇 음소거" : "🔊 사운드"}
          </button>
          {stage !== "summary" && (
            <button
              type="button"
              onClick={() => setStage("summary")}
              className="rounded-full border border-white/15 bg-black/25 px-3 py-2 text-xs font-bold text-white/75 backdrop-blur transition hover:bg-white/10 hover:text-white"
            >
              연출 건너뛰기
            </button>
          )}
        </div>

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
                <Link
                  href="/journals"
                  onClick={moveToJournals}
                  className="rounded-full border border-white/20 px-8 py-3.5 font-black text-white/80 transition hover:-translate-y-0.5 hover:bg-white/10"
                >
                  일지 보러 가기
                </Link>
                <Link
                  href="/shop?category=GACHA_PACK&sort=new&page=1"
                  className="rounded-full border border-[#d7c266]/60 px-8 py-3.5 font-black text-[#f2dc83] transition hover:-translate-y-0.5 hover:bg-[#d7c266]/10"
                >
                  카드팩 구매하기
                </Link>
                <Link
                  href="/gacha?tab=history"
                  className="rounded-full border border-white/20 px-8 py-3.5 font-black text-white/80 transition hover:-translate-y-0.5 hover:bg-white/10"
                >
                  다른 개봉 내역 보기
                </Link>
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
