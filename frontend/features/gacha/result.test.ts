import { describe, expect, it } from "vitest";
import { groupGachaDrawResults } from "./result";
import { GachaDrawDetail, GachaDrawItem, GachaRarity } from "@/lib/gacha-api";

function item(
  cardId: number,
  name: string,
  rarity: GachaRarity,
  ownedCountAfter: number,
): GachaDrawItem {
  return {
    sequence: cardId,
    cardId,
    code: `CARD_${cardId}`,
    name,
    imageUrl: `/cards/${cardId}/card.png`,
    rolledRarity: rarity,
    finalRarity: rarity,
    downgraded: false,
    new: ownedCountAfter === 1,
    ownedCountAfter,
    nextMilestone: null,
    goldenOriginRank: rarity === "GOLDEN_RARE" ? ownedCountAfter : null,
  };
}

function draw(drawId: number, items: GachaDrawItem[]): GachaDrawDetail {
  return {
    drawId,
    status: "COMPLETED",
    sourceType: "ADMIN",
    rateVersion: 1,
    createdAt: "2026-07-30T03:00:00Z",
    completedAt: "2026-07-30T03:00:01Z",
    resultViewedAt: null,
    items,
  };
}

describe("groupGachaDrawResults", () => {
  it("같은 카드를 합치고 골든부터 등급 내림차순으로 정렬한다", () => {
    const results = groupGachaDrawResults([
      draw(1, [item(1, "커먼 카드", "COMMON", 1)]),
      draw(2, [
        item(1, "커먼 카드", "COMMON", 2),
        item(2, "골든 카드", "GOLDEN_RARE", 1),
        item(3, "레어 카드", "RARE", 1),
      ]),
    ]);

    expect(results.map((result) => result.name)).toEqual([
      "골든 카드",
      "레어 카드",
      "커먼 카드",
    ]);
    expect(results.find((result) => result.cardId === 1)).toMatchObject({
      count: 2,
      ownedCountAfter: 2,
      newCount: 1,
    });
  });
});
