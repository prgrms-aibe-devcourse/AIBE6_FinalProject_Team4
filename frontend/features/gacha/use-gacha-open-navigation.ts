"use client";

import { useRouter } from "next/navigation";
import type { MouseEvent } from "react";
import { useStore } from "@/lib/store";

export function useGachaOpenNavigation() {
  const router = useRouter();
  const { refreshNotifications } = useStore();

  const moveToJournals = async (event: MouseEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    await refreshNotifications();
    router.push("/journals");
  };

  const moveToCollection = () => {
    router.replace("/gacha?tab=mine");
  };

  return { moveToJournals, moveToCollection };
}
