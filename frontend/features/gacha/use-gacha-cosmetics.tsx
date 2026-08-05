"use client";

import {
  createContext,
  Dispatch,
  ReactNode,
  SetStateAction,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import {
  GachaCosmetic,
  GachaMyCosmetics,
  getMyGachaCosmetics,
} from "@/lib/gacha-api";
import { useStore } from "@/lib/store";

interface GachaCosmeticsContextValue {
  data: GachaMyCosmetics | null;
  setData: Dispatch<SetStateAction<GachaMyCosmetics | null>>;
  title: GachaCosmetic | null;
  border: GachaCosmetic | null;
  refresh: () => Promise<void>;
}

const GachaCosmeticsContext = createContext<GachaCosmeticsContextValue | null>(
  null,
);

export function GachaCosmeticsProvider({ children }: { children: ReactNode }) {
  const { state } = useStore();
  const accessToken = state.accessToken;
  const [data, setData] = useState<GachaMyCosmetics | null>(null);
  const requestId = useRef(0);

  const refresh = useCallback(async () => {
    const currentRequestId = ++requestId.current;
    if (!accessToken) {
      setData(null);
      return;
    }

    const next = await getMyGachaCosmetics(accessToken);
    if (currentRequestId === requestId.current) {
      setData(next);
    }
  }, [accessToken]);

  useEffect(() => {
    void refresh().catch(() => undefined);
    return () => {
      requestId.current += 1;
    };
  }, [refresh]);

  const equipped = useMemo(
    () => data?.cosmetics.filter((item) => item.equipped) ?? [],
    [data],
  );
  const title = equipped.find((item) => item.type === "TITLE") ?? null;
  const border = equipped.find((item) => item.type === "BORDER") ?? null;
  const value = useMemo(
    () => ({ data, setData, title, border, refresh }),
    [border, data, refresh, title],
  );

  return (
    <GachaCosmeticsContext.Provider value={value}>
      {children}
    </GachaCosmeticsContext.Provider>
  );
}

export function useGachaCosmetics() {
  const context = useContext(GachaCosmeticsContext);
  if (!context) {
    throw new Error(
      "useGachaCosmetics must be used within GachaCosmeticsProvider",
    );
  }
  return context;
}

export function equippedCosmetic(
  cosmetics: GachaCosmetic[],
  type: GachaCosmetic["type"],
) {
  return cosmetics.find((item) => item.type === type && item.equipped) ?? null;
}
