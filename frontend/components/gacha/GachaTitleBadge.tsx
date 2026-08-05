import type { ReactNode } from "react";

type TitleEffect = {
  effect: string;
  icon: string;
  shell: string;
  glow: string;
  accent: ReactNode;
};

const TITLE_EFFECTS: Record<string, TitleEffect> = {
  TITLE_SPROUT_COLLECTOR: {
    effect: "sprout-glow",
    icon: "eco",
    shell:
      "border-[#8ccf83]/70 bg-gradient-to-r from-[#163d28] via-[#2e6b3e] to-[#8aae50] text-[#f1ffe8] shadow-[0_0_18px_rgba(117,190,92,.38)]",
    glow: "bg-[#9eff7c]/35",
    accent: (
      <>
        <span className="absolute -left-1 top-1 h-2 w-4 -rotate-12 rounded-[100%_0_100%_0] bg-[#b9ef8e]/80" />
        <span className="absolute bottom-0 right-2 h-1.5 w-1.5 rounded-full bg-[#d8ff9f] shadow-[0_0_8px_#b6ff80] motion-safe:animate-pulse" />
      </>
    ),
  },
  TITLE_GARDEN_KEEPER: {
    effect: "guardian-orbit",
    icon: "shield_with_heart",
    shell:
      "border-[#7ad5c4]/70 bg-gradient-to-r from-[#102f35] via-[#17594f] to-[#3d8b68] text-[#eafff7] shadow-[0_0_20px_rgba(64,196,159,.36)]",
    glow: "bg-[#67f0cf]/30",
    accent: (
      <>
        <span
          data-title-particle
          className="absolute right-2 top-1 h-1.5 w-1.5 rotate-45 bg-[#b9ffe9] shadow-[0_0_8px_#68ffd2] motion-safe:animate-ping"
        />
        <span
          data-title-particle
          className="absolute bottom-1 left-8 h-1 w-1 rounded-full bg-white shadow-[0_0_7px_#8dffe0] motion-safe:animate-pulse"
        />
        <span
          data-title-particle
          className="absolute left-3 top-1.5 h-1 w-1 rotate-45 bg-[#d5fff2] shadow-[0_0_7px_#68ffd2] motion-safe:animate-pulse [animation-delay:600ms]"
        />
        <span
          data-title-particle
          className="absolute bottom-1.5 right-10 h-1 w-1 rounded-full bg-[#e8fff8] shadow-[0_0_8px_#8dffe0] motion-safe:animate-ping [animation-delay:900ms]"
        />
        <span
          data-title-particle
          className="absolute left-1/3 top-0.5 h-0.5 w-0.5 rounded-full bg-white shadow-[0_0_6px_#8dffe0] motion-safe:animate-pulse [animation-delay:200ms]"
        />
        <span
          data-title-particle
          className="absolute bottom-0.5 left-1/2 h-0.5 w-0.5 rotate-45 bg-[#b9ffe9] shadow-[0_0_6px_#68ffd2] motion-safe:animate-ping [animation-delay:1100ms]"
        />
        <span
          data-title-particle
          className="absolute right-1/3 top-1 h-1 w-1 rounded-full bg-[#e8fff8]/80 shadow-[0_0_7px_#8dffe0] motion-safe:animate-pulse [animation-delay:1400ms]"
        />
        <span
          data-title-particle
          className="absolute bottom-1 left-2/3 h-0.5 w-0.5 rotate-45 bg-white shadow-[0_0_6px_#68ffd2] motion-safe:animate-pulse [animation-delay:1700ms]"
        />
      </>
    ),
  },
  TITLE_CARD_MASTER: {
    effect: "master-aurora",
    icon: "auto_awesome",
    shell:
      "border-[#e6c66c]/80 bg-gradient-to-r from-[#24163f] via-[#684a9b] to-[#bd8b32] text-[#fff8dc] shadow-[0_0_24px_rgba(190,132,255,.4),0_0_14px_rgba(255,206,78,.28)]",
    glow: "bg-[#d59bff]/35",
    accent: (
      <>
        <span className="absolute inset-y-0 -left-1/3 w-1/3 -skew-x-12 bg-gradient-to-r from-transparent via-white/35 to-transparent motion-safe:animate-[pulse_1.8s_ease-in-out_infinite]" />
        <span
          data-title-particle
          className="absolute right-2 top-1 h-2 w-2 rotate-45 bg-[#fff1a8] shadow-[0_0_10px_#ffd95a] motion-safe:animate-pulse"
        />
        <span
          data-title-particle
          className="absolute bottom-1 left-3 h-1 w-1 rotate-45 bg-white shadow-[0_0_8px_#d8a7ff] motion-safe:animate-ping"
        />
        <span
          data-title-particle
          className="absolute left-8 top-1 h-1.5 w-1.5 rotate-45 bg-[#f1d4ff] shadow-[0_0_8px_#d8a7ff] motion-safe:animate-pulse [animation-delay:250ms]"
        />
        <span
          data-title-particle
          className="absolute bottom-1.5 right-10 h-1 w-1 rounded-full bg-[#fff4b8] shadow-[0_0_8px_#ffd95a] motion-safe:animate-ping [animation-delay:450ms]"
        />
        <span
          data-title-particle
          className="absolute left-1/2 top-1 h-1 w-1 -translate-x-1/2 rotate-45 bg-white shadow-[0_0_7px_#ffffff] motion-safe:animate-pulse [animation-delay:700ms]"
        />
        <span
          data-title-particle
          className="absolute bottom-1 right-1/4 h-1.5 w-1.5 rotate-45 bg-[#edc8ff] shadow-[0_0_9px_#ba78ff] motion-safe:animate-pulse [animation-delay:950ms]"
        />
        <span
          data-title-particle
          className="absolute left-1/4 top-1.5 h-1 w-1 rounded-full bg-[#fff9d8] shadow-[0_0_8px_#ffd95a] motion-safe:animate-ping [animation-delay:1100ms]"
        />
        <span
          data-title-particle
          className="absolute bottom-1 right-5 h-1 w-1 rotate-45 bg-white shadow-[0_0_8px_#d8a7ff] motion-safe:animate-pulse [animation-delay:1300ms]"
        />
        <span
          data-title-particle
          className="absolute left-[15%] top-0.5 h-0.5 w-0.5 rounded-full bg-[#fff4b8] shadow-[0_0_6px_#ffd95a] motion-safe:animate-pulse [animation-delay:150ms]"
        />
        <span
          data-title-particle
          className="absolute bottom-0.5 left-[35%] h-1 w-1 rotate-45 bg-[#edc8ff]/80 shadow-[0_0_7px_#ba78ff] motion-safe:animate-ping [animation-delay:350ms]"
        />
        <span
          data-title-particle
          className="absolute right-[38%] top-0.5 h-0.5 w-0.5 rounded-full bg-white shadow-[0_0_6px_#ffffff] motion-safe:animate-pulse [animation-delay:550ms]"
        />
        <span
          data-title-particle
          className="absolute bottom-0.5 right-[15%] h-0.5 w-0.5 rotate-45 bg-[#fff1a8] shadow-[0_0_7px_#ffd95a] motion-safe:animate-pulse [animation-delay:800ms]"
        />
        <span
          data-title-particle
          className="absolute left-[45%] top-1.5 h-1 w-1 rounded-full bg-[#f1d4ff]/80 shadow-[0_0_7px_#d8a7ff] motion-safe:animate-ping [animation-delay:1050ms]"
        />
        <span
          data-title-particle
          className="absolute bottom-1 left-[60%] h-0.5 w-0.5 rotate-45 bg-white shadow-[0_0_6px_#ffffff] motion-safe:animate-pulse [animation-delay:1250ms]"
        />
        <span
          data-title-particle
          className="absolute right-[8%] top-1/2 h-1 w-1 -translate-y-1/2 rounded-full bg-[#fff4b8]/80 shadow-[0_0_7px_#ffd95a] motion-safe:animate-pulse [animation-delay:1500ms]"
        />
        <span
          data-title-particle
          className="absolute left-[22%] top-1/2 h-0.5 w-0.5 -translate-y-1/2 rotate-45 bg-[#edc8ff] shadow-[0_0_6px_#ba78ff] motion-safe:animate-ping [animation-delay:1750ms]"
        />
        <span
          data-title-particle
          className="absolute left-[8%] top-1 h-0.5 w-0.5 rounded-full bg-white/80 shadow-[0_0_5px_#ffffff] motion-safe:animate-pulse [animation-delay:100ms]"
        />
        <span
          data-title-particle
          className="absolute bottom-1 left-[18%] h-0.5 w-0.5 rotate-45 bg-[#fff1a8]/80 shadow-[0_0_6px_#ffd95a] motion-safe:animate-ping [animation-delay:300ms]"
        />
        <span
          data-title-particle
          className="absolute right-[48%] top-0.5 h-0.5 w-0.5 rounded-full bg-[#edc8ff]/75 shadow-[0_0_5px_#ba78ff] motion-safe:animate-pulse [animation-delay:500ms]"
        />
        <span
          data-title-particle
          className="absolute bottom-0.5 right-[42%] h-0.5 w-0.5 rotate-45 bg-white/80 shadow-[0_0_5px_#ffffff] motion-safe:animate-pulse [animation-delay:750ms]"
        />
        <span
          data-title-particle
          className="absolute right-[30%] top-1.5 h-0.5 w-0.5 rounded-full bg-[#fff4b8]/75 shadow-[0_0_6px_#ffd95a] motion-safe:animate-ping [animation-delay:1000ms]"
        />
        <span
          data-title-particle
          className="absolute bottom-1 right-[25%] h-0.5 w-0.5 rotate-45 bg-[#f1d4ff]/80 shadow-[0_0_6px_#d8a7ff] motion-safe:animate-pulse [animation-delay:1200ms]"
        />
        <span
          data-title-particle
          className="absolute right-[18%] top-0.5 h-0.5 w-0.5 rounded-full bg-white/75 shadow-[0_0_5px_#ffffff] motion-safe:animate-pulse [animation-delay:1450ms]"
        />
        <span
          data-title-particle
          className="absolute bottom-0.5 right-[6%] h-0.5 w-0.5 rotate-45 bg-[#fff1a8]/80 shadow-[0_0_6px_#ffd95a] motion-safe:animate-ping [animation-delay:1650ms]"
        />
      </>
    ),
  },
};

const FALLBACK_EFFECT: TitleEffect = {
  effect: "standard",
  icon: "workspace_premium",
  shell:
    "border-[#cfe1c8] bg-gradient-to-r from-[#edf3e9] to-[#dcebd5] text-brand-dark shadow-sm",
  glow: "bg-brand/15",
  accent: null,
};

export default function GachaTitleBadge({
  code,
  name,
  size = "compact",
  className = "",
}: {
  code: string;
  name: string;
  size?: "compact" | "showcase";
  className?: string;
}) {
  const effect = TITLE_EFFECTS[code] ?? FALLBACK_EFFECT;
  const sizing =
    size === "showcase"
      ? "min-h-12 px-5 py-2.5 text-sm sm:text-base"
      : "min-h-7 px-3 py-1 text-[11px] sm:text-xs";

  return (
    <span
      data-cosmetic-title={code}
      data-title-effect={effect.effect}
      className={`relative inline-flex max-w-full items-center gap-1.5 overflow-hidden rounded-full border font-black tracking-[-0.01em] ${effect.shell} ${sizing} ${className}`}
    >
      <span
        aria-hidden="true"
        className={`pointer-events-none absolute -left-3 top-1/2 h-9 w-9 -translate-y-1/2 rounded-full blur-lg motion-safe:animate-pulse ${effect.glow}`}
      />
      {effect.accent}
      <span
        aria-hidden="true"
        className={`material-symbols-outlined relative z-10 ${size === "showcase" ? "text-[20px]" : "text-[15px]"}`}
      >
        {effect.icon}
      </span>
      <span className="relative z-10 truncate">{name}</span>
    </span>
  );
}
