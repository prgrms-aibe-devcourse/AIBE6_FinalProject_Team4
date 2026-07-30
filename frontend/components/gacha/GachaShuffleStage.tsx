import Image from "next/image";

const CARD_BACK = "/cards/900002/00e26b51-bded-5925-bb6c-eed61a8df37a.svg";
const CARD_OFFSETS = [-34, -17, 0, 17, 34];
const CARD_ROTATIONS = [-10, -5, 0, 5, 10];

interface GachaShuffleStageProps {
  packCount?: number;
  onComplete: () => void;
  completeLabel?: string;
}

export default function GachaShuffleStage({
  packCount = 1,
  onComplete,
  completeLabel = "섞인 카드 펼치기",
}: GachaShuffleStageProps) {
  return (
    <section
      aria-labelledby="shuffle-title"
      className="flex w-full flex-col items-center text-center"
    >
      <p className="text-xs font-black tracking-[0.3em] text-[#e4ce72]">
        {packCount > 1 ? `${packCount} PACKS` : "CARD SHUFFLE"}
      </p>
      <h1 id="shuffle-title" className="mt-2 text-2xl font-black">
        카드의 순서를 섞고 있어요
      </h1>
      <p className="mt-2 text-sm text-white/55">
        결과는 이미 서버에 안전하게 확정되어 있습니다.
      </p>

      <div
        className="relative mt-9 h-[340px] w-[min(82vw,420px)]"
        aria-label="카드 셔플 연출"
      >
        <div className="absolute inset-x-[12%] bottom-4 h-12 rounded-full bg-black/60 blur-2xl" />
        {CARD_OFFSETS.map((offset, index) => (
          <div
            key={offset}
            className="absolute left-1/2 top-1/2 aspect-[1122/1402] w-[min(43vw,190px)] -translate-x-1/2 -translate-y-1/2"
            style={{
              marginLeft: `${offset}px`,
              transform: `translate(-50%, -50%) rotate(${CARD_ROTATIONS[index]}deg)`,
            }}
          >
            <div
              className="relative h-full w-full motion-safe:animate-cardShuffle"
              style={{ animationDelay: `${index * 90}ms` }}
            >
              <Image
                src={CARD_BACK}
                alt=""
                fill
                priority={index === 2}
                className="object-contain drop-shadow-[0_20px_24px_rgba(0,0,0,.55)]"
              />
            </div>
          </div>
        ))}
        <div className="pointer-events-none absolute left-1/2 top-1/2 h-44 w-44 -translate-x-1/2 -translate-y-1/2 rounded-full bg-[#e5d36c]/10 blur-3xl motion-safe:animate-glowPulse" />
      </div>

      <button
        type="button"
        onClick={onComplete}
        className="mt-5 rounded-full bg-white px-7 py-3.5 font-black text-[#253822] shadow-[0_12px_35px_rgba(0,0,0,.3)] transition hover:-translate-y-0.5 hover:bg-[#f5f8ef] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#e8d77d]"
      >
        {completeLabel}
      </button>
    </section>
  );
}
