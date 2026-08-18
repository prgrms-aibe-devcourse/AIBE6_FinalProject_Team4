import Image from "next/image";
import {
  DisplayCard,
  RARITY_LABEL,
  RARITY_ORDER,
  RARITY_PANEL,
  RARITY_STYLE,
} from "@/features/gacha/GachaCardPresentation";
import { GachaRarity } from "@/lib/gacha-api";

interface GachaCatalogSectionProps {
  cards: DisplayCard[];
  unlockedCount: number;
  totalCount: number;
  expandedRarities: Set<GachaRarity>;
  onToggleRarity: (rarity: GachaRarity) => void;
  onSelectCard: (card: DisplayCard) => void;
}

export default function GachaCatalogSection({
  cards,
  unlockedCount,
  totalCount,
  expandedRarities,
  onToggleRarity,
  onSelectCard,
}: GachaCatalogSectionProps) {
  return (
    <section aria-labelledby="catalog-title">
      <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="text-xs font-bold uppercase tracking-[0.18em] text-brand">
            Collection book
          </p>
          <h2 id="catalog-title" className="mt-1 text-2xl font-black text-ink">
            카드 도감
          </h2>
          <p className="mt-1 text-sm text-sub">
            등급을 눌러 펼쳐보세요. 모든 도감은 처음에는 접혀 있습니다.
          </p>
        </div>
        <div className="rounded-full bg-white px-4 py-2 text-sm font-bold text-sub shadow-sm">
          해금 {unlockedCount} / {totalCount}
        </div>
      </div>

      <div className="space-y-3">
        {RARITY_ORDER.map((rarity) => {
          const rarityCards = cards.filter((card) => card.rarity === rarity);
          const rarityUnlockedCount = rarityCards.filter(
            (card) => card.unlocked,
          ).length;
          const expanded = expandedRarities.has(rarity);
          return (
            <section
              key={rarity}
              className={`overflow-hidden rounded-2xl border ${RARITY_PANEL[rarity]}`}
            >
              <button
                type="button"
                aria-expanded={expanded}
                aria-controls={`catalog-${rarity}`}
                aria-label={`${RARITY_LABEL[rarity]} 도감 ${expanded ? "접기" : "펼치기"}`}
                onClick={() => onToggleRarity(rarity)}
                className="flex w-full items-center justify-between gap-4 px-5 py-4 text-left"
              >
                <span className="flex items-center gap-3">
                  <span
                    className={`rounded-full px-3 py-1.5 text-xs font-black ${RARITY_STYLE[rarity]}`}
                  >
                    {RARITY_LABEL[rarity]}
                  </span>
                  <span className="text-sm font-bold text-sub">
                    {rarityUnlockedCount}/{rarityCards.length} 해금
                  </span>
                </span>
                <span
                  className={`material-symbols-outlined transition-transform ${expanded ? "rotate-180" : ""}`}
                >
                  expand_more
                </span>
              </button>

              {expanded ? (
                <div
                  id={`catalog-${rarity}`}
                  className="grid grid-cols-2 gap-3 border-t border-black/5 p-4 sm:grid-cols-3 lg:grid-cols-5"
                >
                  {rarityCards.map((card) => (
                    <article
                      key={card.id}
                      className="overflow-hidden rounded-2xl border border-white/80 bg-white/80 shadow-sm"
                    >
                      <button
                        type="button"
                        disabled={!card.unlocked || !card.imageUrl}
                        onClick={() => onSelectCard(card)}
                        aria-label={
                          card.unlocked
                            ? `${card.name} 원본 일러스트 크게 보기`
                            : `${card.name} 미획득 카드`
                        }
                        className="relative block aspect-[5/7] w-full overflow-hidden bg-[#dfe4da] disabled:cursor-not-allowed"
                      >
                        {card.unlocked && card.imageUrl ? (
                          <>
                            <Image
                              src={card.imageUrl}
                              alt={`${card.name} 카드 일러스트`}
                              fill
                              sizes="(max-width: 640px) 50vw, (max-width: 1024px) 33vw, 20vw"
                              className="object-cover transition duration-300 hover:scale-[1.03]"
                            />
                            <span className="absolute bottom-2 right-2 rounded-full bg-black/55 p-1.5 text-white backdrop-blur">
                              <span className="material-symbols-outlined block text-[18px]">
                                zoom_in
                              </span>
                            </span>
                          </>
                        ) : (
                          <span className="absolute inset-0 flex flex-col items-center justify-center bg-[radial-gradient(circle_at_50%_35%,#eef2e9,#cdd4c7)] text-[#8c9686]">
                            <span className="material-symbols-outlined text-[42px]">
                              lock
                            </span>
                            <span className="mt-2 text-[11px] font-black tracking-[0.15em]">
                              NOT ACQUIRED
                            </span>
                          </span>
                        )}
                      </button>
                      <div className="p-3">
                        <p className="truncate text-sm font-black text-ink">
                          {card.name}
                        </p>
                        <p className="mt-1 line-clamp-2 min-h-8 text-[11px] leading-4 text-sub">
                          {card.description ?? "설명이 준비 중입니다."}
                        </p>
                      </div>
                    </article>
                  ))}
                </div>
              ) : null}
            </section>
          );
        })}
      </div>
    </section>
  );
}
