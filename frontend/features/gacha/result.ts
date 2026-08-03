import { GachaDrawDetail, GachaDrawItem, GachaRarity } from "@/lib/gacha-api";

export const GACHA_RARITY_LABEL: Record<GachaRarity, string> = {
  COMMON: "커먼",
  RARE: "레어",
  SUPER_RARE: "슈퍼 레어",
  HYPER_RARE: "하이퍼 레어",
  GOLDEN_RARE: "골든 레어",
};

export const GACHA_RARITY_RANK: Record<GachaRarity, number> = {
  COMMON: 1,
  RARE: 2,
  SUPER_RARE: 3,
  HYPER_RARE: 4,
  GOLDEN_RARE: 5,
};

export interface GroupedGachaResult {
  cardId: number;
  code: string;
  name: string;
  imageUrl: string | null;
  rarity: GachaRarity;
  count: number;
  newCount: number;
  downgradedCount: number;
  ownedCountAfter: number;
}

export function sortGachaItemsByRarity(items: GachaDrawItem[]) {
  return [...items].sort(
    (left, right) =>
      GACHA_RARITY_RANK[right.finalRarity] -
        GACHA_RARITY_RANK[left.finalRarity] || left.cardId - right.cardId,
  );
}

export function groupGachaDrawResults(
  draws: GachaDrawDetail[],
): GroupedGachaResult[] {
  const grouped = new Map<number, GroupedGachaResult>();

  for (const draw of draws) {
    for (const item of draw.items) {
      const current = grouped.get(item.cardId);
      if (!current) {
        grouped.set(item.cardId, {
          cardId: item.cardId,
          code: item.code,
          name: item.name,
          imageUrl: item.imageUrl,
          rarity: item.finalRarity,
          count: 1,
          newCount: item.new ? 1 : 0,
          downgradedCount: item.downgraded ? 1 : 0,
          ownedCountAfter: item.ownedCountAfter,
        });
        continue;
      }

      current.count += 1;
      current.newCount += item.new ? 1 : 0;
      current.downgradedCount += item.downgraded ? 1 : 0;
      current.ownedCountAfter = Math.max(
        current.ownedCountAfter,
        item.ownedCountAfter,
      );
    }
  }

  return [...grouped.values()].sort(
    (left, right) =>
      GACHA_RARITY_RANK[right.rarity] - GACHA_RARITY_RANK[left.rarity] ||
      left.cardId - right.cardId,
  );
}
