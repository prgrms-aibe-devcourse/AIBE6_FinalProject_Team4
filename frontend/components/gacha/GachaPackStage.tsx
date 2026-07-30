"use client";

import Image from "next/image";
import { useCallback, useEffect, useRef, useState } from "react";
import { playPackTearSound } from "@/features/gacha/audio";

const PACK_FRONT = "/cards/900001/0005fbe2-236e-5543-a4d4-69f8b57bd3f7.svg";
const PACK_BACK = "/cards/900003/ada07292-dc4b-58f0-ba69-1386fc040e56.svg";
const CARD_BACK = "/cards/900002/61de4f73-7b73-541c-9dfe-5bfc5ae6dc0c.svg";
const PACK_OPEN_DURATION_MS = 1280;
const CARD_PEEK_OFFSETS = [-22, -11, 0, 11, 22];
const SEAM_PARTICLES = Array.from({ length: 12 }, (_, index) => index);

interface GachaPackStageProps {
  muted?: boolean;
  packCount?: number;
  onOpen: () => void;
}

export default function GachaPackStage({
  muted = false,
  packCount = 1,
  onOpen,
}: GachaPackStageProps) {
  const multiple = packCount > 1;
  const [opening, setOpening] = useState(false);
  const openingRef = useRef(false);
  const timerRef = useRef<number | null>(null);

  useEffect(
    () => () => {
      if (timerRef.current !== null) {
        window.clearTimeout(timerRef.current);
      }
    },
    [],
  );

  const startOpening = useCallback(() => {
    if (openingRef.current) return;

    openingRef.current = true;
    setOpening(true);
    playPackTearSound(muted);
    const reducedMotion =
      window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false;
    timerRef.current = window.setTimeout(
      onOpen,
      reducedMotion ? 0 : PACK_OPEN_DURATION_MS,
    );
  }, [muted, onOpen]);

  return (
    <button
      type="button"
      aria-label={
        multiple ? `${packCount}팩 한번에 개봉하기` : "팩을 눌러 개봉하기"
      }
      aria-busy={opening}
      disabled={opening}
      onClick={startOpening}
      className="group flex select-none flex-col items-center rounded-[32px] px-6 py-4 motion-safe:animate-stageEnter focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#e8d77d] disabled:cursor-wait"
    >
      {multiple && (
        <p className="mb-3 text-xs font-black tracking-[0.3em] text-[#e4ce72]">
          {packCount} PACKS
        </p>
      )}

      <div className="relative aspect-[1122/1402] w-[min(72vw,320px)] [perspective:1200px]">
        <div
          className={`absolute inset-[8%] rounded-full blur-3xl transition duration-700 ${
            opening
              ? "scale-125 bg-[#f8dd79]/35"
              : "bg-[#b4d29a]/15 group-hover:bg-[#d7e9bd]/25"
          }`}
        />
        <div className="pointer-events-none absolute inset-x-[18%] bottom-[2%] h-10 rounded-full bg-black/60 blur-xl" />

        {multiple && !opening && (
          <>
            <div className="absolute inset-0 -translate-x-5 rotate-[-5deg] opacity-70">
              <Image
                src={PACK_FRONT}
                alt=""
                fill
                className="object-contain drop-shadow-[0_24px_24px_rgba(0,0,0,.4)]"
              />
            </div>
            <div className="absolute inset-0 translate-x-5 rotate-[5deg] opacity-70">
              <Image
                src={PACK_BACK}
                alt=""
                fill
                className="object-contain drop-shadow-[0_24px_24px_rgba(0,0,0,.4)]"
              />
            </div>
          </>
        )}

        {!opening && (
          <div className="absolute inset-0 motion-safe:animate-packTurn [transform-style:preserve-3d]">
            <div className="absolute inset-0 [backface-visibility:hidden]">
              <Image
                src={PACK_FRONT}
                alt={multiple ? `${packCount}개 카드팩` : "시즌 1 카드팩 앞면"}
                fill
                priority
                className="object-contain drop-shadow-[0_28px_30px_rgba(0,0,0,.55)]"
              />
            </div>
            <div
              className="absolute inset-0 [backface-visibility:hidden]"
              style={{ transform: "rotateY(180deg)" }}
            >
              <Image
                src={PACK_BACK}
                alt={multiple ? "" : "시즌 1 카드팩 뒷면"}
                fill
                priority
                className="object-contain drop-shadow-[0_28px_30px_rgba(0,0,0,.55)]"
              />
            </div>
          </div>
        )}

        {opening && (
          <div className="absolute inset-0 motion-safe:animate-packOpening">
            <div className="absolute inset-x-[22%] bottom-[18%] top-[26%]">
              {CARD_PEEK_OFFSETS.map((offset, index) => (
                <div
                  key={offset}
                  className="absolute inset-0 origin-bottom"
                  style={{
                    transform: `translateX(${offset}px) rotate(${offset / 4}deg)`,
                  }}
                >
                  <div
                    className="h-full w-full motion-safe:animate-packCardEmerge"
                    style={{
                      animationDelay: `${index * 24}ms`,
                    }}
                  >
                    <div className="relative h-full w-full overflow-hidden rounded-[12px] shadow-[0_18px_36px_rgba(0,0,0,.48)]">
                      <Image
                        src={CARD_BACK}
                        alt=""
                        fill
                        priority={index === 2}
                        className="object-contain"
                      />
                    </div>
                  </div>
                </div>
              ))}
            </div>

            <div
              className="absolute inset-0 drop-shadow-[0_28px_30px_rgba(0,0,0,.55)] motion-safe:animate-packBodyOpen"
              style={{ clipPath: "inset(18% 0 0 0)" }}
            >
              <Image src={PACK_FRONT} alt="" fill className="object-contain" />
            </div>

            <div
              className="absolute inset-0 origin-[16%_18%] drop-shadow-[0_18px_18px_rgba(0,0,0,.45)] motion-safe:animate-packAutoTear"
              style={{ clipPath: "inset(0 0 80% 0)" }}
            >
              <Image src={PACK_FRONT} alt="" fill className="object-contain" />
            </div>

            <div className="pointer-events-none absolute left-[10%] right-[9%] top-[17.7%] h-[3px] origin-left bg-gradient-to-r from-transparent via-[#fff3a5] to-white shadow-[0_0_18px_rgba(255,241,154,.95)] motion-safe:animate-packTearLine" />
            <span className="pointer-events-none absolute left-[9%] top-[15.4%] h-4 w-4 rounded-full border border-[#fff4b2]/70 bg-[#fff0a0]/80 shadow-[0_0_16px_rgba(255,240,149,.9)] motion-safe:animate-packTearHandle" />

            {SEAM_PARTICLES.map((particle) => (
              <span
                key={particle}
                className="pointer-events-none absolute left-[12%] top-[17.5%] h-1.5 w-1.5 rounded-full bg-[#fff2a4] shadow-[0_0_10px_rgba(255,240,149,.95)] motion-safe:animate-packSeamSpark"
                style={{
                  animationDelay: `${180 + particle * 42}ms`,
                  marginLeft: `${particle * 6.2}%`,
                }}
              />
            ))}
          </div>
        )}
      </div>

      <span className="mt-6 rounded-full bg-white px-7 py-3.5 font-black text-[#253822] shadow-lg transition group-hover:-translate-y-0.5 group-hover:bg-[#f4f8ed]">
        {opening
          ? "팩을 뜯는 중..."
          : multiple
            ? `${packCount}팩 한번에 개봉하기`
            : "팩을 눌러 개봉하기"}
      </span>
      <span className="mt-3 text-xs font-bold text-white/50">
        {opening
          ? "절취선을 따라 포장이 열리고 있어요"
          : "한 번 누르면 팩을 뜯어 카드를 확인합니다"}
      </span>
    </button>
  );
}
