"use client";

import { useEffect } from "react";

export function usePreventBackNavigation(enabled: boolean) {
  useEffect(() => {
    if (!enabled) return;

    const restoreOpenPage = () => {
      window.history.forward();
    };

    window.addEventListener("popstate", restoreOpenPage);
    return () => window.removeEventListener("popstate", restoreOpenPage);
  }, [enabled]);
}
