"use client";

import Image from "next/image";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import GachaPackStage from "@/components/gacha/GachaPackStage";
import GachaShuffleStage from "@/components/gacha/GachaShuffleStage";
import { ApiError } from "@/lib/api";
import {
  GachaDrawDetail,
  GachaDrawItem,
  GachaRarity,
  getGachaDraw,
  markGachaDrawViewed,
} from "@/lib/gacha-api";
import { useStore } from "@/lib/store";

const CARD_BACK = "/cards/900002/61de4f73-7b73-541c-9dfe-5bfc5ae6dc0c.svg";

const RARITY_LABEL: Record<GachaRarity, string> = {
  COMMON: "커먼",
  RARE: "레어",
  SUPER_RARE: "슈퍼 레어",
  HYPER_RARE: "하이퍼 레어",
  GOLDEN_RARE: "골든 레어",
};

type Stage = "loading" | "pack" | "shuffle" | "backs" | "reveal" | "summary";

export default function GachaOpenPage({
  params,
}: {
  params: { drawId: string };
}) {
  const drawId = Number(params.drawId);
  const router = useRouter();
  const { state, hydrated } = useStore();
  const [detail, setDetail] = useState<GachaDrawDetail | null>(null);
  const [stage, setStage] = useState<Stage>("loading");
  const [revealedIndex, setRevealedIndex] = useState(0);
  const [error, setError] = useState("");
  const [muted, setMuted] = useState(true);

  const load = useCallback(
    async (signal?: AbortSignal) => {
      if (!state.accessToken || !Number.isInteger(drawId) || drawId < 1) return;
      try {
        const data = await getGachaDraw(drawId, state.accessToken, signal);
        if (data.status === "COMPLETED" && data.items.length !== 5) {
          setError(
            "카드 결과를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.",
          );
          return;
        }
        setDetail(data);
        setError("");
        setStage(
          data.status === "COMPLETED"
            ? data.resultViewedAt
              ? "summary"
              : "pack"
            : "loading",
        );
      } catch (cause) {
        if (cause instanceof DOMException && cause.name === "AbortError")
          return;
        setError(
          cause instanceof ApiError
            ? cause.message
            : "개봉 결과를 불러오지 못했습니다.",
        );
      }
    },
    [drawId, state.accessToken],
  );

  useEffect(() => {
    if (!hydrated || !state.accessToken) return;
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, [hydrated, load, state.accessToken]);

  useEffect(() => {
    if (!state.accessToken || !detail || detail.status === "COMPLETED") return;
    const controller = new AbortController();
    const timer = window.setInterval(() => void load(controller.signal), 1500);
    return () => {
      controller.abort();
      window.clearInterval(timer);
    };
  }, [detail, load, state.accessToken]);

  const revealNext = () => {
    if (!detail) return;
    if (stage === "pack") {
      setStage("shuffle");
      return;
    }
    if (stage === "shuffle") {
      setStage("backs");
      return;
    }
    if (stage === "backs") {
      setRevealedIndex(0);
      setStage("reveal");
      return;
    }
    if (stage === "reveal") {
      if (revealedIndex < detail.items.length - 1) {
        setRevealedIndex((value) => value + 1);
      } else {
        setStage("summary");
      }
    }
  };

  const confirm = async () => {
    if (!detail || !state.accessToken) return;
    if (!detail.resultViewedAt) {
      await markGachaDrawViewed(detail.drawId, state.accessToken);
    }
    router.push("/gacha");
  };

  const replay = () => {
    setRevealedIndex(0);
    setStage("pack");
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
        <div className="flex items-center justify-between">
          <Link href="/gacha" className="text-sm font-bold text-white/70">
            ← 나가기
          </Link>
          <button
            type="button"
            onClick={() => setMuted((value) => !value)}
            className="rounded-full bg-white/10 px-3 py-2 text-xs font-bold"
          >
            {muted ? "🔇 음소거" : "🔊 사운드"}
          </button>
        </div>

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
          {stage === "pack" && <GachaPackStage onOpen={revealNext} />}

          {stage === "shuffle" && <GachaShuffleStage onComplete={revealNext} />}

          {stage === "backs" && (
            <button
              type="button"
              onClick={revealNext}
              className="flex flex-col items-center"
            >
              <p className="mb-8 text-sm font-bold tracking-[0.22em] text-white/55">
                5 CARDS
              </p>
              <div className="flex -space-x-24 sm:-space-x-14">
                {detail.items.map((item, index) => (
                  <div
                    key={item.sequence}
                    className="relative aspect-[1122/1402] w-[min(38vw,220px)] origin-bottom drop-shadow-2xl transition-transform duration-300 hover:-translate-y-2"
                    style={{ transform: `rotate(${(index - 2) * 6}deg)` }}
                  >
                    <Image
                      src={CARD_BACK}
                      alt="카드 뒷면"
                      fill
                      className="object-contain"
                    />
                  </div>
                ))}
              </div>
              <span className="mt-10 rounded-full bg-white px-6 py-3 font-black text-[#253822]">
                첫 카드 확인하기
              </span>
            </button>
          )}

          {stage === "reveal" && (
            <RevealCard
              key={detail.items[revealedIndex].sequence}
              item={detail.items[revealedIndex]}
              index={revealedIndex}
              total={detail.items.length}
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
                  onClick={replay}
                  className="rounded-xl border border-white/25 px-5 py-3 text-sm font-bold"
                >
                  개봉 연출 다시 보기
                </button>
                <button
                  type="button"
                  onClick={() => void confirm()}
                  className="rounded-xl bg-white px-7 py-3 text-sm font-black text-[#253822]"
                >
                  확인하고 내 카드 보기
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function RevealCard({
  item,
  index,
  total,
  onNext,
}: {
  item: GachaDrawItem;
  index: number;
  total: number;
  onNext: () => void;
}) {
  const golden = item.finalRarity === "GOLDEN_RARE";
  return (
    <section
      aria-live="polite"
      className="flex w-full flex-col items-center text-center"
    >
      <div
        className="mb-5 flex items-center gap-2"
        aria-label="카드 공개 진행률"
      >
        {Array.from({ length: total }, (_, dotIndex) => (
          <span
            key={dotIndex}
            className={`h-1.5 rounded-full transition-all duration-300 ${
              dotIndex === index
                ? "w-7 bg-[#e6d273]"
                : dotIndex < index
                  ? "w-3 bg-white/55"
                  : "w-3 bg-white/15"
            }`}
          />
        ))}
      </div>
      <div
        className={`relative aspect-[1122/1402] w-[min(84vw,51vh,400px)] overflow-hidden rounded-[24px] bg-black/20 shadow-[0_30px_70px_rgba(0,0,0,.5)] motion-safe:animate-pop ${
          golden
            ? "ring-2 ring-[#f6da63] shadow-[0_25px_80px_rgba(218,181,65,.25)]"
            : "ring-1 ring-white/10"
        }`}
      >
        {item.imageUrl && (
          <Image
            src={item.imageUrl}
            alt={item.name}
            fill
            priority
            sizes="(max-width: 640px) 84vw, 400px"
            className="object-contain"
          />
        )}
        {golden && (
          <div className="pointer-events-none absolute inset-0 bg-gradient-to-br from-transparent via-white/25 to-transparent mix-blend-screen" />
        )}
        {item.new && (
          <span className="absolute left-4 top-4 rounded-full bg-[#ffda52] px-3 py-1.5 text-xs font-black text-[#4e3a00]">
            NEW
          </span>
        )}
      </div>
      <h1 className="mt-5 text-2xl font-black">{item.name}</h1>
      <p className="mt-1 text-sm font-bold text-[#dfca72]">
        {RARITY_LABEL[item.finalRarity]}
        {item.downgraded ? " · 골든 구간 대체" : ""}
      </p>
      {item.goldenOriginRank && (
        <p className="mt-2 text-sm text-white/70">
          이 카드의 제 {item.goldenOriginRank}번째 최초 획득자
        </p>
      )}
      <button
        type="button"
        onClick={onNext}
        className="mt-5 min-w-48 rounded-full bg-white px-6 py-3 text-sm font-black text-[#253822] shadow-lg transition hover:-translate-y-0.5 hover:bg-[#f4f8ed] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#e8d77d]"
      >
        {index < total - 1 ? "다음 카드 보기" : "전체 결과 보기"}
      </button>
    </section>
  );
}
