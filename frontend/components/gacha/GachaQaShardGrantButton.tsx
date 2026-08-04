"use client";

import { useState } from "react";
import { notifyGachaCosmeticsChanged } from "@/features/gacha/use-gacha-cosmetics";
import { grantGachaQaShards } from "@/lib/gacha-api";
import { ApiError } from "@/lib/api";
import { useUI } from "@/lib/ui";

export default function GachaQaShardGrantButton({
  accessToken,
}: {
  accessToken: string;
}) {
  const { showToast } = useUI();
  const [busy, setBusy] = useState(false);

  if (process.env.NODE_ENV === "production") return null;

  const grant = async () => {
    if (busy) return;
    setBusy(true);
    try {
      const wallet = await grantGachaQaShards(accessToken);
      notifyGachaCosmeticsChanged();
      showToast(`조각 100개 지급 완료 · 현재 ${wallet.balance}개`);
    } catch (error) {
      showToast(
        error instanceof ApiError ? error.message : "조각을 지급하지 못했어요.",
        "err",
      );
    } finally {
      setBusy(false);
    }
  };

  return (
    <button
      type="button"
      disabled={busy}
      onClick={() => void grant()}
      className="block w-full cursor-pointer px-4 py-2.5 text-left text-[14px] font-semibold text-[#8a6500] transition-colors duration-150 hover:bg-[#fff8dc] disabled:cursor-wait disabled:opacity-50"
    >
      {busy ? "조각 지급 중…" : "조각 100개 지급 (QA)"}
    </button>
  );
}
