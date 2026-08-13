"use client";

import Image from "next/image";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  useCallback,
  useEffect,
  useState,
  type CSSProperties,
  type MouseEvent,
} from "react";
import GoldenCelebrationEffects from "@/components/gacha/GoldenCelebrationEffects";
import GachaPackStage from "@/components/gacha/GachaPackStage";
import GachaShuffleStage from "@/components/gacha/GachaShuffleStage";
import { playRarityRevealSound } from "@/features/gacha/audio";
import { usePreventBackNavigation } from "@/features/gacha/use-prevent-back-navigation";
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

const RARITY_REVEAL_STYLE: Record<
  GachaRarity,
  {
    aura: string;
    frame: string;
    particleColor: string;
    particleCount: number;
  }
> = {
  COMMON: {
    aura: "bg-[#80bd78]/20",
    frame: "ring-1 ring-[#a9d39e]/35",
    particleColor: "#a9d39e",
    particleCount: 0,
  },
  RARE: {
    aura: "bg-[#6ca9dc]/25",
    frame: "ring-2 ring-[#8fc9f4]/55 shadow-[0_25px_75px_rgba(84,155,213,.22)]",
    particleColor: "#9bd4ff",
    particleCount: 6,
  },
  SUPER_RARE: {
    aura: "bg-[#a779e0]/30",
    frame: "ring-2 ring-[#c9a2f2]/70 shadow-[0_25px_80px_rgba(151,95,211,.3)]",
    particleColor: "#d8b8ff",
    particleCount: 10,
  },
  HYPER_RARE: {
    aura: "bg-[#ef6e8e]/30",
    frame: "ring-2 ring-[#ff9fb7]/80 shadow-[0_25px_90px_rgba(234,78,122,.38)]",
    particleColor: "#ffb0c4",
    particleCount: 16,
  },
  GOLDEN_RARE: {
    aura: "bg-[#d8ad42]/20",
    frame:
      "ring-1 ring-[#f4d97b]/80 shadow-[0_30px_110px_rgba(191,145,33,.34)]",
    particleColor: "#ffe783",
    particleCount: 22,
  },
};

type Stage = "loading" | "pack" | "shuffle" | "reveal" | "summary";

export default function GachaOpenPage({
  params,
}: {
  params: { drawId: string };
}) {
  const drawId = Number(params.drawId);
  const router = useRouter();
  const { state, hydrated, refreshNotifications } = useStore();
  const [detail, setDetail] = useState<GachaDrawDetail | null>(null);
  const [stage, setStage] = useState<Stage>("loading");
  const [revealedIndex, setRevealedIndex] = useState(0);
  const [error, setError] = useState("");
  const [muted, setMuted] = useState(false);

  const moveToJournals = async (event: MouseEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    await refreshNotifications();
    router.push("/journals");
  };

  const load = useCallback(
    async (signal?: AbortSignal) => {
      if (!state.accessToken || !Number.isInteger(drawId) || drawId < 1) return;
      try {
        const data = await getGachaDraw(drawId, state.accessToken, signal);
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
          await markGachaDrawViewed(data.drawId, state.accessToken);
        }
        const viewedData = shouldStartOpening
          ? { ...data, resultViewedAt: new Date().toISOString() }
          : data;

        setDetail(viewedData);
        setError("");
        setStage(
          data.status === "COMPLETED"
            ? shouldStartOpening
              ? "pack"
              : "summary"
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
    if (
      !state.accessToken ||
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
  }, [detail, load, state.accessToken]);

  usePreventBackNavigation(
    hydrated &&
      Boolean(state.accessToken) &&
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
    if (stage === "reveal") {
      if (revealedIndex < detail.items.length - 1) {
        setRevealedIndex((value) => value + 1);
      } else {
        setStage("summary");
      }
    }
  };

  const confirm = () => {
    router.replace("/gacha?tab=mine");
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
        <div className="flex items-center justify-end">
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
          {stage === "pack" && (
            <GachaPackStage muted={muted} onOpen={revealNext} />
          )}

          {stage === "shuffle" && (
            <GachaShuffleStage muted={muted} onComplete={revealNext} />
          )}

          {stage === "reveal" && (
            <RevealCard
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
                <Link
                  href="/journals"
                  onClick={moveToJournals}
                  className="rounded-xl border border-white/25 px-5 py-3 text-sm font-bold"
                >
                  일지 보러 가기
                </Link>
                <Link
                  href="/shop?category=GACHA_PACK&sort=new&page=1"
                  className="rounded-xl border border-[#dfca72]/60 px-5 py-3 text-sm font-bold text-[#f3dc82] transition hover:bg-[#dfca72]/10"
                >
                  카드팩 구매하기
                </Link>
                <Link
                  href="/gacha?tab=history"
                  className="rounded-xl border border-white/25 px-5 py-3 text-sm font-bold"
                >
                  다른 개봉 내역 보기
                </Link>
                <button
                  type="button"
                  onClick={replay}
                  className="rounded-xl border border-white/25 px-5 py-3 text-sm font-bold"
                >
                  개봉 연출 다시 보기
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

function RevealCard({
  item,
  index,
  total,
  muted,
  onNext,
}: {
  item: GachaDrawItem;
  index: number;
  total: number;
  muted: boolean;
  onNext: () => void;
}) {
  const golden = item.finalRarity === "GOLDEN_RARE";
  const hyper = item.finalRarity === "HYPER_RARE";
  const premium = golden || hyper;
  const revealStyle = RARITY_REVEAL_STYLE[item.finalRarity];
  const [revealComplete, setRevealComplete] = useState(!premium);

  useEffect(() => {
    playRarityRevealSound(item.finalRarity, muted);
  }, [item.finalRarity, muted]);

  useEffect(() => {
    if (!premium) {
      setRevealComplete(true);
      return;
    }

    const reducedMotion =
      window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false;
    const timer = window.setTimeout(
      () => setRevealComplete(true),
      reducedMotion ? 0 : golden ? 3_400 : 1_850,
    );
    return () => window.clearTimeout(timer);
  }, [golden, item.sequence, premium]);

  return (
    <section
      aria-live="polite"
      className={`flex w-full flex-col items-center text-center ${
        premium ? "" : "motion-safe:animate-stageEnter"
      }`}
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

      <div className="relative isolate flex items-center justify-center">
        <div
          className={`pointer-events-none absolute h-[82%] w-[115%] rounded-full blur-3xl motion-safe:animate-revealAura ${revealStyle.aura}`}
        />
        {premium && <PremiumRevealEffects golden={golden} />}

        {Array.from(
          { length: premium ? 0 : revealStyle.particleCount },
          (_, particleIndex) => {
            const angle =
              (particleIndex / revealStyle.particleCount) * Math.PI * 2;
            const distance = 150 + (particleIndex % 4) * 24;
            return (
              <span
                key={particleIndex}
                className="pointer-events-none absolute left-1/2 top-1/2 h-2 w-2 rounded-full motion-safe:animate-rarityParticle"
                style={
                  {
                    "--particle-x": `${Math.cos(angle) * distance}px`,
                    "--particle-y": `${Math.sin(angle) * distance * 0.78}px`,
                    backgroundColor: revealStyle.particleColor,
                    boxShadow: `0 0 14px ${revealStyle.particleColor}`,
                    animationDelay: `${particleIndex * 35}ms`,
                  } as CSSProperties
                }
              />
            );
          },
        )}

        <div className="relative z-10 aspect-[1122/1402] w-[min(84vw,51vh,400px)] [perspective:1500px]">
          <div
            className={`absolute inset-0 [transform-style:preserve-3d] ${
              golden
                ? "motion-safe:animate-goldenCardReveal"
                : hyper
                  ? "motion-safe:animate-hyperCardReveal"
                  : "motion-safe:animate-cardReveal3d"
            }`}
          >
            <div
              className={`absolute inset-0 aspect-[1122/1402] overflow-hidden rounded-[24px] bg-black/20 shadow-[0_30px_70px_rgba(0,0,0,.5)] [backface-visibility:hidden] ${revealStyle.frame}`}
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
              <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(112deg,transparent_28%,rgba(255,255,255,.28)_45%,transparent_62%)] bg-[length:240%_100%] opacity-70 mix-blend-screen motion-safe:animate-goldenSweep" />
              {golden && (
                <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(108deg,transparent_30%,rgba(255,248,211,.28)_45%,transparent_58%)] bg-[length:280%_100%] mix-blend-screen motion-safe:animate-premiumFoil" />
              )}
              {hyper && (
                <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(108deg,transparent_24%,rgba(114,239,255,.24)_39%,rgba(255,255,255,.38)_47%,rgba(255,112,230,.2)_55%,transparent_70%)] bg-[length:300%_100%] mix-blend-screen motion-safe:animate-premiumFoil" />
              )}
              {item.new && (
                <span className="absolute left-4 top-4 rounded-full bg-[#ffda52] px-3 py-1.5 text-xs font-black text-[#4e3a00] shadow-[0_5px_20px_rgba(255,218,82,.35)]">
                  NEW
                </span>
              )}
            </div>
            <div
              className="absolute inset-0 overflow-hidden rounded-[24px] bg-[#102519] shadow-[0_30px_70px_rgba(0,0,0,.55)] [backface-visibility:hidden]"
              style={{ transform: "rotateY(180deg)" }}
            >
              <Image
                src={CARD_BACK}
                alt=""
                fill
                priority
                className="object-contain"
              />
            </div>
          </div>
        </div>
      </div>

      <div
        className={`flex flex-col items-center transition duration-500 ${
          revealComplete
            ? "translate-y-0 opacity-100"
            : "pointer-events-none translate-y-3 opacity-0"
        }`}
      >
        <h1 className="mt-5 text-2xl font-black">{item.name}</h1>
        <p className="mt-1 text-sm font-bold text-[#dfca72]">
          {RARITY_LABEL[item.finalRarity]}
          {item.downgraded ? " · 골든 구간 대체" : ""}
        </p>
        <button
          type="button"
          disabled={!revealComplete}
          onClick={onNext}
          className="mt-5 min-w-48 rounded-full bg-white px-6 py-3 text-sm font-black text-[#253822] shadow-lg transition hover:-translate-y-0.5 hover:bg-[#f4f8ed] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#e8d77d] disabled:cursor-wait"
        >
          {index < total - 1 ? "다음 카드 보기" : "전체 결과 보기"}
        </button>
      </div>
    </section>
  );
}

function PremiumRevealEffects({ golden }: { golden: boolean }) {
  if (golden) {
    return <GoldenCelebrationEffects />;
  }

  const sparkCount = 16;
  const color = "#b8f7ff";

  return (
    <>
      <div className="pointer-events-none fixed inset-0 z-0 bg-[radial-gradient(circle_at_50%_46%,rgba(91,86,210,.2),rgba(7,8,18,.96)_66%)] motion-reduce:hidden motion-safe:animate-premiumBackdrop" />
      <div className="pointer-events-none fixed inset-0 z-[1] bg-[radial-gradient(circle_at_center,transparent_30%,rgba(0,0,0,.62)_82%)] motion-reduce:hidden motion-safe:animate-premiumVignette" />

      <div className="pointer-events-none absolute left-1/2 top-1/2 -z-[1] aspect-square w-[185%] -translate-x-1/2 -translate-y-1/2 rounded-full bg-[repeating-conic-gradient(from_12deg,rgba(103,236,255,.38)_0deg,transparent_8deg,rgba(255,104,227,.25)_14deg,transparent_25deg)] blur-[2px] motion-reduce:hidden motion-safe:animate-hyperRadiance" />
      <div className="pointer-events-none absolute left-1/2 top-1/2 -z-[1] aspect-square w-[132%] -translate-x-1/2 -translate-y-1/2 rounded-full border border-[#9ff5ff]/70 shadow-[0_0_30px_rgba(83,221,255,.65),inset_0_0_25px_rgba(255,96,221,.24)] motion-reduce:hidden motion-safe:animate-hyperBurstRing" />
      <div className="pointer-events-none absolute left-1/2 top-1/2 -z-[1] aspect-square w-[104%] -translate-x-1/2 -translate-y-1/2 rounded-full bg-[radial-gradient(circle,rgba(255,255,255,.92)_0%,rgba(117,235,255,.42)_25%,rgba(238,91,255,.2)_48%,transparent_70%)] blur-2xl motion-reduce:hidden motion-safe:animate-hyperBloom" />

      {Array.from({ length: sparkCount }, (_, sparkIndex) => {
        const angle = (sparkIndex / sparkCount) * Math.PI * 2;
        const distance = 170 + (sparkIndex % 5) * 30;
        const size = 3 + (sparkIndex % 3) * 2;
        return (
          <span
            key={sparkIndex}
            className="pointer-events-none absolute left-1/2 top-1/2 z-[12] rounded-full motion-reduce:hidden motion-safe:animate-premiumSpark"
            style={
              {
                "--spark-x": `${Math.cos(angle) * distance}px`,
                "--spark-y": `${Math.sin(angle) * distance * 0.78}px`,
                width: `${size}px`,
                height: `${size}px`,
                backgroundColor: color,
                boxShadow: `0 0 ${size * 3}px ${color}`,
                animationDelay: `${650 + (sparkIndex % 4) * 24}ms`,
              } as CSSProperties
            }
          />
        );
      })}

      <div className="pointer-events-none fixed inset-0 z-20 bg-[#f5fbff] opacity-0 motion-reduce:hidden motion-safe:animate-hyperFlash" />
    </>
  );
}
