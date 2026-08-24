"use client";
// 온보딩 스포트라이트 투어의 순수 렌더링 컴포넌트 — 상태는 useSpotlightTour 훅이 들고 있고,
// 이 컴포넌트는 현재 스텝을 받아 오버레이/스포트라이트/설명 카드만 그린다. 홈 화면 온보딩과
// 도메인별 온보딩(이슈 2)이 이 컴포넌트를 공유한다.
import { useCallback, useEffect, useState } from "react";

export interface TourStep {
  // null이면 특정 요소를 가리키지 않는 중앙 카드로 렌더링한다(예: 환영 인사).
  targetId: string | null;
  title: string;
  description: string;
}

interface SpotlightTourProps {
  steps: TourStep[];
  stepIndex: number;
  onNext: () => void;
  onSkip: () => void;
}

export default function SpotlightTour({
  steps,
  stepIndex,
  onNext,
  onSkip,
}: SpotlightTourProps) {
  const [rect, setRect] = useState<DOMRect | null>(null);

  const findVisibleRect = useCallback((targetId: string): DOMRect | null => {
    // 같은 data-tour-id를 가진 요소가 여러 개 동시에 화면에 있을 수 있다 — 데스크톱/모바일
    // 버전이 Tailwind hidden/md:hidden으로 한쪽만 보이는 경우(이때는 보이는 것 하나만 잡힘),
    // 또는 드롭다운 그룹의 트리거 링크와 펼쳐진 패널처럼 같은 targetId를 공유하며 둘 다 실제로
    // 보이는 경우(이때는 두 요소를 감싸는 사각형이 되어야 스포트라이트가 드롭다운 전체를
    // 덮는다). 그래서 너비/높이가 있는(실제로 보이는) 요소를 전부 모아 하나로 합친다.
    const candidates = document.querySelectorAll(`[data-tour-id="${targetId}"]`);
    let visibleRect: DOMRect | null = null;
    candidates.forEach((el) => {
      const r = el.getBoundingClientRect();
      if (r.width === 0 || r.height === 0) return;
      if (!visibleRect) {
        visibleRect = r;
        return;
      }
      const left = Math.min(visibleRect.left, r.left);
      const top = Math.min(visibleRect.top, r.top);
      const right = Math.max(visibleRect.right, r.right);
      const bottom = Math.max(visibleRect.bottom, r.bottom);
      visibleRect = new DOMRect(left, top, right - left, bottom - top);
    });
    return visibleRect;
  }, []);

  const updateRect = useCallback(() => {
    const step = steps[stepIndex];
    if (!step || !step.targetId) {
      setRect(null);
      return;
    }
    setRect(findVisibleRect(step.targetId));
  }, [steps, stepIndex, findVisibleRect]);

  useEffect(() => {
    updateRect();
    window.addEventListener("resize", updateRect);
    return () => window.removeEventListener("resize", updateRect);
  }, [updateRect]);

  // 드롭다운 그룹(예: 내 식물/가챠)을 가리키는 스텝은 트리거 링크가 항상 먼저 보이고,
  // Navbar가 온보딩 이벤트를 받아 펼친 패널은 한 박자 늦게 DOM에 나타난다. 그래서 "못 찾았을
  // 때만 재시도"로는 안 되고(트리거만으로도 이미 "찾음" 처리돼버림), 스텝이 바뀔 때마다 짧게
  // (최대 10프레임) 계속 다시 측정해 패널이 늦게 나타나도 합쳐진 사각형으로 갱신되게 한다.
  useEffect(() => {
    const step = steps[stepIndex];
    if (!step?.targetId) return;
    const targetId = step.targetId;
    let frame = 0;
    let rafId: number;
    const tick = () => {
      setRect(findVisibleRect(targetId));
      frame += 1;
      if (frame < 10) rafId = requestAnimationFrame(tick);
    };
    rafId = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(rafId);
  }, [steps, stepIndex, findVisibleRect]);

  const step = steps[Math.min(stepIndex, steps.length - 1)];
  if (!step) return null;
  const isLast = stepIndex >= steps.length - 1;

  // 좁은 화면에서는 카드 폭을 화면에 맞게 줄인다.
  const cardWidth = Math.min(320, window.innerWidth - 32);
  // 강조 영역 바로 아래에 카드를 두되, 화면 아래로 넘치면 위쪽에 띄운다.
  const cardTop = rect
    ? rect.bottom + 220 <= window.innerHeight
      ? rect.bottom + 16
      : Math.max(16, rect.top - 196)
    : undefined;
  const cardLeft = rect
    ? Math.min(Math.max(rect.left, 16), window.innerWidth - cardWidth - 16)
    : undefined;

  return (
    <div className="fixed inset-0 z-[80]">
      {rect ? (
        <div
          className="pointer-events-none fixed rounded-2xl transition-all duration-300"
          style={{
            top: rect.top - 8,
            left: rect.left - 8,
            width: rect.width + 16,
            height: rect.height + 16,
            boxShadow: "0 0 0 9999px rgba(20,26,18,0.78)",
          }}
        />
      ) : (
        <div className="fixed inset-0 bg-[rgba(20,26,18,0.78)]" />
      )}

      <div
        className={`fixed rounded-[18px] bg-white p-5 shadow-[0_20px_50px_rgba(0,0,0,.3)] ${
          rect ? "" : "left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2"
        }`}
        style={
          rect
            ? { top: cardTop, left: cardLeft, width: cardWidth }
            : { width: cardWidth }
        }
      >
        <div className="mb-1 text-[11px] font-bold text-faint">
          {stepIndex + 1} / {steps.length}
        </div>
        <h3 className="mb-1.5 text-lg font-extrabold">{step.title}</h3>
        <p className="mb-4 text-sm leading-[1.6] text-[#6d7a68]">
          {step.description}
        </p>
        <div className="flex gap-2.5">
          <button
            type="button"
            onClick={onNext}
            className="flex-1 cursor-pointer rounded-xl bg-brand p-[11px] font-extrabold text-white hover:bg-brand-dark hover:text-white"
          >
            {isLast ? "확인" : "다음"}
          </button>
          <button
            type="button"
            onClick={onSkip}
            className="cursor-pointer rounded-xl border-[1.5px] border-line bg-white px-4 py-[11px] font-bold text-sub"
          >
            건너뛰기
          </button>
        </div>
      </div>
    </div>
  );
}
