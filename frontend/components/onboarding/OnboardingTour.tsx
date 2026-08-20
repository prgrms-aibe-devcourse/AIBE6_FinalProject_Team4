"use client";
// 최초 로그인 시 메인 화면에서 자동으로(또는 제목 옆 재실행 버튼으로 언제든) 상단 메뉴를
// 하나씩 스포트라이트로 소개하는 온보딩 투어. 강조 대상은 Navbar.tsx의 NAV 항목에 붙인
// data-tour-id 속성으로 찾는다(컴포넌트 간 결합을 props 대신 DOM 속성으로 느슨하게 유지).
// 렌더링/상태 로직은 SpotlightTour.tsx·useSpotlightTour.ts로 분리해, 도메인별 온보딩(이슈 2)도
// 같은 두 조각을 재사용한다.
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { useStore } from "@/lib/store";
import { useSpotlightTour } from "@/lib/onboarding/useSpotlightTour";
import SpotlightTour, { TourStep } from "./SpotlightTour";

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

export default function OnboardingTour() {
  const pathname = usePathname();
  const { state } = useStore();
  const [isMobile, setIsMobile] = useState(false);
  const steps = isMobile ? TOUR_STEPS_MOBILE : TOUR_STEPS_DESKTOP;
  // tourId=''는 이 훅으로 옮기기 전부터 쓰던 kwb_onboarding_tour_seen_{userId} 키를 그대로
  // 유지하기 위함이다(이미 투어를 본 유저에게 리팩터링만으로 다시 노출되는 걸 막음). 이 화면은
  // 메인('/')에서만 자동 노출되어야 하므로 autoTriggerEnabled를 그 경로일 때만 켠다.
  const tour = useSpotlightTour("", steps.length, pathname === "/");

  useEffect(() => {
    const check = () => setIsMobile(window.innerWidth < MOBILE_BREAKPOINT_PX);
    check();
    window.addEventListener("resize", check);
    return () => window.removeEventListener("resize", check);
  }, []);

  // 뷰포트가 바뀌면(리사이즈로 데스크톱↔모바일 스텝 배열이 바뀌면) 진행 중이던 스텝 길이가
  // 달라질 수 있으므로 처음부터 다시 보여준다 — 인덱스를 어설프게 클램프하면 순서가 어긋난다.
  useEffect(() => {
    if (tour.open) tour.start();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isMobile]);

  if (pathname !== "/" || !state.authed) return null;

  if (!tour.open) {
    // 이 온보딩은 메인 화면 전용이라, 재실행 버튼도 메인 화면에서만 보여준다 — 다른 화면(예:
    // 일지의 AI 도우미 런처)에 있는 고정 위치 버튼과 겹칠 일이 아예 없어진다.
    return (
      <button
        type="button"
        title="온보딩 투어 다시 보기"
        aria-label="온보딩 투어 다시 보기"
        onClick={tour.start}
        className="fixed bottom-[82px] right-4 z-[44] flex h-12 w-12 cursor-pointer items-center justify-center rounded-full bg-brand text-white shadow-[0_8px_20px_rgba(85,139,47,.35)] transition-colors duration-150 hover:bg-brand-dark md:bottom-6 md:right-6"
      >
        <span className="material-symbols-outlined text-2xl">lightbulb</span>
      </button>
    );
  }

  return (
    <SpotlightTour
      steps={steps}
      stepIndex={tour.stepIndex}
      onNext={tour.next}
      onSkip={tour.skip}
    />
  );
}
