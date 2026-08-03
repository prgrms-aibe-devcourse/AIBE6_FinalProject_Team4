"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  GachaCosmetic,
  GachaMyCosmetics,
  getMyGachaCosmetics,
} from "@/lib/gacha-api";

export const GACHA_COSMETICS_CHANGED = "kwb:gacha-cosmetics-changed";

export function notifyGachaCosmeticsChanged() {
  window.dispatchEvent(new Event(GACHA_COSMETICS_CHANGED));
}

export function useGachaCosmetics(accessToken?: string | null) {
  const [data, setData] = useState<GachaMyCosmetics | null>(null);

  const refresh = useCallback(async () => {
    if (!accessToken) {
      setData(null);
      return;
    }
    const next = await getMyGachaCosmetics(accessToken);
    setData(next);
  }, [accessToken]);

  useEffect(() => {
    const controller = new AbortController();
    if (!accessToken) {
      setData(null);
      return () => controller.abort();
    }
    getMyGachaCosmetics(accessToken, controller.signal)
      .then(setData)
      .catch(() => undefined);
    const handleChanged = () => void refresh();
    window.addEventListener(GACHA_COSMETICS_CHANGED, handleChanged);
    return () => {
      controller.abort();
      window.removeEventListener(GACHA_COSMETICS_CHANGED, handleChanged);
    };
  }, [accessToken, refresh]);

  const equipped = useMemo(
    () => data?.cosmetics.filter((item) => item.equipped) ?? [],
    [data],
  );
  const title = equipped.find((item) => item.type === "TITLE") ?? null;
  const border = equipped.find((item) => item.type === "BORDER") ?? null;

  return { data, setData, title, border, refresh } as const;
}

export function equippedCosmetic(
  cosmetics: GachaCosmetic[],
  type: GachaCosmetic["type"],
) {
  return cosmetics.find((item) => item.type === type && item.equipped) ?? null;
}
