"use client";

import type { CSSProperties } from "react";

const GOLDEN_COLORS = ["#fff7bf", "#ffd95b", "#ffb52f", "#ffffff", "#e89f19"];
const FIREWORKS = [
  { left: "18%", top: "28%", delay: 0 },
  { left: "82%", top: "24%", delay: 180 },
  { left: "12%", top: "58%", delay: 360 },
  { left: "88%", top: "55%", delay: 480 },
] as const;

export default function GoldenCelebrationEffects() {
  return (
    <div
      data-testid="golden-celebration"
      aria-hidden="true"
      className="pointer-events-none fixed inset-0 z-[5] overflow-hidden motion-reduce:hidden"
    >
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_50%_48%,rgba(255,224,104,.34),rgba(23,15,2,.92)_64%,#080600_100%)] motion-safe:animate-goldenCelebrationBackdrop" />
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_center,transparent_22%,rgba(0,0,0,.7)_82%)] motion-safe:animate-goldenCelebrationVignette" />

      <div className="absolute left-1/2 top-1/2 h-[135vmax] w-[135vmax] -translate-x-1/2 -translate-y-1/2 rounded-full bg-[repeating-conic-gradient(from_0deg,rgba(255,240,160,.48)_0deg,rgba(255,240,160,.48)_2deg,transparent_7deg,transparent_15deg)] mix-blend-screen motion-safe:animate-goldenFanfareRays" />
      <div className="absolute left-1/2 top-1/2 h-[min(88vw,620px)] w-[min(88vw,620px)] -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-[#ffe99a]/80 shadow-[0_0_45px_rgba(255,217,77,.9),inset_0_0_50px_rgba(255,241,169,.38)] motion-safe:animate-goldenCelebrationRing" />
      <div className="absolute left-1/2 top-1/2 h-[min(68vw,470px)] w-[min(68vw,470px)] -translate-x-1/2 -translate-y-1/2 rounded-full bg-[radial-gradient(circle,rgba(255,255,247,.96)_0%,rgba(255,226,105,.5)_28%,transparent_68%)] blur-2xl motion-safe:animate-goldenCelebrationBloom" />

      {Array.from({ length: 12 }, (_, beamIndex) => (
        <span
          key={`beam-${beamIndex}`}
          className="absolute bottom-1/2 left-1/2 h-[75vmax] w-[clamp(18px,4vw,54px)] origin-bottom -translate-x-1/2 bg-[linear-gradient(to_top,rgba(255,244,184,.78),rgba(255,208,60,.18)_46%,transparent_78%)] blur-[2px] mix-blend-screen motion-safe:animate-goldenLightBeam"
          style={
            {
              "--beam-angle": `${beamIndex * 30}deg`,
              animationDelay: `${420 + (beamIndex % 3) * 55}ms`,
            } as CSSProperties
          }
        />
      ))}

      <div className="absolute inset-x-0 top-[10%] flex justify-center sm:top-[12%]">
        <div className="relative text-center motion-safe:animate-goldenFanfareTitle">
          <p className="text-[10px] font-black tracking-[0.55em] text-[#fff2a7] drop-shadow-[0_0_12px_rgba(255,218,82,.9)] sm:text-xs">
            ✦ LEGENDARY DISCOVERY ✦
          </p>
          <p className="mt-1 bg-[linear-gradient(180deg,#fff_0%,#fff1a6_32%,#ffc83d_70%,#a96b00_100%)] bg-clip-text text-3xl font-black tracking-[0.12em] text-transparent drop-shadow-[0_3px_12px_rgba(255,186,25,.75)] sm:text-5xl">
            GOLDEN RARE
          </p>
        </div>
      </div>

      {FIREWORKS.flatMap((firework, fireworkIndex) =>
        Array.from({ length: 12 }, (_, sparkIndex) => {
          const angle = (sparkIndex / 12) * Math.PI * 2;
          const distance = 70 + (sparkIndex % 3) * 24;
          const color =
            GOLDEN_COLORS[(fireworkIndex + sparkIndex) % GOLDEN_COLORS.length];
          return (
            <span
              key={`firework-${fireworkIndex}-${sparkIndex}`}
              className="absolute h-1.5 w-1.5 rounded-full motion-safe:animate-goldenFireworkSpark"
              style={
                {
                  left: firework.left,
                  top: firework.top,
                  "--firework-x": `${Math.cos(angle) * distance}px`,
                  "--firework-y": `${Math.sin(angle) * distance}px`,
                  backgroundColor: color,
                  boxShadow: `0 0 12px 3px ${color}`,
                  animationDelay: `${860 + firework.delay + sparkIndex * 12}ms`,
                } as CSSProperties
              }
            />
          );
        }),
      )}

      {Array.from({ length: 42 }, (_, confettiIndex) => {
        const color = GOLDEN_COLORS[confettiIndex % GOLDEN_COLORS.length];
        return (
          <span
            key={`confetti-${confettiIndex}`}
            className="absolute -top-8 rounded-[2px] motion-safe:animate-goldenConfetti"
            style={
              {
                left: `${(confettiIndex * 37) % 100}%`,
                width: `${4 + (confettiIndex % 3) * 2}px`,
                height: `${10 + (confettiIndex % 4) * 3}px`,
                backgroundColor: color,
                boxShadow: `0 0 8px ${color}`,
                "--confetti-drift": `${((confettiIndex % 9) - 4) * 18}px`,
                "--confetti-turn": `${240 + (confettiIndex % 7) * 85}deg`,
                animationDelay: `${880 + (confettiIndex % 11) * 70}ms`,
                animationDuration: `${1.75 + (confettiIndex % 6) * 0.16}s`,
              } as CSSProperties
            }
          />
        );
      })}

      <div className="absolute inset-0 bg-white opacity-0 mix-blend-screen motion-safe:animate-goldenFanfareFlash" />
    </div>
  );
}
