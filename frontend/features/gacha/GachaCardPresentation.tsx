"use client";

import Image from "next/image";
import { GachaCard, GachaRarity } from "@/lib/gacha-api";

export const RARITY_LABEL: Record<GachaRarity, string> = {
  COMMON: "커먼",
  RARE: "레어",
  SUPER_RARE: "슈퍼 레어",
  HYPER_RARE: "하이퍼 레어",
  GOLDEN_RARE: "골든 레어",
};

export const RARITY_STYLE: Record<GachaRarity, string> = {
  COMMON: "bg-[#eef1e9] text-[#65705f]",
  RARE: "bg-[#e9f4ea] text-[#3c7a43]",
  SUPER_RARE: "bg-[#e9effc] text-[#3d5f9b]",
  HYPER_RARE: "bg-[#f0eafd] text-[#6945a6]",
  GOLDEN_RARE: "bg-[#fff3c9] text-[#8b6800]",
};

export const RARITY_PANEL: Record<GachaRarity, string> = {
  COMMON: "border-[#dce2d6] bg-[#f5f7f2]",
  RARE: "border-[#cde3cf] bg-[#f1f8f1]",
  SUPER_RARE: "border-[#cbd7ef] bg-[#f2f5fc]",
  HYPER_RARE: "border-[#d8caee] bg-[#f7f3fd]",
  GOLDEN_RARE: "border-[#e7cc69] bg-gradient-to-br from-[#fff9df] to-[#f8e7a5]",
};

export const RARITY_ORDER: GachaRarity[] = [
  "COMMON",
  "RARE",
  "SUPER_RARE",
  "HYPER_RARE",
  "GOLDEN_RARE",
];

export type DisplayCard = GachaCard & {
  ownedCount: number;
  dismantleableCount: number;
  shardPerCard: number;
  owned: boolean;
  unlocked: boolean;
  goldenGachaAcquired: boolean;
};

export function lockedCard(card: GachaCard): DisplayCard {
  return {
    ...card,
    imageUrl: null,
    ownedCount: 0,
    dismantleableCount: 0,
    shardPerCard: 0,
    owned: false,
    unlocked: false,
    goldenGachaAcquired: false,
  };
}

export function GachaCardModal({
  card,
  onClose,
}: {
  card: DisplayCard | null;
  onClose: () => void;
}) {
  if (!card?.imageUrl) return null;

  return (
    <div
      role="presentation"
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
      className="fixed inset-0 z-[100] flex items-center justify-center bg-[#101a12]/85 p-4 backdrop-blur-md"
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label={`${card.name} 원본 일러스트`}
        className="relative w-[min(92vw,58vh,560px)]"
      >
        <button
          type="button"
          onClick={onClose}
          aria-label="원본 일러스트 닫기"
          className="absolute -top-12 right-0 flex h-10 w-10 items-center justify-center rounded-full bg-white/15 text-white backdrop-blur"
        >
          <span className="material-symbols-outlined">close</span>
        </button>
        <div className="relative aspect-[1122/1402] overflow-hidden rounded-[24px] bg-white/[.04] shadow-[0_28px_80px_rgba(0,0,0,.38)]">
          <Image
            src={card.imageUrl}
            alt={`${card.name} 원본 카드 일러스트`}
            fill
            priority
            sizes="(max-width: 640px) 92vw, 560px"
            className="object-contain"
          />
        </div>
        <div className="mt-4 text-center text-white">
          <p className="text-xl font-black">{card.name}</p>
          <p className="mt-1 text-sm text-white/60">
            {RARITY_LABEL[card.rarity]}
            {card.ownedCount > 0
              ? ` · ${card.ownedCount}장 보유`
              : " · 도감 해금"}
          </p>
        </div>
      </div>
    </div>
  );
}
