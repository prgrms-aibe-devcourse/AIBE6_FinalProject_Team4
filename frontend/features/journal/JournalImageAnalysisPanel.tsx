"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import {
  analyzeJournalImage,
  getJournalImageAnalyses,
  JournalImageAnalysisData,
  JournalImageQuality,
  JournalPlantCondition,
} from "@/features/journal/journal-image-analysis-api";
import { ApiError, resolveImageUrl } from "@/lib/api";
import { JournalImageData } from "@/lib/journal-api";

interface JournalImageAnalysisPanelProps {
  journalId: number;
  images: JournalImageData[];
  activeIndex: number;
  accessToken: string;
}

const ANALYSIS_TIMEOUT_MS = 40_000;

const CONDITION_META: Record<
  JournalPlantCondition,
  { label: string; icon: string; className: string }
> = {
  HEALTHY: {
    label: "안정적으로 보여요",
    icon: "check_circle",
    className: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  },
  NEEDS_ATTENTION: {
    label: "조금 더 관찰해 주세요",
    icon: "visibility",
    className: "bg-amber-50 text-amber-700 ring-amber-200",
  },
  URGENT_CHECK: {
    label: "빠른 확인이 필요해요",
    icon: "error",
    className: "bg-rose-50 text-rose-700 ring-rose-200",
  },
  UNKNOWN: {
    label: "사진만으로 판단하기 어려워요",
    icon: "help",
    className: "bg-slate-50 text-slate-600 ring-slate-200",
  },
};

const QUALITY_LABEL: Record<JournalImageQuality, string> = {
  CLEAR: "사진 선명도 좋음",
  LIMITED: "일부만 확인 가능",
  UNUSABLE: "재촬영 권장",
};

function AnalysisList({
  title,
  icon,
  items,
  accent = false,
}: {
  title: string;
  icon: string;
  items: string[];
  accent?: boolean;
}) {
  if (items.length === 0) return null;
  return (
    <section
      className={`rounded-2xl border p-4 sm:p-5 ${
        accent
          ? "border-emerald-200 bg-emerald-50/70"
          : "border-[#e7eadf] bg-white/80"
      }`}
    >
      <h3 className="mb-3 flex items-center gap-2 text-sm font-extrabold text-[#344331]">
        <span className="material-symbols-outlined text-[19px] text-[#4f7c42]">
          {icon}
        </span>
        {title}
      </h3>
      <ul className="space-y-2.5">
        {items.map((item, index) => (
          <li
            key={`${title}-${index}`}
            className="flex gap-2.5 text-[13.5px] leading-[1.65] text-[#52604e]"
          >
            <span className="mt-[8px] h-1.5 w-1.5 shrink-0 rounded-full bg-[#79a76b]" />
            <span>{item}</span>
          </li>
        ))}
      </ul>
    </section>
  );
}

export default function JournalImageAnalysisPanel({
  journalId,
  images,
  activeIndex,
  accessToken,
}: JournalImageAnalysisPanelProps) {
  const [results, setResults] = useState<
    Record<string, JournalImageAnalysisData>
  >({});
  const [loadingSaved, setLoadingSaved] = useState(true);
  const [analyzingHash, setAnalyzingHash] = useState<string | null>(null);
  const [error, setError] = useState<{
    imageHash: string | null;
    message: string;
  } | null>(null);
  const analysisControllerRef = useRef<AbortController | null>(null);
  const analysisTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const imageSignature = useMemo(
    () => images.map((image) => image.imageHash).join(","),
    [images],
  );
  const activeImage = images[activeIndex] ?? images[0] ?? null;
  const result = activeImage ? results[activeImage.imageHash] : null;
  const isAnalyzing = analyzingHash === activeImage?.imageHash;
  const visibleError =
    error &&
    (error.imageHash === null || error.imageHash === activeImage?.imageHash)
      ? error.message
      : "";

  useEffect(() => {
    setAnalyzingHash(null);
    return () => {
      analysisControllerRef.current?.abort();
      analysisControllerRef.current = null;
      if (analysisTimeoutRef.current !== null) {
        clearTimeout(analysisTimeoutRef.current);
        analysisTimeoutRef.current = null;
      }
    };
  }, [activeImage?.imageHash, journalId]);

  useEffect(() => {
    const controller = new AbortController();
    setLoadingSaved(true);
    setError(null);
    getJournalImageAnalyses(journalId, accessToken, controller.signal)
      .then((savedResults) => {
        setResults(
          Object.fromEntries(
            savedResults.map((savedResult) => [
              savedResult.imageHash,
              savedResult,
            ]),
          ),
        );
      })
      .catch((requestError) => {
        if (
          requestError instanceof DOMException &&
          requestError.name === "AbortError"
        )
          return;
        setError({
          imageHash: null,
          message: "저장된 분석 결과를 불러오지 못했어요.",
        });
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoadingSaved(false);
      });

    return () => controller.abort();
  }, [accessToken, imageSignature, journalId]);

  const runAnalysis = async () => {
    if (!activeImage || analyzingHash) return;
    const requestedImageHash = activeImage.imageHash;
    const controller = new AbortController();
    let timedOut = false;
    analysisControllerRef.current = controller;
    analysisTimeoutRef.current = setTimeout(() => {
      timedOut = true;
      controller.abort();
    }, ANALYSIS_TIMEOUT_MS);
    setAnalyzingHash(requestedImageHash);
    setError(null);
    try {
      const analysis = await analyzeJournalImage(
        journalId,
        requestedImageHash,
        accessToken,
        controller.signal,
      );
      if (controller.signal.aborted) return;
      setResults((current) => ({
        ...current,
        [analysis.imageHash]: analysis,
      }));
    } catch (requestError) {
      if (controller.signal.aborted) {
        if (timedOut) {
          setError({
            imageHash: requestedImageHash,
            message:
              "AI 분석 응답 시간이 초과됐어요. 잠시 후 다시 확인해 주세요.",
          });
        }
        return;
      }
      if (
        requestError instanceof ApiError &&
        requestError.code === "AI_IMAGE_ANALYSIS_IN_PROGRESS"
      ) {
        setError({
          imageHash: requestedImageHash,
          message:
            "이 사진을 이미 분석하고 있어요. 잠시 후 다시 확인해 주세요.",
        });
      } else {
        setError({
          imageHash: requestedImageHash,
          message:
            requestError instanceof ApiError
              ? requestError.message
              : "사진을 분석하지 못했어요. 잠시 후 다시 시도해 주세요.",
        });
      }
    } finally {
      if (analysisControllerRef.current === controller) {
        analysisControllerRef.current = null;
        if (analysisTimeoutRef.current !== null) {
          clearTimeout(analysisTimeoutRef.current);
          analysisTimeoutRef.current = null;
        }
        setAnalyzingHash(null);
      }
    }
  };

  const cancelAnalysis = () => {
    analysisControllerRef.current?.abort();
    analysisControllerRef.current = null;
    if (analysisTimeoutRef.current !== null) {
      clearTimeout(analysisTimeoutRef.current);
      analysisTimeoutRef.current = null;
    }
    setAnalyzingHash(null);
  };

  if (!activeImage) return null;

  const condition = result ? CONDITION_META[result.condition] : null;

  return (
    <section
      aria-labelledby="journal-image-analysis-title"
      className="relative mt-8 overflow-hidden rounded-[24px] border border-[#dfe8d8] bg-[#f8faf5] shadow-[0_18px_55px_rgba(48,75,42,0.09)]"
    >
      <div className="absolute -right-20 -top-24 h-64 w-64 rounded-full bg-emerald-200/25 blur-3xl" />
      <div className="relative grid gap-0 lg:grid-cols-[290px_1fr]">
        <div className="relative min-h-[270px] overflow-hidden bg-[#1d3024] p-5 text-white sm:p-6">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src={resolveImageUrl(activeImage.imageUrl)}
            alt="AI 분석 대상으로 선택한 일지 사진"
            className="absolute inset-0 h-full w-full object-cover opacity-45"
          />
          <div className="absolute inset-0 bg-gradient-to-t from-[#14251b] via-[#1c3426]/75 to-[#173e2a]/30" />
          {isAnalyzing && (
            <div className="pointer-events-none absolute inset-x-5 top-1/2 h-px animate-pulse bg-gradient-to-r from-transparent via-emerald-300 to-transparent shadow-[0_0_18px_4px_rgba(110,231,183,.7)]" />
          )}
          <div className="relative flex h-full min-h-[222px] flex-col justify-between">
            <div className="flex items-center justify-between gap-3">
              {images.length > 1 && (
                <span className="rounded-full bg-black/30 px-2.5 py-1 text-[11px] font-semibold backdrop-blur-md">
                  {activeIndex + 1} / {images.length}
                </span>
              )}
            </div>
            <div>
              <div className="mb-2 text-xs font-semibold text-emerald-200">
                현재 선택한 저장 사진
              </div>
              <p className="text-xl font-extrabold leading-snug sm:text-[22px]">
                {isAnalyzing
                  ? "잎과 줄기의 신호를 살펴보고 있어요"
                  : result
                    ? "사진 속 성장 신호를 정리했어요"
                    : "사진으로 식물의 상태를 살펴보세요"}
              </p>
            </div>
          </div>
        </div>

        <div className="relative p-5 sm:p-7">
          <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
            <div>
              <div className="mb-1 flex items-center gap-2">
                <span className="rounded-full bg-[#e8f3e4] px-2.5 py-1 text-[11px] font-extrabold text-[#47703c]">
                  저장 사진 분석
                </span>
                {result && (
                  <span className="text-[11px] font-semibold text-[#8a9584]">
                    {QUALITY_LABEL[result.imageQuality]}
                  </span>
                )}
              </div>
              <h2
                id="journal-image-analysis-title"
                className="text-xl font-extrabold tracking-[-0.02em] text-[#273326]"
              >
                AI 식물 사진 리포트
              </h2>
            </div>
            {condition && (
              <span
                className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs font-extrabold ring-1 ring-inset ${condition.className}`}
              >
                <span className="material-symbols-outlined text-[17px]">
                  {condition.icon}
                </span>
                {condition.label}
              </span>
            )}
          </div>

          {loadingSaved ? (
            <div
              className="space-y-3"
              aria-label="저장된 사진 분석 결과 불러오는 중"
            >
              <div className="h-5 w-2/3 animate-pulse rounded-full bg-[#e7eadf]" />
              <div className="h-4 w-full animate-pulse rounded-full bg-[#edf0e8]" />
              <div className="h-4 w-4/5 animate-pulse rounded-full bg-[#edf0e8]" />
            </div>
          ) : isAnalyzing ? (
            <div className="rounded-2xl border border-emerald-200 bg-white/80 p-5">
              <div className="mb-3 flex items-center gap-3">
                <span className="relative flex h-10 w-10 items-center justify-center rounded-full bg-emerald-100 text-emerald-700">
                  <span className="absolute inset-0 animate-ping rounded-full bg-emerald-200/60" />
                  <span className="material-symbols-outlined relative text-[21px]">
                    image_search
                  </span>
                </span>
                <div>
                  <div className="text-sm font-extrabold text-[#344331]">
                    사진과 최근 성장 기록을 함께 확인 중이에요
                  </div>
                  <div className="mt-0.5 text-xs text-[#7c8878]">
                    보이는 특징만 근거로 안전한 관리 방법을 정리합니다.
                  </div>
                </div>
              </div>
              <div className="h-1.5 overflow-hidden rounded-full bg-emerald-100">
                <div className="h-full w-2/3 animate-pulse rounded-full bg-gradient-to-r from-emerald-400 to-lime-400" />
              </div>
              <button
                type="button"
                onClick={cancelAnalysis}
                className="mt-4 cursor-pointer rounded-lg bg-white px-3 py-1.5 text-xs font-extrabold text-emerald-800 ring-1 ring-inset ring-emerald-200"
              >
                분석 취소
              </button>
            </div>
          ) : result ? (
            <div>
              <p className="mb-5 text-[15px] font-semibold leading-[1.75] text-[#465443]">
                {result.summary}
              </p>
              <div className="grid gap-3 md:grid-cols-2">
                <AnalysisList
                  title="사진에서 보이는 점"
                  icon="center_focus_strong"
                  items={result.observations}
                />
                <AnalysisList
                  title="가능한 원인"
                  icon="account_tree"
                  items={result.possibleCauses}
                />
                <AnalysisList
                  title="지금 해볼 관리"
                  icon="task_alt"
                  items={result.recommendedActions}
                  accent
                />
                <AnalysisList
                  title="추가로 확인할 곳"
                  icon="manage_search"
                  items={result.additionalChecks}
                />
              </div>
            </div>
          ) : (
            <div className="rounded-2xl border border-[#e4e8dd] bg-white/75 p-5 sm:p-6">
              <p className="mb-1 text-[15px] font-extrabold text-[#354333]">
                새 사진을 올릴 필요가 없어요
              </p>
              <p className="mb-5 text-[13.5px] leading-relaxed text-[#748070]">
                현재 선택한 일지 사진과 최근 기록을 함께 참고해, 눈에 보이는
                변화와 다음 관리 방법을 정리해 드려요.
              </p>
              <button
                type="button"
                aria-label="선택한 사진 AI로 살펴보기"
                onClick={runAnalysis}
                disabled={Boolean(analyzingHash)}
                className="inline-flex w-full cursor-pointer items-center justify-center gap-2 rounded-[14px] bg-gradient-to-r from-[#315c3b] to-[#4f7f47] px-5 py-3.5 text-sm font-extrabold text-white shadow-[0_10px_24px_rgba(48,91,57,.2)] transition hover:-translate-y-0.5 hover:shadow-[0_14px_28px_rgba(48,91,57,.25)] disabled:cursor-not-allowed disabled:opacity-60 sm:w-auto"
              >
                <span className="material-symbols-outlined text-[19px]">
                  image_search
                </span>
                선택한 사진 AI로 살펴보기
              </button>
            </div>
          )}

          {visibleError && (
            <div
              role="alert"
              className="mt-4 flex flex-wrap items-center justify-between gap-2 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-[13px] font-semibold text-rose-700"
            >
              <span>{visibleError}</span>
              {!isAnalyzing && (
                <button
                  type="button"
                  onClick={runAnalysis}
                  className="cursor-pointer rounded-lg bg-white px-3 py-1.5 text-xs font-extrabold ring-1 ring-inset ring-rose-200"
                >
                  다시 시도
                </button>
              )}
            </div>
          )}

          <p className="mt-5 flex items-start gap-1.5 text-[11.5px] leading-relaxed text-[#8a9584]">
            <span className="material-symbols-outlined mt-px text-[15px]">
              info
            </span>
            AI 결과는 사진 기반 참고 정보이며 병해충이나 영양 문제를 확정
            진단하지 않습니다.
          </p>
        </div>
      </div>
    </section>
  );
}
