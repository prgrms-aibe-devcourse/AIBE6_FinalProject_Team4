"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import type { KeyboardEvent as ReactKeyboardEvent } from "react";
import PlantCareGroundingNotice from "@/components/PlantCareGroundingNotice";
import {
  getPlantCareFaqs,
  PlantCareFaq,
  PlantCareFaqCategory,
  PLANT_CARE_FAQ_CATEGORIES,
} from "@/features/journal/plant-care-faqs";
import { askPlantChat } from "@/features/journal/plant-chat-api";
import { ApiError } from "@/lib/api";
import { PlantCareGrounding } from "@/lib/plant-care-grounding";

const MAX_QUESTION_LENGTH = 2000;
const PANEL_ID = "plant-journal-assistant-panel";
const FAQ_SHEET_ID = "plant-journal-assistant-faq-sheet";
const AI_DISCLAIMER =
  "AI 답변은 식물 프로필과 최근 일지를 바탕으로 만든 참고 정보예요.";

interface PlantSummary {
  id: number;
  nickname: string;
  speciesName: string;
}

type MessageSource = "FAQ" | "AI";

interface DisplayMessage {
  id: number;
  role: "USER" | "ASSISTANT";
  content: string;
  source: MessageSource;
  recommendedActions?: readonly string[];
  additionalChecks?: readonly string[];
  grounding?: PlantCareGrounding;
}

interface PlantJournalAssistantProps {
  plant: PlantSummary | null;
  plantOptions?: readonly PlantSummary[];
  plantOptionsLoading?: boolean;
  onPlantChange?: (plantId: number) => void;
  accessToken: string | null;
}

function toErrorMessage(requestError: unknown): string {
  if (requestError instanceof ApiError) {
    if (
      requestError.code === "COMMON_RATE_LIMITED" &&
      requestError.retryAfterSeconds !== undefined
    ) {
      return `AI 사용 한도에 도달했어요. ${formatRetryAfter(requestError.retryAfterSeconds)} 후 다시 시도할 수 있어요.`;
    }
    return requestError.message;
  }
  return "AI 답변을 불러오지 못했어요. 네트워크 상태를 확인하고 다시 시도해 주세요.";
}

function formatRetryAfter(retryAfterSeconds: number): string {
  const totalMinutes = Math.floor(Math.max(1, retryAfterSeconds) / 60);
  const days = Math.floor(totalMinutes / (24 * 60));
  const hours = Math.floor((totalMinutes % (24 * 60)) / 60);
  const minutes = totalMinutes % 60;

  if (days > 0) return hours > 0 ? `${days}일 ${hours}시간` : `${days}일`;
  if (hours > 0)
    return minutes > 0 ? `${hours}시간 ${minutes}분` : `${hours}시간`;
  if (minutes > 0) return `${minutes}분`;
  return `${Math.ceil(Math.max(1, retryAfterSeconds))}초`;
}

function AssistantMark({
  compact = false,
  inverted = false,
}: {
  compact?: boolean;
  inverted?: boolean;
}) {
  return (
    <span
      aria-hidden="true"
      className={`relative flex flex-none items-center justify-center border shadow-sm ${
        compact ? "h-10 w-10 rounded-[14px]" : "h-12 w-12 rounded-[17px]"
      } ${
        inverted
          ? "border-brand-dark bg-brand-dark text-white"
          : "border-[#d5ded0] bg-white text-brand-dark"
      }`}
    >
      <span
        className={`material-symbols-outlined ${compact ? "text-[23px]" : "text-[28px]"}`}
      >
        psychiatry
      </span>
      <span
        className={`absolute -right-1 -top-1 flex items-center justify-center rounded-full border-2 border-white bg-[#e4a934] text-white shadow-sm ${
          compact ? "h-[17px] w-[17px]" : "h-5 w-5"
        }`}
      >
        <span className="material-symbols-outlined text-[11px]">
          auto_awesome
        </span>
      </span>
    </span>
  );
}

function AssistantMessage({ message }: { message: DisplayMessage }) {
  return (
    <div className="max-w-[95%] rounded-[18px] rounded-tl-[6px] border border-line bg-white px-3.5 py-3 text-[14px] leading-[1.65] text-[#465144]">
      {/* 이 배지 div의 직계 텍스트는 "준비된 답변" / "내 식물 기록 기반 AI 답변" 뿐이어야 한다.
          "· FAQ" 같은 문자열을 덧붙이면 테스트의 getByText("준비된 답변")이 깨진다. */}
      <div className="mb-1.5 flex items-center gap-1.5 text-[11px] font-extrabold text-brand-text">
        <span
          aria-hidden="true"
          className="material-symbols-outlined text-[14px]"
        >
          {message.source === "FAQ" ? "menu_book" : "auto_awesome"}
        </span>
        {message.source === "FAQ" ? "준비된 답변" : "내 식물 기록 기반 AI 답변"}
      </div>
      <p className="whitespace-pre-wrap">{message.content}</p>

      {message.grounding && (
        <PlantCareGroundingNotice grounding={message.grounding} compact />
      )}

      {message.recommendedActions && message.recommendedActions.length > 0 && (
        <div className="mt-2.5 rounded-r-[10px] border-l-2 border-brand bg-brand-soft px-3 py-2">
          <div className="mb-0.5 text-[12px] font-extrabold text-brand-dark">
            지금 해볼 일
          </div>
          <ul className="space-y-0.5 pl-4 text-[13px] leading-[1.55] [list-style:disc]">
            {message.recommendedActions.map((action, index) => (
              <li key={`${index}-${action}`}>{action}</li>
            ))}
          </ul>
        </div>
      )}

      {message.additionalChecks && message.additionalChecks.length > 0 && (
        <div className="mt-1.5 rounded-r-[10px] border-l-2 border-gold bg-gold-soft/70 px-3 py-2">
          <div className="mb-0.5 text-[12px] font-extrabold text-gold-text">
            더 확인해 볼 것
          </div>
          <ul className="space-y-0.5 pl-4 text-[13px] leading-[1.55] [list-style:disc]">
            {message.additionalChecks.map((check, index) => (
              <li key={`${index}-${check}`}>{check}</li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

export default function PlantJournalAssistant({
  plant,
  plantOptions,
  plantOptionsLoading = false,
  onPlantChange,
  accessToken,
}: PlantJournalAssistantProps) {
  const plantId = plant?.id ?? null;
  const [expanded, setExpanded] = useState(false);
  const [activeCategory, setActiveCategory] =
    useState<PlantCareFaqCategory>("journal");
  const [messages, setMessages] = useState<DisplayMessage[]>([]);
  const [question, setQuestion] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  // 열자마자 준비된 질문이 보여야 하므로 초기값은 반드시 true다.
  const [faqOpen, setFaqOpen] = useState(true);
  const nextMessageId = useRef(0);
  const conversationIdRef = useRef<string | null>(null);
  const inFlightRef = useRef(false);
  const controllerRef = useRef<AbortController | null>(null);
  const launcherRef = useRef<HTMLButtonElement>(null);
  const closeButtonRef = useRef<HTMLButtonElement>(null);
  const scrollTargetRef = useRef<HTMLDivElement>(null);
  const conversationEndRef = useRef<HTMLDivElement>(null);
  const lastScrollTargetKeyRef = useRef<string | null>(null);
  const wasExpandedRef = useRef(false);
  const activeChipRef = useRef<HTMLButtonElement>(null);

  const closeAssistant = () => {
    setExpanded(false);
    launcherRef.current?.focus();
  };

  useEffect(() => {
    controllerRef.current?.abort();
    controllerRef.current = null;
    conversationIdRef.current = null;
    inFlightRef.current = false;
    lastScrollTargetKeyRef.current = null;
    setMessages([]);
    setQuestion("");
    setError("");
    setLoading(false);
    setActiveCategory("journal");
    setFaqOpen(true);

    return () => {
      controllerRef.current?.abort();
      controllerRef.current = null;
      inFlightRef.current = false;
    };
  }, [plantId]);

  useEffect(() => {
    if (!expanded) return;

    closeButtonRef.current?.focus();
    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setExpanded(false);
        launcherRef.current?.focus();
      }
    };
    document.addEventListener("keydown", handleEscape);
    return () => document.removeEventListener("keydown", handleEscape);
  }, [expanded]);

  const isEmptyChat = messages.length === 0;
  const lastMessage = messages[messages.length - 1] ?? null;
  const scrollTargetKey = error
    ? null
    : loading
      ? `loading:${lastMessage?.id ?? "none"}`
      : lastMessage
        ? `message:${lastMessage.id}`
        : null;

  useEffect(() => {
    const justOpened = expanded && !wasExpandedRef.current;
    wasExpandedRef.current = expanded;

    if (!expanded) return;

    if (justOpened && messages.length > 0) {
      lastScrollTargetKeyRef.current = scrollTargetKey;
      conversationEndRef.current?.scrollIntoView?.({ block: "end" });
      return;
    }

    if (
      scrollTargetKey === null ||
      scrollTargetKey === lastScrollTargetKeyRef.current
    )
      return;

    // 새 질문의 로딩 말풍선과 새 답변을 필요한 만큼만 이동해 보이게 한다.
    // nearest를 유지해 이전 대화가 화면 위로 과도하게 밀려나는 것도 막는다.
    lastScrollTargetKeyRef.current = scrollTargetKey;
    scrollTargetRef.current?.scrollIntoView?.({ block: "nearest" });
  }, [expanded, messages.length, scrollTargetKey]);

  const newMessageId = () => {
    nextMessageId.current += 1;
    return nextMessageId.current;
  };

  const selectFaq = (faq: PlantCareFaq) => {
    if (!plant || loading) return;
    setError("");
    setMessages((current) => [
      ...current,
      {
        id: newMessageId(),
        role: "USER",
        content: faq.question,
        source: "FAQ",
      },
      {
        id: newMessageId(),
        role: "ASSISTANT",
        content: faq.answer,
        source: "FAQ",
        recommendedActions: faq.recommendedActions,
        additionalChecks: faq.additionalChecks,
      },
    ]);
    // 답변을 읽을 수 있게 목록을 내리고, 사라지는 버튼에 있던 포커스를 레일의 활성 칩으로 넘긴다.
    setFaqOpen(false);
    activeChipRef.current?.focus();
  };

  const sendQuestion = async () => {
    const normalizedQuestion = question.trim();
    if (!plant || !accessToken || !normalizedQuestion || inFlightRef.current)
      return;

    const pendingMessageId = newMessageId();
    const controller = new AbortController();
    controllerRef.current = controller;
    inFlightRef.current = true;
    setLoading(true);
    setError("");
    setFaqOpen(false);
    setMessages((current) => [
      ...current,
      {
        id: pendingMessageId,
        role: "USER",
        content: normalizedQuestion,
        source: "AI",
      },
    ]);

    try {
      const response = await askPlantChat(
        plant.id,
        {
          question: normalizedQuestion,
          conversationId: conversationIdRef.current,
        },
        accessToken,
        controller.signal,
      );
      if (controller.signal.aborted) return;
      conversationIdRef.current = response.conversationId;

      setMessages((current) => [
        ...current,
        {
          id: newMessageId(),
          role: "ASSISTANT",
          content: response.answer,
          source: "AI",
          recommendedActions: response.recommendedActions,
          additionalChecks: response.additionalChecks,
          grounding: response.grounding,
        },
      ]);
      setQuestion("");
    } catch (requestError) {
      if (controller.signal.aborted) return;
      if (
        requestError instanceof ApiError &&
        requestError.code === "AI_CHAT_CONVERSATION_INVALID"
      ) {
        conversationIdRef.current = null;
        setMessages([]);
      } else {
        setMessages((current) =>
          current.filter((message) => message.id !== pendingMessageId),
        );
      }
      setError(toErrorMessage(requestError));
    } finally {
      if (!controller.signal.aborted) {
        controllerRef.current = null;
        inFlightRef.current = false;
        setLoading(false);
      }
    }
  };

  const handleQuestionKeyDown = (
    event: ReactKeyboardEvent<HTMLTextAreaElement>,
  ) => {
    if (
      event.key !== "Enter" ||
      event.shiftKey ||
      event.nativeEvent.isComposing
    )
      return;
    event.preventDefault();
    void sendQuestion();
  };

  const activeFaqs = getPlantCareFaqs(activeCategory);

  return (
    <>
      {expanded && (
        <section
          id={PANEL_ID}
          role="dialog"
          aria-modal="false"
          aria-labelledby="plant-journal-assistant-title"
          /* 짧은 뷰포트(가로 모드 휴대폰 등)에서는 하단 여백을 최소화해 높이를 최대한 확보한다.
             그러지 않으면 고정 크롬(헤더+레일+컴포저 약 254px)만으로 패널이 가득 차 대화 영역이 71px까지 눌린다. */
          className="fixed bottom-[82px] right-4 z-[55] flex h-[min(740px,calc(100dvh-108px))] w-[calc(100vw-32px)] max-w-[440px] animate-pop flex-col overflow-hidden rounded-[28px] border border-[#d5ded0] bg-[#edf2e8] [box-shadow:0_28px_80px_rgba(47,58,45,.22)] [@media(max-height:560px)]:bottom-2 [@media(max-height:560px)]:h-[calc(100dvh-16px)] md:bottom-6 md:right-6 md:h-[min(740px,calc(100dvh-48px))] md:[@media(max-height:560px)]:h-[calc(100dvh-16px)]"
        >
          <header className="flex flex-none items-center gap-3 border-b border-[#d5ded0] bg-[linear-gradient(135deg,#f9fbf6_0%,#eaf1e4_100%)] px-4 py-2.5">
            <AssistantMark compact />
            <span className="min-w-0 flex-1">
              {/* 이 span의 텍스트는 dialog의 접근명이 된다. 배지·부제를 넣지 말 것. */}
              <span
                id="plant-journal-assistant-title"
                className="block truncate text-[15.5px] font-extrabold leading-tight tracking-[-0.02em] text-ink"
              >
                AI 식물 도우미
              </span>

              {!plant ? null : plantOptions &&
                plantOptions.length > 1 &&
                onPlantChange ? (
                <span className="relative mt-1 block w-fit max-w-full">
                  <select
                    aria-label="상담할 식물 선택"
                    value={plant.id}
                    onChange={(event) =>
                      onPlantChange(Number(event.target.value))
                    }
                    disabled={loading}
                    className="h-8 w-full max-w-[210px] cursor-pointer appearance-none truncate rounded-full border border-[#c4d0bc] bg-white pl-3 pr-7 text-[12px] font-extrabold text-ink outline-none focus:border-brand disabled:bg-[#f3f4ef]"
                  >
                    {plantOptions.map((option) => (
                      <option key={option.id} value={option.id}>
                        {option.nickname} ({option.speciesName})
                      </option>
                    ))}
                  </select>
                  <span
                    aria-hidden="true"
                    className="material-symbols-outlined pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 text-[16px] text-sub"
                  >
                    expand_more
                  </span>
                </span>
              ) : (
                <span className="mt-1 flex h-8 w-fit max-w-full items-center gap-1.5 rounded-full border border-[#c4d0bc] bg-white pl-2 pr-3 text-[12px] font-extrabold text-ink">
                  <span
                    aria-hidden="true"
                    className="material-symbols-outlined flex-none text-[15px] text-brand-dark"
                  >
                    potted_plant
                  </span>
                  {/* truncate는 flex 컨테이너에 걸면 소용없다 — 텍스트가 익명 flex 아이템이 되어
                      text-overflow가 적용되지 않고 말줄임 없이 글자 중간에서 잘린다.
                      그래서 텍스트만 감싸는 안쪽 span에 건다.
                      단, 닉네임과 종명은 반드시 이 한 span의 직계 텍스트 노드로 함께 두어야 한다
                      (서로 다른 element로 쪼개면 테스트의 getByText(/상추\(청상추\)/)가 깨진다). */}
                  <span className="min-w-0 truncate">
                    {plant.nickname}({plant.speciesName})
                  </span>
                </span>
              )}
            </span>
            <button
              ref={closeButtonRef}
              type="button"
              onClick={closeAssistant}
              aria-label="AI 식물 도우미 닫기"
              className="flex h-9 w-9 flex-none cursor-pointer items-center justify-center rounded-full bg-[#f1f3ee] text-sub hover:bg-[#e8ece4] hover:text-ink"
            >
              <span
                aria-hidden="true"
                className="material-symbols-outlined text-[20px]"
              >
                close
              </span>
            </button>
          </header>

          {plantOptionsLoading ? (
            <div
              role="status"
              className="flex min-h-0 flex-1 flex-col items-center justify-center gap-3 bg-[#f2f6ee] px-6 text-center text-sm font-semibold text-[#687465]"
            >
              <AssistantMark compact />내 식물을 불러오고 있어요…
            </div>
          ) : !plant ? (
            <div className="flex min-h-0 flex-1 flex-col items-center justify-center bg-[#f2f6ee] px-6 text-center">
              <AssistantMark />
              <h2 className="mt-4 text-lg font-extrabold text-ink">
                먼저 상담할 식물이 필요해요
              </h2>
              <p className="mt-2 max-w-[280px] text-sm leading-[1.6] text-sub">
                식물을 먼저 선택하면 준비 답변과 내 기록 기반 AI 질문을 이용할
                수 있어요.
              </p>
              {plantOptions && (
                <Link
                  href="/plants"
                  className="mt-5 rounded-xl bg-brand px-4 py-2.5 text-sm font-extrabold text-white hover:text-white"
                >
                  내 식물 보러 가기
                </Link>
              )}
            </div>
          ) : (
            <>
              <div className="relative min-h-0 flex-1 overflow-hidden bg-[#edf2e8]">
                <div className="absolute inset-0 overflow-y-auto overscroll-contain px-3.5 py-3">
                  {/* 짧은 대화는 입력창 가까이에 붙이고, 길어진 대화는 이 내부 높이가 자연스럽게
                      늘어나도록 해 이전 FAQ와 직접 질문 사이에 빈 여백이 생기지 않게 한다. */}
                  <div className="flex min-h-full flex-col justify-end">
                    {/* 메시지가 0건이어도 항상 마운트한다. 첫 메시지와 동시에 생기면
                        스크린리더가 첫 답변을 읽지 못한다. */}
                    <div
                      role="log"
                      aria-live="polite"
                      aria-label="AI 식물 도우미 대화"
                      className="space-y-2.5"
                    >
                      {messages.map((message) =>
                        message.role === "USER" ? (
                          <div
                            key={message.id}
                            ref={
                              !loading && message.id === lastMessage?.id
                                ? scrollTargetRef
                                : undefined
                            }
                            className="flex scroll-mb-3 scroll-mt-3 justify-end"
                          >
                            <div className="max-w-[88%] rounded-[18px] rounded-tr-[6px] bg-brand-dark px-3.5 py-2.5 text-[14px] leading-[1.6] text-white">
                              {message.content}
                            </div>
                          </div>
                        ) : (
                          <div
                            key={message.id}
                            ref={
                              !loading && message.id === lastMessage?.id
                                ? scrollTargetRef
                                : undefined
                            }
                            className="flex scroll-mb-3 scroll-mt-3 justify-start"
                          >
                            <AssistantMessage message={message} />
                          </div>
                        ),
                      )}
                    </div>

                    {/* 화면 전체에서 role="status"는 이것 하나뿐이어야 한다.
                        레일·컴포저·시트에 별도 "답변 중" 표시를 추가하지 말 것. */}
                    {loading && (
                      <div
                        ref={scrollTargetRef}
                        role="status"
                        className="mt-2.5 flex scroll-mb-3 scroll-mt-3 justify-start"
                      >
                        <div className="rounded-2xl rounded-tl-sm border border-line bg-white px-4 py-3 text-sm font-semibold text-sub">
                          {plant.nickname}의 기록을 살펴보고 있어요…
                        </div>
                      </div>
                    )}
                    <div
                      ref={conversationEndRef}
                      data-testid="plant-journal-chat-end"
                      aria-hidden="true"
                    />
                  </div>
                </div>

                {!isEmptyChat && (
                  <div
                    aria-hidden="true"
                    onClick={() => setFaqOpen(false)}
                    className={`absolute inset-0 z-20 bg-ink transition-opacity duration-200 motion-reduce:transition-none ${
                      faqOpen ? "opacity-25" : "pointer-events-none opacity-0"
                    }`}
                  />
                )}

                {/* FAQ 시트: 이 트리는 절대 언마운트하거나 부모를 옮기지 않는다.
                    접기는 className 교체(invisible)로만 표현한다 — 조건부 렌더나 hidden 속성으로
                    바꾸면 대화 도중 FAQ 버튼 노드가 사라져 대화 맥락 전달 테스트가 깨진다.
                    inert는 쓰지 않는다: React 18.3.1에서 inert={true}는 tsc를 통과하면서도
                    DOM에 속성이 나가지 않는 no-op이다. visibility:hidden이 같은 효과
                    (포커스 불가 + 보조기술 비노출)를 클래스 하나로 준다. */}
                <div
                  id={FAQ_SHEET_ID}
                  role="group"
                  aria-label="자주 묻는 질문"
                  className={`absolute inset-x-0 bottom-0 z-30 flex flex-col overflow-hidden bg-[#f4f7f1] transition-transform duration-200 ease-[cubic-bezier(.22,.68,.28,1)] motion-reduce:transition-none ${
                    isEmptyChat
                      ? "top-0 justify-center"
                      : `max-h-[min(320px,100%)] rounded-t-[22px] border-t border-[#d5ded0] [box-shadow:0_-16px_40px_rgba(47,58,45,.16)] ${
                          faqOpen
                            ? "translate-y-0"
                            : "invisible translate-y-full"
                        }`
                  }`}
                >
                  {/* 빈 상태에서는 남는 세로 공간을 위아래로 나눠 갖도록 가운데 정렬한다.
                      위로 몰아붙이면 아래에 큰 빈칸이 생겨 화면이 비어 보인다. */}
                  <div
                    className={
                      isEmptyChat
                        ? "flex flex-none flex-col items-center px-5 pb-4 text-center"
                        : "relative flex flex-none items-center gap-2 px-3.5 pb-2 pt-3"
                    }
                  >
                    {isEmptyChat ? (
                      /* 짧은 뷰포트에서는 히어로 장식(마크·보조문구)을 접어 질문 목록에 높이를 넘긴다. */
                      <>
                        <span className="[@media(max-height:560px)]:hidden">
                          <AssistantMark compact />
                        </span>
                        <h2 className="mt-2.5 text-[15.5px] font-extrabold text-ink [@media(max-height:560px)]:mt-0">
                          {plant.nickname}에 대해 무엇이 궁금하세요?
                        </h2>
                        <p className="mt-1 text-[12.5px] leading-[1.5] text-sub [@media(max-height:560px)]:hidden">
                          아래 질문을 골라 바로 확인하거나, 직접 물어보세요.
                        </p>
                      </>
                    ) : (
                      <>
                        <span
                          aria-hidden="true"
                          className="absolute left-1/2 top-1.5 h-1 w-9 -translate-x-1/2 rounded-full bg-[#cdd6c6]"
                        />
                        <h2 className="flex-1 text-[13px] font-extrabold text-[#52604f]">
                          자주 묻는 질문
                        </h2>
                        <button
                          type="button"
                          onClick={() => setFaqOpen(false)}
                          aria-label="자주 묻는 질문 닫기"
                          className="flex h-8 w-8 flex-none cursor-pointer items-center justify-center rounded-full text-sub hover:bg-[#e8ece4] hover:text-ink"
                        >
                          <span
                            aria-hidden="true"
                            className="material-symbols-outlined text-[19px]"
                          >
                            keyboard_arrow_down
                          </span>
                        </button>
                      </>
                    )}
                  </div>

                  <div
                    className={`min-h-0 overflow-y-auto overscroll-contain px-3.5 pb-3.5 ${
                      isEmptyChat ? "flex-initial" : "flex-1"
                    }`}
                  >
                    <div className="divide-y divide-line overflow-hidden rounded-[14px] border border-line bg-white">
                      {activeFaqs.map((faq) => (
                        <button
                          key={faq.id}
                          type="button"
                          onClick={() => selectFaq(faq)}
                          disabled={loading}
                          /* 이 버튼의 접근명은 faq.question과 완전히 일치해야 한다.
                             보이는 텍스트를 더 넣으려면 반드시 aria-hidden 처리할 것. */
                          className="group flex w-full cursor-pointer items-center gap-2 px-3.5 py-2.5 text-left text-[13px] font-bold leading-[1.45] text-[#465442] hover:bg-[#f7faf4] disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          <span className="min-w-0 flex-1">{faq.question}</span>
                          <span
                            aria-hidden="true"
                            className="material-symbols-outlined flex-none text-[17px] text-faint group-hover:text-brand-dark"
                          >
                            chevron_right
                          </span>
                        </button>
                      ))}
                    </div>
                  </div>
                </div>
              </div>

              {/* 칩 5개의 총 너비(약 480px)는 모바일 레일 가용 폭(약 280~320px)을 항상 넘는다.
                  가로 스크롤로 두면 마지막 칩이 잘려 보이고 대화 중에는 토글에 가려 아예 사라지므로,
                  줄바꿈으로 5개를 항상 노출한다. */}
              <div className="flex flex-none items-start gap-1.5 border-t border-[#d5ded0] bg-[#f4f7f1] px-3 py-1.5">
                <div
                  role="group"
                  aria-label="자주 묻는 질문 분류"
                  className="flex min-w-0 flex-1 flex-wrap gap-1"
                >
                  {PLANT_CARE_FAQ_CATEGORIES.map((category) => (
                    <button
                      key={category.id}
                      ref={
                        category.id === activeCategory
                          ? activeChipRef
                          : undefined
                      }
                      type="button"
                      onClick={() => {
                        setActiveCategory(category.id);
                        setFaqOpen(true);
                      }}
                      aria-pressed={activeCategory === category.id}
                      aria-controls={FAQ_SHEET_ID}
                      disabled={loading}
                      className={`flex h-[30px] flex-none cursor-pointer items-center gap-1 rounded-full border px-2.5 text-[12px] font-extrabold disabled:cursor-not-allowed disabled:opacity-60 ${
                        activeCategory === category.id
                          ? "border-ink bg-ink text-white shadow-sm"
                          : "border-[#c4d0bc] bg-white text-[#566252] hover:border-[#aebe9f]"
                      }`}
                    >
                      <span
                        aria-hidden="true"
                        className="material-symbols-outlined text-[15px]"
                      >
                        {category.icon}
                      </span>
                      {category.label}
                    </button>
                  ))}
                </div>

                {!isEmptyChat && (
                  <button
                    type="button"
                    onClick={() => setFaqOpen((open) => !open)}
                    aria-expanded={faqOpen}
                    aria-controls={FAQ_SHEET_ID}
                    aria-label={
                      faqOpen
                        ? "자주 묻는 질문 목록 접기"
                        : "자주 묻는 질문 목록 펼치기"
                    }
                    disabled={loading}
                    className="flex h-[30px] w-[30px] flex-none shrink-0 cursor-pointer items-center justify-center rounded-full border border-[#c4d0bc] bg-white text-sub hover:text-ink disabled:opacity-60"
                  >
                    <span
                      aria-hidden="true"
                      className={`material-symbols-outlined text-[18px] transition-transform motion-reduce:transition-none ${
                        faqOpen ? "" : "rotate-180"
                      }`}
                    >
                      keyboard_arrow_down
                    </span>
                  </button>
                )}
              </div>

              <div className="flex-none border-t border-[#d5ded0] bg-[#f2f6ee] px-3.5 pb-2.5 pt-2">
                {/* 이 alert 안에 재시도 버튼을 넣지 말 것 — 서버 메시지에 이미 "다시 시도" 문구가 있어
                    안내가 중복된다. */}
                {error && (
                  <div
                    role="alert"
                    className="mb-2 rounded-xl bg-danger-soft px-3.5 py-2.5 text-[13px] font-semibold leading-[1.45] text-danger"
                  >
                    {error}
                  </div>
                )}

                <label
                  htmlFor="plant-journal-chat-question"
                  className="sr-only"
                >
                  내 식물 기록을 바탕으로 직접 질문하기
                </label>

                <div className="relative flex items-end gap-2 rounded-[18px] border-[1.5px] border-[#c4d0bc] bg-white p-2 focus-within:border-brand-dark focus-within:[box-shadow:0_0_0_3px_rgba(124,179,66,.16)]">
                  <textarea
                    id="plant-journal-chat-question"
                    name="plantJournalAiQuestion"
                    value={question}
                    onChange={(event) => setQuestion(event.target.value)}
                    onKeyDown={handleQuestionKeyDown}
                    autoComplete="off"
                    maxLength={MAX_QUESTION_LENGTH}
                    rows={2}
                    disabled={loading}
                    placeholder={`${plant.speciesName}에 대해 궁금한 점을 입력해 주세요.`}
                    className="min-h-[44px] w-full flex-1 resize-none bg-transparent px-1 text-[14px] leading-[1.5] text-ink outline-none placeholder:font-medium placeholder:text-[#687565] disabled:text-sub [@media(max-height:560px)]:h-[34px] [@media(max-height:560px)]:min-h-0"
                  />
                  <button
                    type="button"
                    onClick={() => void sendQuestion()}
                    disabled={loading || !accessToken || !question.trim()}
                    aria-label="AI에게 묻기"
                    className="mb-0.5 flex h-9 w-9 flex-none cursor-pointer items-center justify-center rounded-full bg-brand-dark text-white shadow-sm disabled:cursor-not-allowed disabled:bg-[#aab9a0] disabled:opacity-100"
                  >
                    <span
                      aria-hidden="true"
                      className="material-symbols-outlined text-[20px]"
                    >
                      arrow_upward
                    </span>
                  </button>
                  {/* 글자수는 한도에 가까워질 때만 띄운다. absolute라 평소 세로 공간을 먹지 않는다. */}
                  {question.length >= MAX_QUESTION_LENGTH * 0.9 && (
                    <span className="pointer-events-none absolute bottom-1 right-12 text-[10px] font-bold text-danger">
                      <span className="sr-only">입력 글자 수 </span>
                      {question.length}/{MAX_QUESTION_LENGTH}
                    </span>
                  )}
                </div>

                {/* 이 문단의 직계 텍스트는 AI_DISCLAIMER 하나만 유지한다(아이콘·글자수 재삽입 금지). */}
                <p className="mt-1 text-center text-[10px] font-medium leading-[1.45] text-[#6f7b6b]">
                  {AI_DISCLAIMER}
                </p>
              </div>
            </>
          )}
        </section>
      )}

      <button
        ref={launcherRef}
        type="button"
        onClick={() => setExpanded(true)}
        aria-expanded={expanded}
        aria-controls={PANEL_ID}
        aria-label="식물을 키우다 궁금한 점이 있나요? AI 식물 도우미 열기"
        aria-hidden={expanded}
        tabIndex={expanded ? -1 : 0}
        className={`fixed bottom-[82px] right-4 z-[54] flex h-[60px] cursor-pointer items-center gap-2.5 rounded-[20px] border border-[#d5ded0] bg-white py-2 pl-2.5 pr-4 text-ink [box-shadow:0_14px_38px_rgba(47,58,45,.18)] md:bottom-6 md:right-6 ${
          expanded
            ? "pointer-events-none translate-y-2 opacity-0"
            : "opacity-100"
        }`}
      >
        <AssistantMark compact inverted />
        <span className="hidden pr-1 text-left sm:block">
          <span className="block text-[13.5px] font-extrabold tracking-[-0.01em] text-ink">
            AI 식물 도우미
          </span>
          <span className="mt-0.5 block text-[10px] font-semibold text-sub">
            궁금한 점을 물어보세요
          </span>
        </span>
      </button>
    </>
  );
}
