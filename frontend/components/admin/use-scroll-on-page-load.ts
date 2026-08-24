"use client";

import { RefObject, useEffect, useRef } from "react";

// 페이지 번호가 바뀐 뒤 그 페이지의 데이터가 실제로 도착(loading이 false로 바뀜)했을 때만
// 스크롤한다 — 클릭 즉시 스크롤하면 아직 이전 페이지 내용이 보이는 채로 화면만 움직여서
// 부자연스럽다. enabled=false면(예: 다른 탭이 활성일 때) 아무 것도 하지 않는다.
export function useScrollOnPageLoad(
  page: number,
  loading: boolean,
  targetRef: RefObject<HTMLElement | null>,
  enabled = true,
) {
  const prevPageRef = useRef(page);
  const pendingRef = useRef(false);

  useEffect(() => {
    if (!enabled) return;
    if (page !== prevPageRef.current) {
      pendingRef.current = true;
    }
    prevPageRef.current = page;
  }, [page, enabled]);

  useEffect(() => {
    if (!enabled || loading || !pendingRef.current) return;
    pendingRef.current = false;
    const el = targetRef.current;
    if (el && typeof el.scrollIntoView === "function") {
      el.scrollIntoView({ behavior: "smooth", block: "start" });
    } else if (typeof window !== "undefined" && typeof window.scrollTo === "function") {
      window.scrollTo({ top: 0, behavior: "smooth" });
    }
  }, [loading, enabled, targetRef]);
}
