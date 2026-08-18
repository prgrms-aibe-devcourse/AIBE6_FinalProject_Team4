"use client";

import Image from "next/image";
import { useEffect, useState, type CSSProperties } from "react";
import GoldenCelebrationEffects from "@/components/gacha/GoldenCelebrationEffects";
import { playRarityRevealSound } from "@/features/gacha/audio";
import type { GachaDrawItem, GachaRarity } from "@/lib/gacha-api";

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

export default function GachaRevealCard({
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
