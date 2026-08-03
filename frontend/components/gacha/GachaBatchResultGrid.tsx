import Image from "next/image";
import {
  GACHA_RARITY_LABEL,
  GroupedGachaResult,
} from "@/features/gacha/result";

const RARITY_STYLE: Record<GroupedGachaResult["rarity"], string> = {
  COMMON: "border-white/10 bg-white/[0.045]",
  RARE: "border-[#87b8d8]/35 bg-[#87b8d8]/[0.08]",
  SUPER_RARE: "border-[#af86e8]/40 bg-[#af86e8]/[0.09]",
  HYPER_RARE: "border-[#ef8fbd]/45 bg-[#ef8fbd]/[0.1]",
  GOLDEN_RARE:
    "border-[#f3d468]/65 bg-[linear-gradient(145deg,rgba(243,212,104,.18),rgba(255,255,255,.045))]",
};

export default function GachaBatchResultGrid({
  results,
}: {
  results: GroupedGachaResult[];
}) {
  return (
    <div className="mx-auto mt-8 grid max-w-[1050px] grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
      {results.map((result, index) => (
        <article
          key={result.cardId}
          className={`rounded-[22px] border p-2.5 shadow-[0_16px_32px_rgba(0,0,0,.18)] motion-safe:animate-upIn ${RARITY_STYLE[result.rarity]}`}
          style={{ animationDelay: `${Math.min(index, 12) * 45}ms` }}
        >
          <div className="relative aspect-[1122/1402] overflow-hidden rounded-[15px] bg-black/15">
            {result.imageUrl && (
              <Image
                src={result.imageUrl}
                alt={result.name}
                fill
                sizes="(max-width: 640px) 45vw, (max-width: 1024px) 30vw, 190px"
                className="object-contain"
              />
            )}
            {result.newCount > 0 && (
              <span className="absolute left-2 top-2 rounded-full bg-[#ffda52] px-2 py-1 text-[10px] font-black text-[#4e3a00]">
                NEW
              </span>
            )}
            <span className="absolute bottom-2 right-2 rounded-full bg-black/70 px-2.5 py-1 text-xs font-black text-white backdrop-blur">
              +{result.count}
            </span>
          </div>
          <div className="px-1 pb-1 pt-2.5">
            <p className="truncate text-sm font-extrabold">{result.name}</p>
            <p className="mt-0.5 text-[11px] font-bold text-[#dfca72]">
              {GACHA_RARITY_LABEL[result.rarity]}
            </p>
            <p className="mt-2 text-xs text-white/60">
              최종 보유 {result.ownedCountAfter}장
            </p>
            {result.downgradedCount > 0 && (
              <p className="mt-1 text-[11px] text-white/45">
                골든 구간 대체 {result.downgradedCount}회
              </p>
            )}
          </div>
        </article>
      ))}
    </div>
  );
}
