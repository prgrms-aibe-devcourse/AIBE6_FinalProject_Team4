"use client";

import { useRouter } from "next/navigation";
import { useStore } from "@/lib/store";

const GACHA_FALLBACK_PATH = "/gacha";

function hasSameOriginHistory() {
  if (
    typeof window === "undefined" ||
    typeof document === "undefined" ||
    window.history.length <= 1 ||
    !document.referrer
  ) {
    return false;
  }

  try {
    return new URL(document.referrer).origin === window.location.origin;
  } catch {
    return false;
  }
}

export function useGachaOpenNavigation(returnTo?: "journals") {
  const router = useRouter();
  const { refreshNotifications } = useStore();

  const moveBack = async () => {
    await refreshNotifications();
    if (returnTo === "journals") {
      router.replace("/journals");
      return;
    }
    if (hasSameOriginHistory()) {
      router.back();
      return;
    }
    router.replace(GACHA_FALLBACK_PATH);
  };

  const moveToCollection = () => {
    router.replace("/gacha?tab=mine");
  };

  return { moveBack, moveToCollection };
}
