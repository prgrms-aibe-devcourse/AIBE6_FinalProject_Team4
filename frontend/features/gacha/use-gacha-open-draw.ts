"use client";

import { useCallback, useEffect, useState } from "react";
import {
  commerceErrorMessage,
  isAbortError,
} from "@/features/commerce/presentation";
import { usePreventBackNavigation } from "@/features/gacha/use-prevent-back-navigation";
import {
  GachaDrawDetail,
  getGachaDraw,
  markGachaDrawViewed,
} from "@/lib/gacha-api";

type Stage = "loading" | "pack" | "shuffle" | "reveal" | "summary";

export function useGachaOpenDraw({
  drawId,
  accessToken,
  hydrated,
}: {
  drawId: number;
  accessToken: string | null;
  hydrated: boolean;
}) {
  const [detail, setDetail] = useState<GachaDrawDetail | null>(null);
  const [stage, setStage] = useState<Stage>("loading");
  const [revealedIndex, setRevealedIndex] = useState(0);
  const [error, setError] = useState("");

  const load = useCallback(
    async (signal?: AbortSignal) => {
      if (!accessToken || !Number.isInteger(drawId) || drawId < 1) return;
      try {
        const data = await getGachaDraw(drawId, accessToken, signal);
        if (data.status === "REFUNDED") {
          setDetail(data);
          setError("팩을 준비하지 못해 사용한 포인트를 돌려드렸어요.");
          return;
        }
        if (data.status === "COMPLETED" && data.items.length !== 5) {
          setError(
            "카드 결과를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.",
          );
          return;
        }
        const shouldStartOpening =
          data.status === "COMPLETED" && !data.resultViewedAt;
        if (shouldStartOpening) {
          await markGachaDrawViewed(data.drawId, accessToken);
        }
        setDetail(
          shouldStartOpening
            ? { ...data, resultViewedAt: new Date().toISOString() }
            : data,
        );
        setError("");
        setStage(
          data.status === "COMPLETED"
            ? shouldStartOpening
              ? "pack"
              : "summary"
            : "loading",
        );
      } catch (cause) {
        if (isAbortError(cause)) return;
        setError(
          commerceErrorMessage(cause, "개봉 결과를 불러오지 못했습니다."),
        );
      }
    },
    [accessToken, drawId],
  );

  useEffect(() => {
    if (!hydrated || !accessToken) return;
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, [accessToken, hydrated, load]);

  useEffect(() => {
    if (
      !accessToken ||
      !detail ||
      detail.status === "COMPLETED" ||
      detail.status === "REFUNDED"
    )
      return;
    const controller = new AbortController();
    const timer = window.setInterval(() => void load(controller.signal), 1500);
    return () => {
      controller.abort();
      window.clearInterval(timer);
    };
  }, [accessToken, detail, load]);

  usePreventBackNavigation(
    hydrated &&
      Boolean(accessToken) &&
      Number.isInteger(drawId) &&
      drawId > 0 &&
      !error,
  );

  const revealNext = () => {
    if (!detail) return;
    if (stage === "pack") {
      setStage("shuffle");
      return;
    }
    if (stage === "shuffle") {
      setRevealedIndex(0);
      setStage("reveal");
      return;
    }
    if (stage !== "reveal") return;
    if (revealedIndex < detail.items.length - 1) {
      setRevealedIndex((value) => value + 1);
    } else {
      setStage("summary");
    }
  };

  const replay = () => {
    setRevealedIndex(0);
    setStage("pack");
  };

  return {
    detail,
    stage,
    setStage,
    revealedIndex,
    error,
    load,
    revealNext,
    replay,
  };
}
