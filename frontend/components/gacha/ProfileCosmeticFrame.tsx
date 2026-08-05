import { useId, type ReactNode } from "react";

const PROFILE_FRAME_STYLE: Record<string, string> = {
  BORDER_SPROUT_VINE:
    "bg-[conic-gradient(from_20deg,#dff5b9,#3f8f46,#9ed36e,#276b38,#dff5b9)] shadow-[0_0_0_2px_#effbdc,0_0_0_4px_#4e8d45,0_7px_22px_rgba(36,105,52,.42)]",
  BORDER_BLOOM_GARDEN:
    "bg-[conic-gradient(from_25deg,#fff3f8,#f5a9ce,#d867a8,#ffd8e9,#ee8abb,#fff3f8)] shadow-[0_0_0_2px_#fff5fa,0_0_0_4px_#d86aa8,0_0_26px_rgba(229,103,167,.55)]",
  BORDER_GOLDEN_HARVEST:
    "bg-[conic-gradient(from_15deg,#fffbd0,#d58b08,#fff07a,#9d5b00,#ffd84b,#fffbd0)] shadow-[0_0_0_2px_#fff6a6,0_0_0_5px_#a26506,0_0_32px_rgba(255,190,29,.82)]",
};

function LeafCluster({ className }: { className: string }) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 64 64"
      className={`pointer-events-none absolute z-30 overflow-visible drop-shadow-[0_3px_3px_rgba(23,74,31,.35)] ${className}`}
    >
      <path
        d="M8 46C9 24 23 12 43 9 42 28 31 43 8 46Z"
        fill="#55a84d"
        stroke="#286c36"
        strokeWidth="2.5"
      />
      <path d="M10 43 38 15" stroke="#d9f4a8" strokeWidth="2.5" />
      <path
        d="M27 52C29 36 40 27 56 27 54 42 44 51 27 52Z"
        fill="#9bd263"
        stroke="#397b38"
        strokeWidth="2.5"
      />
      <path d="m31 49 20-17" stroke="#efffc1" strokeWidth="2" />
    </svg>
  );
}

function CherryBlossom({ className }: { className: string }) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 64 64"
      className={`pointer-events-none absolute z-30 overflow-visible drop-shadow-[0_3px_4px_rgba(159,52,108,.34)] ${className}`}
    >
      <g fill="#ffd6e8" stroke="#df6fa8" strokeWidth="2">
        <ellipse cx="32" cy="17" rx="10" ry="16" />
        <ellipse cx="46" cy="28" rx="10" ry="16" transform="rotate(72 46 28)" />
        <ellipse
          cx="41"
          cy="45"
          rx="10"
          ry="16"
          transform="rotate(144 41 45)"
        />
        <ellipse
          cx="23"
          cy="45"
          rx="10"
          ry="16"
          transform="rotate(216 23 45)"
        />
        <ellipse
          cx="18"
          cy="28"
          rx="10"
          ry="16"
          transform="rotate(288 18 28)"
        />
      </g>
      <circle
        cx="32"
        cy="32"
        r="7"
        fill="#ffd45e"
        stroke="#e58b47"
        strokeWidth="2"
      />
      <circle cx="29" cy="29" r="1.5" fill="#fff8bd" />
      <circle cx="36" cy="31" r="1.5" fill="#fff8bd" />
    </svg>
  );
}

function GoldenAppleCrest() {
  const gradientId = useId().replace(/:/g, "");

  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 72 72"
      className="pointer-events-none absolute -top-[30%] left-1/2 z-30 h-[58%] w-[58%] -translate-x-1/2 overflow-visible drop-shadow-[0_4px_7px_rgba(112,63,0,.58)] motion-safe:animate-profileAppleFloat"
    >
      <defs>
        <linearGradient id={gradientId} x1="15%" y1="10%" x2="85%" y2="90%">
          <stop offset="0" stopColor="#fff8a8" />
          <stop offset="0.45" stopColor="#ffc928" />
          <stop offset="1" stopColor="#c87900" />
        </linearGradient>
      </defs>
      <path
        d="M36 25c-8-11-25-7-27 8-2 14 8 28 18 27 4-.5 6-2 9-2s5 1.5 9 2c10 1 20-13 18-27-2-15-19-19-27-8Z"
        fill={`url(#${gradientId})`}
        stroke="#8b5100"
        strokeWidth="3"
      />
      <path
        d="M36 25c-1-8 2-14 7-18"
        fill="none"
        stroke="#704309"
        strokeWidth="4"
        strokeLinecap="round"
      />
      <path
        d="M41 13C47 4 58 5 62 7c-3 9-12 13-21 6Z"
        fill="#76a93f"
        stroke="#466b22"
        strokeWidth="2.5"
      />
      <path
        d="M21 34c3-7 9-9 14-8"
        fill="none"
        stroke="#fffbd0"
        strokeWidth="4"
        strokeLinecap="round"
        opacity=".8"
      />
    </svg>
  );
}

export default function ProfileCosmeticFrame({
  borderCode,
  children,
  className = "",
}: {
  borderCode?: string | null;
  children: ReactNode;
  className?: string;
}) {
  const style = borderCode
    ? `p-[3px] ${PROFILE_FRAME_STYLE[borderCode] ?? "bg-line"}`
    : "bg-transparent p-[3px]";

  return (
    <div
      data-profile-border={borderCode ?? undefined}
      className={`relative inline-grid shrink-0 place-items-center rounded-full ${style} ${className}`}
    >
      {borderCode === "BORDER_SPROUT_VINE" ? (
        <>
          <span className="pointer-events-none absolute inset-[-9%] z-20 rounded-full border-2 border-dashed border-[#8dcc69]/85 motion-safe:animate-profileRingTurn" />
          <LeafCluster className="-left-[27%] -top-[20%] h-[58%] w-[58%] -rotate-12 motion-safe:animate-profileLeafSway" />
          <LeafCluster className="-bottom-[24%] -right-[25%] h-[52%] w-[52%] rotate-[168deg] motion-safe:animate-profileLeafSway [animation-delay:-1.4s]" />
        </>
      ) : null}
      {borderCode === "BORDER_BLOOM_GARDEN" ? (
        <>
          <span className="pointer-events-none absolute inset-[-8%] z-20 rounded-full border border-[#ffd8ea] shadow-[inset_0_0_9px_rgba(255,255,255,.9)]" />
          <CherryBlossom className="-left-[25%] -top-[27%] h-[55%] w-[55%] -rotate-12 motion-safe:animate-profileBlossomBloom" />
          <CherryBlossom className="-bottom-[22%] -right-[22%] h-[42%] w-[42%] rotate-12 motion-safe:animate-profileBlossomBloom [animation-delay:-1.6s]" />
          <span className="pointer-events-none absolute -right-[20%] top-[20%] z-30 h-[14%] w-[8%] rotate-[38deg] rounded-[90%_10%_90%_10%] bg-[#ffc4dd] shadow-[0_0_7px_#ffb2d3] motion-safe:animate-profilePetalDrift" />
          <span className="pointer-events-none absolute -bottom-[17%] left-[12%] z-30 h-[11%] w-[7%] -rotate-[28deg] rounded-[90%_10%_90%_10%] bg-[#ffe1ed] shadow-[0_0_6px_#ffbad8] motion-safe:animate-profilePetalDrift [animation-delay:-1.55s]" />
        </>
      ) : null}
      {borderCode === "BORDER_GOLDEN_HARVEST" ? (
        <>
          <span className="pointer-events-none absolute inset-[-11%] z-20 rounded-full border-[3px] border-double border-[#ffdb43] shadow-[inset_0_0_10px_#fff8b5,0_0_14px_rgba(255,198,35,.75)] motion-safe:animate-profileRingTurn" />
          <GoldenAppleCrest />
          <span className="pointer-events-none absolute inset-[-4%] z-20 rounded-full bg-[linear-gradient(110deg,transparent_22%,rgba(255,255,255,.95)_43%,transparent_61%)] bg-[length:250%_100%] opacity-90 motion-safe:animate-profileShine" />
          <span className="pointer-events-none absolute -right-[18%] top-[12%] z-30 h-[15%] w-[15%] rotate-45 bg-[#fff9bd] shadow-[0_0_13px_#ffd52e] motion-safe:animate-profileTwinkle" />
          <span className="pointer-events-none absolute -bottom-[13%] left-[4%] z-30 h-[10%] w-[10%] rotate-45 bg-white shadow-[0_0_11px_#ffe048] motion-safe:animate-profileTwinkle [animation-delay:-.75s]" />
          <span className="pointer-events-none absolute bottom-[4%] -right-[11%] z-30 h-[7%] w-[7%] rotate-45 bg-[#fff3a0] shadow-[0_0_9px_#ffc51b] motion-safe:animate-profileTwinkle [animation-delay:-1.1s]" />
        </>
      ) : null}
      <div className="relative z-10 block h-full w-full overflow-hidden rounded-full">
        {children}
      </div>
    </div>
  );
}
