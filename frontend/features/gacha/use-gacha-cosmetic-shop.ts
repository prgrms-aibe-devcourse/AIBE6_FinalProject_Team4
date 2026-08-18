"use client";

import { useState } from "react";
import { commerceErrorMessage } from "@/features/commerce/presentation";
import { useGachaCosmetics } from "@/features/gacha/use-gacha-cosmetics";
import {
  equipGachaCosmetic,
  GachaCosmetic,
  purchaseGachaCosmetic,
  unequipGachaCosmetic,
} from "@/lib/gacha-api";
import { useUI } from "@/lib/ui";

export function useGachaCosmeticShop(accessToken: string) {
  const { showToast } = useUI();
  const { data, setData, refresh } = useGachaCosmetics();
  const [busy, setBusy] = useState(false);

  const purchase = async (cosmetic: GachaCosmetic) => {
    if (busy) return;
    setBusy(true);
    try {
      const next = await purchaseGachaCosmetic(
        cosmetic.code,
        accessToken,
        crypto.randomUUID(),
      );
      setData(next);
      showToast(`${cosmetic.name}을(를) 해금했어요.`);
    } catch (error) {
      showToast(commerceErrorMessage(error, "해금하지 못했어요."), "err");
    } finally {
      setBusy(false);
    }
  };

  const toggleEquip = async (cosmetic: GachaCosmetic) => {
    if (busy) return;
    setBusy(true);
    try {
      const next = cosmetic.equipped
        ? await unequipGachaCosmetic(cosmetic.type, accessToken)
        : await equipGachaCosmetic(cosmetic.code, accessToken);
      setData(next);
      showToast(cosmetic.equipped ? "장착을 해제했어요." : "장착했어요.");
    } catch (error) {
      showToast(
        commerceErrorMessage(error, "장착 상태를 바꾸지 못했어요."),
        "err",
      );
    } finally {
      setBusy(false);
    }
  };

  return { data, refresh, busy, purchase, toggleEquip };
}
