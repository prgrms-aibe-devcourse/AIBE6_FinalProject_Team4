"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { playRarityRevealSound } from "@/features/gacha/audio";
import { loadGachaBatch } from "@/features/gacha/batch-session";
import {
  commerceErrorMessage,
  isAbortError,
} from "@/features/commerce/presentation";
import { groupGachaDrawResults } from "@/features/gacha/result";
import { usePreventBackNavigation } from "@/features/gacha/use-prevent-back-navigation";
import {
  GachaDrawDetail,
  GachaRarity,
  getGachaDraw,
  markGachaDrawViewed,
} from "@/lib/gacha-api";

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

export function useGachaBatchOpen({
  batchKey,
  accessToken,
  hydrated,
  muted,
}: {
  batchKey: string;
  accessToken: string | null;
  hydrated: boolean;
  muted: boolean;
}) {
  const [drawIds, setDrawIds] = useState<number[] | null>(null);
  const [details, setDetails] = useState<GachaDrawDetail[]>([]);
  const [stage, setStage] = useState<Stage>("loading");
  const [error, setError] = useState("");

  useEffect(() => {
    if (!hydrated) return;
    const storedDrawIds = loadGachaBatch(batchKey);
    if (!storedDrawIds) {
      setError(
        "다중 팩 개봉 정보를 찾을 수 없습니다. 생성한 브라우저 탭에서 다시 시도해 주세요.",
      );
      return;
    }
    setDrawIds(storedDrawIds);
  }, [batchKey, hydrated]);

  const load = useCallback(
    async (signal?: AbortSignal) => {
      if (!accessToken || !drawIds) return;
      try {
        let loaded = await mapInChunks(drawIds, (drawId) =>
          getGachaDraw(drawId, accessToken, signal),
        );
        if (
          loaded.some(
            (detail) =>
              detail.status === "COMPLETED" && detail.items.length !== 5,
          )
        ) {
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
              markGachaDrawViewed(detail.drawId, accessToken),
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
        if (isAbortError(cause)) return;
        setError(
          commerceErrorMessage(cause, "다중 팩 결과를 불러오지 못했습니다."),
        );
      }
    },
    [accessToken, drawIds],
  );

  useEffect(() => {
    if (!drawIds || !accessToken) return;
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, [accessToken, drawIds, load]);

  useEffect(() => {
    if (
      !drawIds ||
      details.length === 0 ||
      details.every((detail) => detail.status === "COMPLETED") ||
      error
    )
      return;
    const controller = new AbortController();
    const timer = window.setTimeout(() => void load(controller.signal), 1500);
    return () => {
      controller.abort();
      window.clearTimeout(timer);
    };
  }, [details, drawIds, error, load]);

  usePreventBackNavigation(
    hydrated && Boolean(accessToken) && Boolean(drawIds?.length) && !error,
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

  const retry = () => {
    setError("");
    void load();
  };

  return {
    drawIds,
    stage,
    setStage,
    error,
    load,
    groupedResults,
    completedCount,
    totalCardCount,
    showSummary,
    retry,
  };
}
