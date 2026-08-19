"use client";
// 최초 로그인 시 메인 화면에서 자동으로(또는 Navbar의 전구 아이콘으로 언제든) 상단 메뉴를
// 하나씩 스포트라이트로 소개하는 온보딩 투어. 강조 대상은 Navbar.tsx의 NAV 항목에 붙인
// data-tour-id 속성으로 찾는다(컴포넌트 간 결합을 props 대신 DOM 속성으로 느슨하게 유지).
import { useStore } from "@/lib/store";
import { useUI } from "@/lib/ui";
import { usePathname } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";

interface TourStep {
  // null이면 특정 메뉴를 가리키지 않는 중앙 환영 카드로 렌더링한다.
  targetId: string | null;
  title: string;
  description: string;
}

// 데스크톱 상단 NAV(Navbar.tsx의 NAV, 8개) 기준 — 각 항목에 data-tour-id={key}가 붙어있다.
const TOUR_STEPS_DESKTOP: TourStep[] = [
  {
    targetId: null,
    title: "환영해요! 🌱",
    description: "키워볼래가 처음이시죠? 위쪽 메뉴를 하나씩 소개해드릴게요.",
  },
  {
    targetId: "home",
    title: "홈",
    description: "오늘의 포인트, 일지, 식물 현황을 한눈에 볼 수 있어요.",
  },
  {
    targetId: "plants",
    title: "내 식물",
    description: "반려 식물을 등록하고 성장 상태를 관리해요.",
  },
  {
    targetId: "journal",
    title: "일지",
    description: "매일 식물의 모습을 기록하면 포인트를 받아요.",
  },
  {
    targetId: "shop",
    title: "상점",
    description: "포인트로 다양한 상품과 가챠 카드팩을 구매해요.",
  },
  {
    targetId: "cards",
    title: "쿠폰",
    description: "포인트로 쿠폰을 구매하고 실제 농작물로 교환해요.",
  },
  {
    targetId: "gacha",
    title: "가챠",
    description: "카드팩을 열어 카드를 모으고 도감을 완성해보세요.",
  },
  {
    targetId: "board",
    title: "커뮤니티",
    description: "다른 사용자들과 식물 이야기를 나눠보세요.",
  },
  {
    targetId: "market",
    title: "거래소",
    description: "보유한 카드를 다른 사용자와 거래할 수 있어요.",
  },
];

// 모바일 하단 탭(Navbar.tsx의 BOTTOM, 5개 + 더보기 시트)은 데스크톱 NAV와 구조가 달라
// 별도 스텝으로 관리한다. 일지는 하단 탭에 자체 항목이 없어(activeKey가 journal일 때도
// "식물" 탭이 활성 표시됨) plants 탭을 같이 가리키며 설명을 합친다. 쿠폰/거래소/마이페이지는
// "더보기" 시트 안에 있어 각각 스포트라이트하는 대신 "더보기" 버튼 하나로 안내한다.
// 실제 하단 탭 배치 순서(Navbar.tsx의 BOTTOM: 홈→식물→가챠→커뮤니티→상점)와 동일한 순서로
// 진행해야 사용자가 스텝을 따라가면서 자연스럽게 왼쪽에서 오른쪽으로 훑게 된다.
const TOUR_STEPS_MOBILE: TourStep[] = [
  {
    targetId: null,
    title: "환영해요! 🌱",
    description: "키워볼래가 처음이시죠? 아래 메뉴를 하나씩 소개해드릴게요.",
  },
  {
    targetId: "home",
    title: "홈",
    description: "오늘의 포인트, 일지, 식물 현황을 한눈에 볼 수 있어요.",
  },
  {
    targetId: "plants",
    title: "내 식물 · 일지",
    description: "반려 식물을 등록하고, 매일의 모습을 일지로 기록해요.",
  },
  {
    targetId: "gacha",
    title: "가챠",
    description: "카드팩을 열어 카드를 모으고 도감을 완성해보세요.",
  },
  {
    targetId: "board",
    title: "커뮤니티",
    description: "다른 사용자들과 식물 이야기를 나눠보세요.",
  },
  {
    targetId: "shop",
    title: "상점",
    description: "포인트로 다양한 상품과 가챠 카드팩을 구매해요.",
  },
  {
    targetId: "more",
    title: "더보기",
    description: "쿠폰·거래소·마이페이지는 여기서 찾을 수 있어요.",
  },
];

// Navbar.tsx가 데스크톱 NAV/모바일 BOTTOM을 나누는 기준(Tailwind md breakpoint)과 맞춘다.
const MOBILE_BREAKPOINT_PX = 768;

function seenKey(userId: number): string {
  return `kwb_onboarding_tour_seen_${userId}`;
}

export default function OnboardingTour() {
  const pathname = usePathname();
  const { state, hydrated } = useStore();
  const { onboardingTourOpen, openOnboardingTour, closeOnboardingTour } =
    useUI();
  const [stepIndex, setStepIndex] = useState(0);
  const [rect, setRect] = useState<DOMRect | null>(null);
  const [isMobile, setIsMobile] = useState(false);
  // 마지막으로 자동 노출 여부를 판단한 userId — boolean 하나로 막으면 같은 탭에서
  // A로 로그인해 판단이 끝난 뒤 B로 로그인해도 재평가가 안 되는 문제가 있어, 유저별로 추적한다.
  const autoCheckedUserIdRef = useRef<number | null>(null);
  const steps = isMobile ? TOUR_STEPS_MOBILE : TOUR_STEPS_DESKTOP;

  useEffect(() => {
    const check = () => setIsMobile(window.innerWidth < MOBILE_BREAKPOINT_PX);
    check();
    window.addEventListener("resize", check);
    return () => window.removeEventListener("resize", check);
  }, []);

  // 로그인 상태로 메인 화면에 처음 들어왔고, 이 유저가 아직 투어를 본 적 없으면 자동으로 연다.
  // 같은 유저에 대해서는 한 번만 판단하도록 ref로 막아, 홈을 여러 번 오갈 때마다 재평가하지
  // 않는다 — 단, 같은 탭에서 다른 계정으로 로그인하면(state.user.id가 바뀌면) 그 계정 기준으로
  // 다시 판단해야 하므로, "이미 확인한 userId"를 기억하는 방식으로 막는다.
  useEffect(() => {
    if (!hydrated) return;
    if (pathname !== "/" || !state.authed || !state.user) return;
    if (autoCheckedUserIdRef.current === state.user.id) return;
    autoCheckedUserIdRef.current = state.user.id;
    if (!localStorage.getItem(seenKey(state.user.id))) {
      openOnboardingTour();
    }
  }, [hydrated, pathname, state.authed, state.user, openOnboardingTour]);

  // 뷰포트가 바뀌면(리사이즈로 데스크톱↔모바일 스텝 배열이 바뀌면) 스텝 길이가 달라질 수
  // 있으므로 처음부터 다시 보여준다 — 인덱스를 어설프게 클램프하면 순서가 어긋난다.
  useEffect(() => {
    if (onboardingTourOpen) setStepIndex(0);
  }, [onboardingTourOpen, isMobile]);

  const updateRect = useCallback(() => {
    const step = steps[stepIndex];
    if (!step || !step.targetId) {
      setRect(null);
      return;
    }
    // 데스크톱 NAV와 모바일 하단 탭이 같은 data-tour-id를 공유한다(둘 다 렌더링은 되고
    // Tailwind hidden/md:hidden으로 화면에서만 안 보이게 함) — querySelector는 DOM에 먼저
    // 나오는(항상 데스크톱 NAV) 쪽을 집어서, 모바일에서는 숨겨진 요소의 0 근처 좌표를 잘못
    // 가리키는 버그가 있었다. 실제로 화면에 보이는(너비/높이가 있는) 요소를 찾아야 한다.
    const candidates = document.querySelectorAll(
      `[data-tour-id="${step.targetId}"]`,
    );
    let visibleRect: DOMRect | null = null;
    candidates.forEach((el) => {
      const r = el.getBoundingClientRect();
      if (r.width > 0 && r.height > 0) visibleRect = r;
    });
    setRect(visibleRect);
  }, [steps, stepIndex]);

  useEffect(() => {
    if (!onboardingTourOpen) return;
    updateRect();
    window.addEventListener("resize", updateRect);
    return () => window.removeEventListener("resize", updateRect);
  }, [onboardingTourOpen, updateRect]);

  const finish = useCallback(() => {
    if (state.user) localStorage.setItem(seenKey(state.user.id), "1");
    closeOnboardingTour();
  }, [state.user, closeOnboardingTour]);

  if (!onboardingTourOpen) {
    // 이 온보딩은 메인 화면 전용이라, 재실행 버튼도 메인 화면에서만 보여준다 — 다른 화면(예:
    // 일지의 AI 도우미 런처)에 있는 고정 위치 버튼과 겹칠 일이 아예 없어진다.
    if (pathname !== "/" || !state.authed) return null;
    return (
      <button
        type="button"
        title="온보딩 투어 다시 보기"
        aria-label="온보딩 투어 다시 보기"
        onClick={openOnboardingTour}
        className="fixed bottom-[82px] right-4 z-[44] flex h-12 w-12 cursor-pointer items-center justify-center rounded-full bg-brand text-white shadow-[0_8px_20px_rgba(85,139,47,.35)] transition-colors duration-150 hover:bg-brand-dark md:bottom-6 md:right-6"
      >
        <span className="material-symbols-outlined text-2xl">lightbulb</span>
      </button>
    );
  }

  const step = steps[Math.min(stepIndex, steps.length - 1)];
  const isLast = stepIndex >= steps.length - 1;

  const handleNext = () => {
    if (isLast) {
      finish();
      return;
    }
    setStepIndex((i) => i + 1);
  };

  // 좁은 화면에서는 카드 폭을 화면에 맞게 줄인다(모바일 하단 탭은 화면 폭이 320px보다 좁을 수 있음).
  const cardWidth = Math.min(320, window.innerWidth - 32);
  // 강조 영역 바로 아래에 카드를 두되, 화면 아래로 넘치면(모바일 하단 탭이 대표적) 위쪽에 띄운다.
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
            onClick={handleNext}
            className="flex-1 cursor-pointer rounded-xl bg-brand p-[11px] font-extrabold text-white hover:bg-brand-dark hover:text-white"
          >
            {isLast ? "시작하기" : "다음"}
          </button>
          <button
            type="button"
            onClick={finish}
            className="cursor-pointer rounded-xl border-[1.5px] border-line bg-white px-4 py-[11px] font-bold text-sub"
          >
            건너뛰기
          </button>
        </div>
      </div>
    </div>
  );
}
