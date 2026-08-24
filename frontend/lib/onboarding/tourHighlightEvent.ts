"use client";
// 온보딩 투어(OnboardingTour.tsx)와 상단 내비게이션(Navbar.tsx)은 서로 다른 컴포넌트 트리라
// props로 상태를 공유하지 않는다. gacha-api.ts의 GACHA_COLLECTION_CHANGED_EVENT와 같은 방식으로,
// 커스텀 window 이벤트를 통해 "지금 강조 중인 data-tour-id"만 느슨하게 전달한다.
export const TOUR_HIGHLIGHT_EVENT = "kwb:onboarding-tour-highlight";

export type TourHighlightDetail = string | null;

export function dispatchTourHighlight(targetId: TourHighlightDetail) {
  window.dispatchEvent(
    new CustomEvent<TourHighlightDetail>(TOUR_HIGHLIGHT_EVENT, {
      detail: targetId,
    }),
  );
}
