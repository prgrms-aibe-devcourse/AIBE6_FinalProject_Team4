"use client";
// 온보딩 스포트라이트 투어의 상태(열림 여부/스텝/자동 노출 판정)를 관리하는 훅. 홈 화면과
// 도메인별 페이지(이슈 2)가 이 훅을 각자 다른 storageKey로 호출해 독립적으로 사용한다.
import { useCallback, useEffect, useRef, useState } from "react";
import { useStore } from "@/lib/store";

// tourId가 빈 문자열이면 홈 화면의 기존 키(kwb_onboarding_tour_seen_{userId})와 동일하게
// 유지한다 — 이미 투어를 본 유저에게 이 리팩터링 때문에 다시 노출되는 걸 막기 위함이다.
function seenKey(userId: number, tourId: string): string {
  return tourId
    ? `kwb_onboarding_tour_seen_${userId}_${tourId}`
    : `kwb_onboarding_tour_seen_${userId}`;
}

export interface UseSpotlightTourResult {
  open: boolean;
  stepIndex: number;
  /** 버튼 등으로 언제든 처음부터 다시 시작할 때 호출 */
  start: () => void;
  /** 다음 스텝으로 — 마지막 스텝이면 완료 처리(스킵과 동일하게 플래그 저장 후 닫힘) */
  next: () => void;
  /** 즉시 완료 처리하고 닫음(건너뛰기) */
  skip: () => void;
}

export function useSpotlightTour(
  tourId: string,
  totalSteps: number,
  autoTriggerEnabled: boolean = true,
): UseSpotlightTourResult {
  const { state, hydrated } = useStore();
  const [open, setOpen] = useState(false);
  const [stepIndex, setStepIndex] = useState(0);
  // 마지막으로 자동 노출 여부를 판단한 userId — boolean 하나로 막으면 같은 탭에서 A로
  // 로그인해 판단이 끝난 뒤 B로 로그인해도 재평가가 안 되는 문제가 있어, 유저별로 추적한다.
  const autoCheckedUserIdRef = useRef<number | null>(null);

  useEffect(() => {
    if (!autoTriggerEnabled || !hydrated || !state.authed || !state.user) return;
    if (autoCheckedUserIdRef.current === state.user.id) return;
    autoCheckedUserIdRef.current = state.user.id;
    if (!localStorage.getItem(seenKey(state.user.id, tourId))) {
      setStepIndex(0);
      setOpen(true);
    }
  }, [autoTriggerEnabled, hydrated, state.authed, state.user, tourId]);

  const skip = useCallback(() => {
    if (state.user) localStorage.setItem(seenKey(state.user.id, tourId), "1");
    setOpen(false);
  }, [state.user, tourId]);

  const start = useCallback(() => {
    setStepIndex(0);
    setOpen(true);
  }, []);

  const next = useCallback(() => {
    if (stepIndex >= totalSteps - 1) {
      skip();
      return;
    }
    setStepIndex((i) => i + 1);
  }, [stepIndex, totalSteps, skip]);

  return { open, stepIndex, start, next, skip };
}
