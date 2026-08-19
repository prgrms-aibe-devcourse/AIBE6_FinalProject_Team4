"use client";

import { useRouter } from "next/navigation";
import { useStore } from "@/lib/store";

export function useGachaOpenNavigation(returnTo?: "journals") {
  const router = useRouter();
  const { refreshNotifications } = useStore();

  const moveBack = async () => {
    await refreshNotifications();
    if (returnTo === "journals") {
      router.replace("/journals");
      return;
    }
    router.back();
  };

  const moveToCollection = () => {
    router.replace("/gacha?tab=mine");
  };

  return { moveBack, moveToCollection };
}
