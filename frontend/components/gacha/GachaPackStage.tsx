import Image from "next/image";

const PACK_FRONT = "/cards/900001/0005fbe2-236e-5543-a4d4-69f8b57bd3f7.svg";
const PACK_BACK = "/cards/900003/ada07292-dc4b-58f0-ba69-1386fc040e56.svg";

interface GachaPackStageProps {
  packCount?: number;
  onOpen: () => void;
}

export default function GachaPackStage({
  packCount = 1,
  onOpen,
}: GachaPackStageProps) {
  const multiple = packCount > 1;

  return (
    <button
      type="button"
      onClick={onOpen}
      className="group flex flex-col items-center rounded-[32px] px-6 py-4 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#e8d77d]"
    >
      {multiple && (
        <p className="mb-3 text-xs font-black tracking-[0.3em] text-[#e4ce72]">
          {packCount} PACKS
        </p>
      )}
      <div className="relative aspect-[1122/1402] w-[min(72vw,320px)] [perspective:1200px]">
        <div className="absolute inset-[10%] rounded-full bg-[#b4d29a]/15 blur-3xl transition group-hover:bg-[#d7e9bd]/25" />

        {multiple && (
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
      </div>
      <span className="mt-6 rounded-full bg-white px-7 py-3.5 font-black text-[#253822] shadow-lg transition group-hover:-translate-y-0.5 group-hover:bg-[#f4f8ed]">
        {multiple ? `${packCount}팩 한번에 개봉하기` : "팩을 눌러 개봉하기"}
      </span>
      <span className="mt-3 text-xs font-bold text-white/45">
        팩이 뒤집히면 뒷면도 확인할 수 있어요
      </span>
    </button>
  );
}
