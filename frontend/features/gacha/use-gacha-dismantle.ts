"use client";

import { useMemo, useState } from "react";
import { commerceErrorMessage } from "@/features/commerce/presentation";
import { dismantleGachaCards, GachaCollectionCard } from "@/lib/gacha-api";
import { useUI } from "@/lib/ui";

export const MAX_DISMANTLE_QUANTITY = 20;

const RARITY_PRIORITY: Record<string, number> = {
  COMMON: 0,
  RARE: 1,
  SUPER_RARE: 2,
};

export function useGachaDismantle({
  accessToken,
  collection,
  onCollectionRefresh,
  onWalletRefresh,
}: {
  accessToken: string;
  collection: GachaCollectionCard[];
  onCollectionRefresh: () => Promise<void>;
  onWalletRefresh: () => Promise<void>;
}) {
  const { showToast } = useUI();
  const [quantities, setQuantities] = useState<Record<number, number>>({});
  const [busy, setBusy] = useState(false);
  const dismantleableCards = useMemo(
    () => collection.filter((card) => card.dismantleableCount > 0),
    [collection],
  );
  const selected = useMemo(
    () =>
      dismantleableCards
        .map((card) => ({
          card,
          quantity: Math.min(
            Math.max(quantities[card.id] ?? 0, 0),
            card.dismantleableCount,
          ),
        }))
        .filter((item) => item.quantity > 0),
    [dismantleableCards, quantities],
  );
  const selectedCount = selected.reduce((sum, item) => sum + item.quantity, 0);
  const expectedShards = selected.reduce(
    (sum, item) => sum + item.quantity * item.card.shardPerCard,
    0,
  );

  const selectLowestRarityCards = () => {
    let remaining = MAX_DISMANTLE_QUANTITY;
    const next: Record<number, number> = {};

    [...dismantleableCards]
      .sort(
        (left, right) =>
          (RARITY_PRIORITY[left.rarity] ?? 99) -
            (RARITY_PRIORITY[right.rarity] ?? 99) ||
          left.displayOrder - right.displayOrder ||
          left.id - right.id,
      )
      .forEach((card) => {
        if (remaining <= 0) return;
        const quantity = Math.min(card.dismantleableCount, remaining);
        if (quantity > 0) {
          next[card.id] = quantity;
          remaining -= quantity;
        }
      });

    setQuantities(next);
  };

  const decrement = (cardId: number) => {
    setQuantities((current) => ({
      ...current,
      [cardId]: Math.max(0, (current[cardId] ?? 0) - 1),
    }));
  };

  const increment = (card: GachaCollectionCard) => {
    setQuantities((current) => {
      const currentTotal = Object.values(current).reduce(
        (sum, value) => sum + Math.max(0, value),
        0,
      );
      if (currentTotal >= MAX_DISMANTLE_QUANTITY) return current;
      return {
        ...current,
        [card.id]: Math.min(
          card.dismantleableCount,
          (current[card.id] ?? 0) + 1,
        ),
      };
    });
  };

  const dismantle = async () => {
    if (!selected.length || busy) return;
    if (selectedCount > MAX_DISMANTLE_QUANTITY) {
      showToast("한 번에 최대 20장까지 분해할 수 있어요.", "err");
      return;
    }
    setBusy(true);
    try {
      const result = await dismantleGachaCards(
        selected.map(({ card, quantity }) => ({ cardId: card.id, quantity })),
        accessToken,
        crypto.randomUUID(),
      );
      setQuantities({});
      await onCollectionRefresh();
      await onWalletRefresh();
      showToast(`${result.earnedShards}조각을 획득했어요.`);
    } catch (error) {
      showToast(
        commerceErrorMessage(error, "카드를 분해하지 못했어요."),
        "err",
      );
    } finally {
      setBusy(false);
    }
  };

  return {
    busy,
    dismantleableCards,
    quantities,
    selected,
    selectedCount,
    expectedShards,
    selectLowestRarityCards,
    decrement,
    increment,
    dismantle,
  };
}
