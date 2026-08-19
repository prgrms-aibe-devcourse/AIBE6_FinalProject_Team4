'use client';
// 최초 로그인 시 메인 화면에서 자동으로(또는 Navbar의 전구 아이콘으로 언제든) 상단 메뉴를
// 하나씩 스포트라이트로 소개하는 온보딩 투어. 강조 대상은 Navbar.tsx의 NAV 항목에 붙인
// data-tour-id 속성으로 찾는다(컴포넌트 간 결합을 props 대신 DOM 속성으로 느슨하게 유지).
import { useCallback, useEffect, useRef, useState } from 'react';
import { usePathname } from 'next/navigation';
import { useStore } from '@/lib/store';
import { useUI } from '@/lib/ui';

interface TourStep {
  // null이면 특정 메뉴를 가리키지 않는 중앙 환영 카드로 렌더링한다.
  targetId: string | null;
  title: string;
  description: string;
}

const TOUR_STEPS: TourStep[] = [
  { targetId: null, title: '환영해요! 🌱', description: '키워볼래가 처음이시죠? 위쪽 메뉴를 하나씩 소개해드릴게요.' },
  { targetId: 'home', title: '홈', description: '오늘의 포인트, 일지, 식물 현황을 한눈에 볼 수 있어요.' },
  { targetId: 'plants', title: '내 식물', description: '반려 식물을 등록하고 성장 상태를 관리해요.' },
  { targetId: 'journal', title: '일지', description: '매일 식물의 모습을 기록하면 포인트를 받아요.' },
  { targetId: 'shop', title: '상점', description: '포인트로 다양한 상품과 가챠 카드팩을 구매해요.' },
  { targetId: 'cards', title: '쿠폰', description: '포인트로 얻은 카드를 모아 실제 농작물 쿠폰으로 교환해요.' },
  { targetId: 'gacha', title: '가챠', description: '카드팩을 열어 카드를 모으고 도감을 완성해보세요.' },
  { targetId: 'board', title: '커뮤니티', description: '다른 사용자들과 식물 이야기를 나눠보세요.' },
  { targetId: 'market', title: '거래소', description: '보유한 카드를 다른 사용자와 거래할 수 있어요.' },
];

function seenKey(userId: number): string {
  return `kwb_onboarding_tour_seen_${userId}`;
}

export default function OnboardingTour() {
  const pathname = usePathname();
  const { state, hydrated } = useStore();
  const { onboardingTourOpen, openOnboardingTour, closeOnboardingTour } = useUI();
  const [stepIndex, setStepIndex] = useState(0);
  const [rect, setRect] = useState<DOMRect | null>(null);
  const autoCheckedRef = useRef(false);

  // 로그인 상태로 메인 화면에 처음 들어왔고, 이 유저가 아직 투어를 본 적 없으면 자동으로 연다.
  // 세션당 한 번만 판단하도록 ref로 막아, 홈을 여러 번 오갈 때마다 재평가하지 않는다.
  useEffect(() => {
    if (!hydrated || autoCheckedRef.current) return;
    if (pathname !== '/' || !state.authed || !state.user) return;
    autoCheckedRef.current = true;
    if (!localStorage.getItem(seenKey(state.user.id))) {
      openOnboardingTour();
    }
  }, [hydrated, pathname, state.authed, state.user, openOnboardingTour]);

  useEffect(() => {
    if (onboardingTourOpen) setStepIndex(0);
  }, [onboardingTourOpen]);

  const updateRect = useCallback(() => {
    const step = TOUR_STEPS[stepIndex];
    if (!step.targetId) {
      setRect(null);
      return;
    }
    const el = document.querySelector(`[data-tour-id="${step.targetId}"]`);
    setRect(el ? el.getBoundingClientRect() : null);
  }, [stepIndex]);

  useEffect(() => {
    if (!onboardingTourOpen) return;
    updateRect();
    window.addEventListener('resize', updateRect);
    return () => window.removeEventListener('resize', updateRect);
  }, [onboardingTourOpen, updateRect]);

  const finish = useCallback(() => {
    if (state.user) localStorage.setItem(seenKey(state.user.id), '1');
    closeOnboardingTour();
  }, [state.user, closeOnboardingTour]);

  if (!onboardingTourOpen) return null;

  const step = TOUR_STEPS[stepIndex];
  const isLast = stepIndex === TOUR_STEPS.length - 1;

  const handleNext = () => {
    if (isLast) {
      finish();
      return;
    }
    setStepIndex((i) => i + 1);
  };

  // 강조 영역 바로 아래에 카드를 두되, 화면 아래로 넘치면 위쪽에 띄운다.
  const cardTop = rect
    ? rect.bottom + 300 <= window.innerHeight
      ? rect.bottom + 16
      : Math.max(16, rect.top - 176)
    : undefined;
  const cardLeft = rect ? Math.min(Math.max(rect.left, 16), window.innerWidth - 320 - 16) : undefined;

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
            boxShadow: '0 0 0 9999px rgba(20,26,18,0.78)',
          }}
        />
      ) : (
        <div className="fixed inset-0 bg-[rgba(20,26,18,0.78)]" />
      )}

      <div
        className={`fixed w-[320px] rounded-[18px] bg-white p-5 shadow-[0_20px_50px_rgba(0,0,0,.3)] ${
          rect ? '' : 'left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2'
        }`}
        style={rect ? { top: cardTop, left: cardLeft } : undefined}
      >
        <div className="mb-1 text-[11px] font-bold text-faint">{stepIndex + 1} / {TOUR_STEPS.length}</div>
        <h3 className="mb-1.5 text-lg font-extrabold">{step.title}</h3>
        <p className="mb-4 text-sm leading-[1.6] text-[#6d7a68]">{step.description}</p>
        <div className="flex gap-2.5">
          <button
            type="button"
            onClick={handleNext}
            className="flex-1 cursor-pointer rounded-xl bg-brand p-[11px] font-extrabold text-white hover:bg-brand-dark hover:text-white"
          >
            {isLast ? '시작하기' : '다음'}
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
