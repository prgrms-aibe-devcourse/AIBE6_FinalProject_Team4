"use client";

import Image from "next/image";
import { useEffect, type CSSProperties } from "react";
import { playShuffleSound } from "@/features/gacha/audio";

const CARD_BACK = "/cards/900002/61de4f73-7b73-541c-9dfe-5bfc5ae6dc0c.svg";
const CARD_LAYOUT = [
  {
    x: "clamp(-116px, -26vw, -82px)",
    y: "18px",
    rotation: "-12deg",
    crossX: "92px",
    crossRotation: "12deg",
  },
  {
    x: "clamp(-58px, -13vw, -41px)",
    y: "-2px",
    rotation: "-6deg",
    crossX: "-104px",
    crossRotation: "-13deg",
  },
  {
    x: "0px",
    y: "-18px",
    rotation: "0deg",
    crossX: "118px",
    crossRotation: "15deg",
  },
  {
    x: "clamp(41px, 13vw, 58px)",
    y: "-2px",
    rotation: "6deg",
    crossX: "-112px",
    crossRotation: "-14deg",
  },
  {
    x: "clamp(82px, 26vw, 116px)",
    y: "18px",
    rotation: "12deg",
    crossX: "86px",
    crossRotation: "11deg",
  },
] as const;

interface GachaShuffleStageProps {
  muted?: boolean;
  packCount?: number;
  onComplete: () => void;
  completeLabel?: string;
}

export default function GachaShuffleStage({
  muted = false,
  packCount = 1,
  onComplete,
  completeLabel = "첫 카드 확인하기",
}: GachaShuffleStageProps) {
  useEffect(() => {
    playShuffleSound(muted);
  }, [muted]);

  return (
    <section
      aria-labelledby="shuffle-title"
      className="flex w-full flex-col items-center text-center motion-safe:animate-stageEnter"
    >
      <p className="text-xs font-black tracking-[0.3em] text-[#e4ce72]">
        {packCount > 1 ? `${packCount} PACKS` : "CARD SHUFFLE"}
      </p>
      <h1 id="shuffle-title" className="mt-2 text-2xl font-black">
        카드의 순서를 섞고 있어요
      </h1>
      <p className="mt-2 text-sm text-white/55">
        팩에서 나온 카드가 하나씩 자리를 찾습니다
      </p>

      <div
        className="relative mt-5 h-[390px] w-[min(94vw,520px)] [perspective:1300px]"
        aria-label="카드 셔플 연출"
      >
        <div className="pointer-events-none absolute left-1/2 top-1/2 h-64 w-64 -translate-x-1/2 -translate-y-1/2 rounded-full border border-[#ebdb89]/10 motion-safe:animate-shuffleOrbit" />
        <div className="pointer-events-none absolute left-1/2 top-1/2 h-48 w-48 -translate-x-1/2 -translate-y-1/2 rounded-full border border-white/10 motion-safe:animate-shuffleOrbitReverse" />
        <div className="absolute inset-x-[12%] bottom-8 h-12 rounded-full bg-black/65 blur-2xl" />

        {CARD_LAYOUT.map((layout, index) => (
          <div
            key={layout.x}
            className="absolute left-1/2 top-1/2 aspect-[1122/1402] w-[min(40vw,184px)] motion-safe:animate-cardDeal"
            style={
              {
                "--fan-x": layout.x,
                "--fan-y": layout.y,
                "--fan-r": layout.rotation,
                "--cross-x": layout.crossX,
                "--cross-r": layout.crossRotation,
                animationDelay: `${index * 80}ms`,
                zIndex: index === 2 ? 5 : 4 - Math.abs(2 - index),
                transform: `translate(-50%, -50%) translate3d(${layout.x}, ${layout.y}, 0) rotate(${layout.rotation})`,
              } as CSSProperties
            }
          >
            <div className="relative h-full w-full overflow-hidden rounded-[20px] border border-[#f6e4a0]/70 bg-[#102519] shadow-[0_22px_42px_rgba(0,0,0,.5)]">
              <Image
                src={CARD_BACK}
                alt=""
                fill
                priority={index === 2}
                className="object-contain drop-shadow-[0_20px_24px_rgba(0,0,0,.55)]"
              />
              <div
                className="pointer-events-none absolute inset-0 bg-[linear-gradient(110deg,transparent_28%,rgba(255,255,255,.3)_45%,transparent_62%)] bg-[length:230%_100%] opacity-70 mix-blend-screen motion-safe:animate-shuffleSweep"
                style={{ animationDelay: `${760 + index * 70}ms` }}
              />
            </div>
          </div>
        ))}

        <div className="pointer-events-none absolute left-1/2 top-1/2 h-52 w-52 -translate-x-1/2 -translate-y-1/2 rounded-full bg-[#e5d36c]/15 blur-3xl motion-safe:animate-glowPulse" />
        <p className="absolute inset-x-0 bottom-0 text-[11px] font-bold tracking-[0.22em] text-white/35">
          FIVE CARDS · ONE COLLECTION
        </p>
      </div>

      <button
        type="button"
        onClick={onComplete}
        className="mt-1 rounded-full bg-white px-7 py-3.5 font-black text-[#253822] shadow-[0_12px_35px_rgba(0,0,0,.3)] transition hover:-translate-y-0.5 hover:bg-[#f5f8ef] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#e8d77d]"
      >
        {completeLabel}
      </button>
    </section>
  );
}
