'use client';

import { useEffect, useRef, useState } from 'react';
import Skeleton from '@/components/Skeleton';
import { ApiError } from '@/lib/api';
import { getPlantCareGuide, PlantCareGuideData } from '@/lib/care-guide-api';

// 내용 검수를 하지 않기로 한 제품 결정(docs/ai_Feat_이슈목록.md 이슈 2)의 유일한 안전장치라,
// 가이드가 보이는 곳에는 항상 함께 노출한다. 선택 사항이 아니다.
const DISCLAIMER = 'AI가 생성한 참고 정보이며 정확성을 보장하지 않습니다.';

const DIFFICULTY_BADGE: Record<string, { bg: string; color: string }> = {
  초급: { bg: '#EEF3E4', color: '#4b7a1e' },
  중급: { bg: '#FFF6D6', color: '#8a6d00' },
  고급: { bg: '#fdf1ec', color: '#c0563a' },
};
const FALLBACK_BADGE = { bg: '#EEF3E4', color: '#4b7a1e' };

const STAGE_EMOJI: Record<string, string> = { 파종: '🌰', 새싹: '🌱', 성장: '🌿', 수확: '🧺' };

const ENVIRONMENT_ROWS: { key: keyof PlantCareGuideData['environment']; label: string; icon: string }[] = [
  { key: 'sunlight', label: '햇빛', icon: 'sunny' },
  { key: 'watering', label: '물주기', icon: 'water_drop' },
  { key: 'temperature', label: '온도', icon: 'device_thermostat' },
];

// 서버 메시지는 이미 사용자용 한국어라 그대로 쓰고(lib/api.ts 주석 참고), 여기서는 "그래서 어떻게
// 하면 되는지"만 덧붙인다. 429·409는 재시도로 풀리는 상황이라 안내가 특히 중요하다.
// 서버 메시지에 이미 있는 말("잠시 후 다시 시도해 주세요")은 반복하지 않는다 — 두 줄이 나란히
// 뜨는 자리라 같은 말을 두 번 하면 힌트가 정보가 아니라 잡음이 된다.
const ERROR_HINTS: Record<string, string> = {
  COMMON_DATA_CONFLICT: '먼저 시작된 생성이 끝나면 저장된 가이드가 바로 나와요.',
  COMMON_RATE_LIMITED: 'AI 호출 횟수 제한에 걸렸어요.',
  AI_REQUEST_TIMEOUT: '생성이 예상보다 오래 걸리고 있어요.',
  AI_PROVIDER_UNAVAILABLE: 'AI 서비스가 일시적으로 불안정해요. 잠시 뒤에 다시 시도해 주세요.',
  AI_CONFIGURATION_INVALID: '지금은 가이드를 제공할 수 없어요. 문제가 계속되면 문의해 주세요.',
  AI_RESPONSE_INVALID: '가이드를 만드는 데 실패했어요. 다시 시도해 주세요.',
};

interface GuideError {
  message: string;
  hint: string;
}

function toGuideError(requestError: unknown): GuideError {
  if (requestError instanceof ApiError) {
    return { message: requestError.message, hint: ERROR_HINTS[requestError.code] || '' };
  }
  return { message: '재배 가이드를 불러오지 못했어요.', hint: '네트워크 상태를 확인하고 다시 시도해 주세요.' };
}

interface PlantCareGuidePanelProps {
  speciesId: number;
  speciesName: string;
  accessToken: string | null;
  /** card는 페이지의 독립 섹션, inline은 모달 등 이미 카드 안에 들어가는 자리. */
  variant?: 'card' | 'inline';
}

export default function PlantCareGuidePanel({
  speciesId,
  speciesName,
  accessToken,
  variant = 'card',
}: PlantCareGuidePanelProps) {
  const [guide, setGuide] = useState<PlantCareGuideData | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<GuideError | null>(null);
  // 가이드가 길어서 특히 등록 모달에서는 아래 입력값(재배 시작일·대표 사진)이 한참 밀린다. 접어도
  // 받아둔 가이드는 그대로 들고 있어 다시 펼칠 때 AI를 부르지 않는다.
  const [expanded, setExpanded] = useState(true);
  // loading 상태만으로는 같은 tick에 두 번 눌린 클릭을 막지 못한다 — 리렌더 전이라 두 번째 핸들러가
  // 보는 클로저의 loading은 아직 false다. 실제 호출 여부는 ref로 판정해 AI를 두 번 부르지 않는다.
  const inFlightRef = useRef(false);
  const controllerRef = useRef<AbortController | null>(null);

  // 종이 바뀌면 이전 종의 결과를 지우고 진행 중이던 요청도 끊는다. 늦게 도착한 응답이 다른 종의
  // 가이드를 덮어쓰면 안 된다.
  useEffect(() => {
    setGuide(null);
    setError(null);
    setLoading(false);
    setExpanded(true);
    return () => {
      controllerRef.current?.abort();
      controllerRef.current = null;
      inFlightRef.current = false;
    };
  }, [speciesId]);

  const requestGuide = () => {
    if (!accessToken || inFlightRef.current) return;
    inFlightRef.current = true;
    const controller = new AbortController();
    controllerRef.current = controller;
    setLoading(true);
    setError(null);

    // 세 콜백 모두 signal.aborted로 먼저 걸러낸다. AbortError 여부만 보면, 응답이 도착한 뒤
    // 상태 반영 직전에 종이 바뀐 경우 이전 종의 결과가 새 화면에 실려버린다. 뒷정리는 위 cleanup이
    // 이미 끝냈으므로 여기서 상태를 되돌릴 필요도 없다.
    getPlantCareGuide(speciesId, accessToken, controller.signal)
      .then((data) => {
        if (controller.signal.aborted) return;
        setGuide(data);
      })
      .catch((requestError) => {
        if (controller.signal.aborted) return;
        setError(toGuideError(requestError));
      })
      .finally(() => {
        if (controller.signal.aborted) return;
        inFlightRef.current = false;
        controllerRef.current = null;
        setLoading(false);
      });
  };

  const outerClass =
    variant === 'card'
      ? 'mt-7 rounded-2xl bg-white p-5 shadow-card'
      : 'rounded-[14px] border-[1.5px] border-line bg-[#fbfcf8] p-4';
  const headingClass = variant === 'card' ? 'text-[19px] font-extrabold' : 'text-[15px] font-extrabold';
  // 카드는 페이지 h1 아래의 섹션이고, inline은 모달 제목(h3) 아래에 들어간다 — 붙는 자리에 맞춰
  // 제목 레벨을 낮춰야 스크린리더가 읽는 구조가 뒤집히지 않는다.
  const Heading = variant === 'card' ? 'h2' : 'h4';
  const SubHeading = variant === 'card' ? 'h3' : 'h5';
  const badge = DIFFICULTY_BADGE[guide?.difficulty ?? ''] || FALLBACK_BADGE;

  return (
    <section className={outerClass}>
      <div className="flex items-start justify-between gap-3">
        <div>
          <Heading className={headingClass}>🌿 AI 재배 가이드</Heading>
          <p className="mt-1 text-[13px] text-sub">{speciesName} 재배법을 AI가 정리해 드려요.</p>
        </div>
        {guide && (
          <button
            type="button"
            onClick={() => setExpanded((prev) => !prev)}
            aria-expanded={expanded}
            className="flex flex-none cursor-pointer items-center gap-0.5 rounded-[10px] border-[1.5px] border-line bg-white px-2.5 py-1.5 text-xs font-bold text-[#6d7a68] hover:bg-brand-soft hover:text-brand-dark"
          >
            {/* 아이콘 폰트는 리거처라 글자("expand_less")가 그대로 버튼 이름에 섞인다. 장식이므로 숨긴다. */}
            <span aria-hidden="true" className="material-symbols-outlined text-[16px]">
              {expanded ? 'expand_less' : 'expand_more'}
            </span>
            {expanded ? '접기' : '펼치기'}
          </button>
        )}
      </div>

      {!guide && (
        <>
          {loading && (
            <div className="mt-3.5" role="status">
              <div className="mb-3 text-[14.5px] font-semibold text-brand-dark">
                가이드를 만들고 있어요. 잠시만 기다려 주세요 🌱
              </div>
              <Skeleton className="h-4 w-2/3" />
              <Skeleton className="mt-2 h-4 w-full" />
              <Skeleton className="mt-2 h-4 w-5/6" />
            </div>
          )}

          {error && !loading && (
            <div role="alert" className="mt-3.5 rounded-[11px] bg-danger-soft px-[13px] py-[11px] text-[13px] text-danger">
              <div className="flex items-start gap-2 font-semibold">
                <span className="material-symbols-outlined text-[18px]">error</span>
                <span>{error.message}</span>
              </div>
              {error.hint && <div className="mt-1.5 pl-[26px] font-medium">{error.hint}</div>}
            </div>
          )}

          <div className="mt-3.5">
            <button
              type="button"
              onClick={requestGuide}
              disabled={loading || !accessToken}
              className="cursor-pointer rounded-[11px] bg-brand px-[18px] py-[11px] font-bold text-white disabled:cursor-not-allowed disabled:opacity-60"
            >
              {loading ? '가이드를 만드는 중...' : error ? '다시 시도' : '재배 가이드 보기'}
            </button>
            {!loading && !error && (
              <div className="mt-2 text-xs text-faint">처음 보는 종은 가이드를 새로 만드느라 10초 이상 걸릴 수 있어요.</div>
            )}
          </div>
        </>
      )}

      {guide && expanded && (
        <div className="mt-4 flex flex-col gap-5">
          <div>
            <span
              className="inline-block rounded-full px-3 py-[5px] text-xs font-extrabold"
              style={{ background: badge.bg, color: badge.color }}
            >
              난이도 {guide.difficulty}
            </span>
            <p className="mt-2 text-[14.5px] leading-[1.65] text-[#4a5647]">{guide.difficultyReason}</p>
          </div>

          <div>
            <SubHeading className="mb-2 text-[15px] font-extrabold">환경 조건</SubHeading>
            <div className="grid gap-2 [grid-template-columns:repeat(auto-fit,minmax(200px,1fr))]">
              {ENVIRONMENT_ROWS.map(({ key, label, icon }) => (
                <div key={key} className="rounded-xl bg-brand-soft px-3.5 py-3">
                  <div className="flex items-center gap-1.5 text-[13px] font-extrabold text-brand-text">
                    <span className="material-symbols-outlined text-[17px]">{icon}</span> {label}
                  </div>
                  <div className="mt-1 text-[14px] leading-[1.6] text-[#4a5647]">{guide.environment[key]}</div>
                </div>
              ))}
            </div>
          </div>

          <div>
            <SubHeading className="mb-2 text-[15px] font-extrabold">생육 단계</SubHeading>
            <ol className="flex flex-col gap-2">
              {guide.stages.map((stage) => (
                <li key={stage.name} className="rounded-xl border-[1.5px] border-line px-3.5 py-3">
                  <div className="text-[14px] font-extrabold">
                    {STAGE_EMOJI[stage.name] || '🌱'} {stage.name}
                  </div>
                  <div className="mt-1 text-[14px] leading-[1.6] text-[#4a5647]">{stage.guide}</div>
                </li>
              ))}
            </ol>
          </div>

          <div>
            <SubHeading className="mb-2 text-[15px] font-extrabold">흔한 실패와 대처</SubHeading>
            <ul className="flex flex-col gap-2">
              {guide.pitfalls.map((pitfall) => (
                <li key={pitfall.problem} className="rounded-xl bg-[#fdf7ec] px-3.5 py-3">
                  <div className="text-[14px] font-extrabold text-[#8a6d00]">{pitfall.problem}</div>
                  <div className="mt-1 text-[14px] leading-[1.6] text-[#4a5647]">{pitfall.action}</div>
                </li>
              ))}
            </ul>
          </div>

          <div>
            <SubHeading className="mb-2 text-[15px] font-extrabold">수확 목표</SubHeading>
            <p className="text-[14.5px] leading-[1.65] text-[#4a5647]">{guide.harvestTarget}</p>
          </div>

          <p className="border-t border-line pt-3 text-xs leading-[1.6] text-faint">{DISCLAIMER}</p>
        </div>
      )}
    </section>
  );
}
