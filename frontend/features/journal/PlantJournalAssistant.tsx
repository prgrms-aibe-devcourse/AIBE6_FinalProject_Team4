"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import type { KeyboardEvent as ReactKeyboardEvent } from "react";
import {
  getPlantCareFaqs,
  PlantCareFaq,
  PlantCareFaqCategory,
  PLANT_CARE_FAQ_CATEGORIES,
} from "@/features/journal/plant-care-faqs";
import {
  askPlantChat,
  PlantChatMessagePayload,
} from "@/features/journal/plant-chat-api";
import { ApiError } from "@/lib/api";

const MAX_QUESTION_LENGTH = 2000;
const MAX_CONTEXT_MESSAGES = 6;
const MAX_CONTEXT_MESSAGE_LENGTH = 1000;
const MAX_CONTEXT_TOTAL_LENGTH = 4000;
const PANEL_ID = "plant-journal-assistant-panel";
const AI_DISCLAIMER =
  "AI 답변은 식물 프로필과 최근 일지를 바탕으로 만든 참고 정보이며 정확성을 보장하지 않습니다.";

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
}

interface PlantJournalAssistantProps {
  plant: PlantSummary | null;
  plantOptions?: readonly PlantSummary[];
  plantOptionsLoading?: boolean;
  onPlantChange?: (plantId: number) => void;
  currentJournalContent: string;
  accessToken: string | null;
}

function contextContent(message: DisplayMessage): string {
  const sections = [message.content];
  if (message.recommendedActions?.length) {
    sections.push(`권장 행동: ${message.recommendedActions.join(" / ")}`);
  }
  if (message.additionalChecks?.length) {
    sections.push(`추가 확인: ${message.additionalChecks.join(" / ")}`);
  }
  return sections.join("\n").slice(0, MAX_CONTEXT_MESSAGE_LENGTH);
}

function recentMessagePayload(
  messages: readonly DisplayMessage[],
): PlantChatMessagePayload[] {
  const selected: PlantChatMessagePayload[] = [];
  let totalLength = 0;

  for (
    let index = messages.length - 1;
    index >= 0 && selected.length < MAX_CONTEXT_MESSAGES;
    index -= 1
  ) {
    const message = messages[index];
    const content = contextContent(message).trim();
    if (!content) continue;
    if (totalLength + content.length > MAX_CONTEXT_TOTAL_LENGTH) break;

    selected.unshift({ role: message.role, content });
    totalLength += content.length;
  }

  return selected;
}

function toErrorMessage(requestError: unknown): string {
  if (requestError instanceof ApiError) return requestError.message;
  return "AI 답변을 불러오지 못했어요. 네트워크 상태를 확인하고 다시 시도해 주세요.";
}

function AssistantMessage({ message }: { message: DisplayMessage }) {
  return (
    <div className="max-w-[94%] rounded-2xl rounded-tl-sm border border-line bg-white px-4 py-3 text-[14px] leading-[1.65] text-[#4a5647] shadow-sm">
      <div className="mb-1.5 flex items-center gap-1.5 text-[11px] font-extrabold text-brand-text">
        <span
          aria-hidden="true"
          className="material-symbols-outlined text-[15px]"
        >
          {message.source === "FAQ" ? "menu_book" : "auto_awesome"}
        </span>
        {message.source === "FAQ" ? "준비된 답변" : "내 식물 기록 기반 AI 답변"}
      </div>
      <p className="whitespace-pre-wrap">{message.content}</p>

      {message.recommendedActions && message.recommendedActions.length > 0 && (
        <div className="mt-3 rounded-xl bg-brand-soft px-3 py-2.5">
          <div className="mb-1 text-[12px] font-extrabold text-brand-dark">
            지금 해볼 일
          </div>
          <ul className="space-y-1 pl-4 text-[13px] [list-style:disc]">
            {message.recommendedActions.map((action, index) => (
              <li key={`${index}-${action}`}>{action}</li>
            ))}
          </ul>
        </div>
      )}

      {message.additionalChecks && message.additionalChecks.length > 0 && (
        <div className="mt-2 rounded-xl bg-[#f8f6ee] px-3 py-2.5">
          <div className="mb-1 text-[12px] font-extrabold text-[#8a6d00]">
            더 확인해 볼 것
          </div>
          <ul className="space-y-1 pl-4 text-[13px] [list-style:disc]">
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
  currentJournalContent,
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
  const nextMessageId = useRef(0);
  const inFlightRef = useRef(false);
  const controllerRef = useRef<AbortController | null>(null);
  const launcherRef = useRef<HTMLButtonElement>(null);
  const closeButtonRef = useRef<HTMLButtonElement>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const closeAssistant = () => {
    setExpanded(false);
    launcherRef.current?.focus();
  };

  useEffect(() => {
    controllerRef.current?.abort();
    controllerRef.current = null;
    inFlightRef.current = false;
    setMessages([]);
    setQuestion("");
    setError("");
    setLoading(false);
    setActiveCategory("journal");

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

  useEffect(() => {
    if (!expanded || messages.length === 0) return;
    messagesEndRef.current?.scrollIntoView?.({ block: "nearest" });
  }, [expanded, loading, messages]);

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
  };

  const sendQuestion = async () => {
    const normalizedQuestion = question.trim();
    if (!plant || !accessToken || !normalizedQuestion || inFlightRef.current)
      return;

    const previousMessages = messages;
    const pendingMessageId = newMessageId();
    const controller = new AbortController();
    controllerRef.current = controller;
    inFlightRef.current = true;
    setLoading(true);
    setError("");
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
          currentJournalContent: currentJournalContent.trim() || null,
          recentMessages: recentMessagePayload(previousMessages),
        },
        accessToken,
        controller.signal,
      );
      if (controller.signal.aborted) return;

      setMessages((current) => [
        ...current,
        {
          id: newMessageId(),
          role: "ASSISTANT",
          content: response.answer,
          source: "AI",
          recommendedActions: response.recommendedActions,
          additionalChecks: response.additionalChecks,
        },
      ]);
      setQuestion("");
    } catch (requestError) {
      if (controller.signal.aborted) return;
      setMessages((current) =>
        current.filter((message) => message.id !== pendingMessageId),
      );
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
  const hasAnswer = messages.some((message) => message.role === "ASSISTANT");

  return (
    <>
      {expanded && (
        <section
          id={PANEL_ID}
          role="dialog"
          aria-modal="false"
          aria-labelledby="plant-journal-assistant-title"
          className="fixed bottom-[82px] right-4 z-[55] flex h-[min(720px,calc(100dvh-108px))] w-[calc(100vw-32px)] max-w-[430px] animate-pop flex-col overflow-hidden rounded-[24px] border border-[#dbe6cf] bg-[#f7faef] [box-shadow:0_24px_70px_rgba(62,74,61,.24)] md:bottom-6 md:right-6 md:h-[min(720px,calc(100dvh-48px))]"
        >
          <header className="relative flex flex-none items-center gap-3 overflow-hidden border-b border-[#dbe6cf] bg-[linear-gradient(135deg,#eef5e4_0%,#fffdf6_100%)] px-4 py-3.5">
            <span
              aria-hidden="true"
              className="absolute -right-8 -top-10 h-28 w-28 rounded-full bg-brand/10"
            />
            <span className="relative flex h-11 w-11 flex-none items-center justify-center rounded-2xl bg-brand text-[23px] shadow-sm">
              🌱
            </span>
            <span className="relative min-w-0 flex-1">
              <span
                id="plant-journal-assistant-title"
                className="block text-[17px] font-extrabold text-ink"
              >
                식물 도우미
              </span>
              <span className="mt-0.5 block text-[11.5px] font-semibold text-sub">
                성장 기록과 함께 답을 찾아드려요
              </span>
            </span>
            <button
              ref={closeButtonRef}
              type="button"
              onClick={closeAssistant}
              aria-label="식물 도우미 닫기"
              className="relative flex h-9 w-9 cursor-pointer items-center justify-center rounded-full border border-line bg-white/85 text-sub hover:text-ink"
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
              className="flex min-h-0 flex-1 flex-col items-center justify-center gap-3 px-6 text-center text-sm font-semibold text-sub"
            >
              <span className="text-3xl">🌿</span>내 식물을 불러오고 있어요…
            </div>
          ) : !plant ? (
            <div className="flex min-h-0 flex-1 flex-col items-center justify-center px-6 text-center">
              <span className="flex h-16 w-16 items-center justify-center rounded-full bg-brand-soft text-3xl">
                🪴
              </span>
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
              <div className="flex-none border-b border-[#e2ead8] bg-white/80 px-4 py-3">
                {plantOptions && plantOptions.length > 1 && onPlantChange ? (
                  <label className="flex items-center gap-3">
                    <span className="flex-none text-[12px] font-extrabold text-[#52604f]">
                      상담할 식물
                    </span>
                    <span className="relative min-w-0 flex-1">
                      <select
                        aria-label="상담할 식물 선택"
                        value={plant.id}
                        onChange={(event) =>
                          onPlantChange(Number(event.target.value))
                        }
                        disabled={loading}
                        className="w-full appearance-none rounded-xl border border-line bg-white py-2 pl-3 pr-9 text-[13px] font-extrabold text-ink outline-none focus:border-brand disabled:bg-[#f3f4ef]"
                      >
                        {plantOptions.map((option) => (
                          <option key={option.id} value={option.id}>
                            {option.nickname} ({option.speciesName})
                          </option>
                        ))}
                      </select>
                      <span
                        aria-hidden="true"
                        className="material-symbols-outlined pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 text-[18px] text-sub"
                      >
                        expand_more
                      </span>
                    </span>
                  </label>
                ) : (
                  <div className="flex items-center gap-2 text-[13px] font-extrabold text-ink">
                    <span
                      aria-hidden="true"
                      className="material-symbols-outlined text-[18px] text-brand-dark"
                    >
                      potted_plant
                    </span>
                    {plant.nickname}({plant.speciesName})
                  </div>
                )}
                <p className="mt-1.5 text-[11px] text-sub">
                  선택한 식물의 프로필과 최근 일지를 참고해요.
                </p>
              </div>

              <div className="min-h-0 flex-1 overflow-y-auto px-4 py-4">
                {messages.length === 0 && (
                  <div className="mb-4 rounded-2xl border border-[#dde8d2] bg-white px-4 py-4 shadow-sm">
                    <div className="flex items-start gap-3">
                      <span className="flex h-10 w-10 flex-none items-center justify-center rounded-full bg-brand-soft text-xl">
                        🌿
                      </span>
                      <div>
                        <h2 className="text-[15px] font-extrabold text-ink">
                          {plant.nickname}를 키우며 무엇이 궁금한가요?
                        </h2>
                        <p className="mt-1 text-[12.5px] leading-[1.55] text-sub">
                          자주 묻는 질문은 무료로 바로 확인하고, 더 궁금한
                          내용은 아래에서 AI에게 물어보세요.
                        </p>
                      </div>
                    </div>
                  </div>
                )}

                <div className="mb-2 flex items-center justify-between gap-2">
                  <h2 className="text-[13px] font-extrabold text-[#52604f]">
                    자주 묻는 질문
                  </h2>
                  <span className="rounded-full bg-white px-2 py-1 text-[10px] font-bold text-brand-dark">
                    AI 호출 없음
                  </span>
                </div>
                <div
                  className="flex gap-1.5 overflow-x-auto pb-1"
                  aria-label="자주 묻는 질문 분류"
                >
                  {PLANT_CARE_FAQ_CATEGORIES.map((category) => (
                    <button
                      key={category.id}
                      type="button"
                      onClick={() => setActiveCategory(category.id)}
                      aria-pressed={activeCategory === category.id}
                      disabled={loading}
                      className={`flex flex-none cursor-pointer items-center gap-1 rounded-full border px-3 py-1.5 text-xs font-extrabold disabled:cursor-not-allowed disabled:opacity-60 ${
                        activeCategory === category.id
                          ? "border-brand bg-brand text-white"
                          : "border-line bg-white text-[#6d7a68]"
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

                <div className="mt-2.5 grid gap-2">
                  {activeFaqs.map((faq) => (
                    <button
                      key={faq.id}
                      type="button"
                      onClick={() => selectFaq(faq)}
                      disabled={loading}
                      className="group flex cursor-pointer items-center gap-2 rounded-xl border border-line bg-white px-3 py-2.5 text-left text-[13px] font-bold leading-[1.45] text-[#52604f] hover:border-brand hover:bg-brand-soft disabled:cursor-not-allowed disabled:opacity-60"
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

                {messages.length > 0 && (
                  <div
                    role="log"
                    aria-live="polite"
                    aria-label="식물 도우미 대화"
                    className="mt-4 space-y-3 border-t border-[#dde6d4] pt-4"
                  >
                    {messages.map((message) =>
                      message.role === "USER" ? (
                        <div key={message.id} className="flex justify-end">
                          <div className="max-w-[88%] rounded-2xl rounded-tr-sm bg-brand px-3.5 py-2.5 text-[14px] leading-[1.6] text-white shadow-sm">
                            {message.content}
                          </div>
                        </div>
                      ) : (
                        <div key={message.id} className="flex justify-start">
                          <AssistantMessage message={message} />
                        </div>
                      ),
                    )}
                    {loading && (
                      <div role="status" className="flex justify-start">
                        <div className="rounded-2xl rounded-tl-sm border border-line bg-white px-4 py-3 text-sm font-semibold text-sub">
                          {plant.nickname}의 기록을 살펴보고 있어요…
                        </div>
                      </div>
                    )}
                    <div ref={messagesEndRef} />
                  </div>
                )}

                {hasAnswer && (
                  <div className="mt-4 flex flex-wrap items-center justify-between gap-2 rounded-xl bg-white px-3.5 py-3 text-[12.5px] text-sub">
                    <span>그래도 궁금한 점이 해결되지 않았나요?</span>
                    <Link
                      href="/my/inquiries"
                      className="font-extrabold text-brand-dark"
                    >
                      1:1 문의하기 →
                    </Link>
                  </div>
                )}
              </div>

              <div className="flex-none border-t border-[#dbe6cf] bg-white px-3.5 pb-3 pt-3">
                {error && (
                  <div
                    role="alert"
                    className="mb-2.5 rounded-xl bg-danger-soft px-3.5 py-2.5 text-[13px] font-semibold text-danger"
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
                <div className="rounded-2xl border-[1.5px] border-line bg-[#fdfdf9] p-2.5 focus-within:border-brand">
                  <textarea
                    id="plant-journal-chat-question"
                    value={question}
                    onChange={(event) => setQuestion(event.target.value)}
                    onKeyDown={handleQuestionKeyDown}
                    maxLength={MAX_QUESTION_LENGTH}
                    rows={2}
                    disabled={loading}
                    placeholder={`${plant.nickname}에 대해 궁금한 점을 입력해 주세요.`}
                    className="min-h-[54px] w-full resize-none bg-transparent px-1 text-[14px] leading-[1.5] text-ink outline-none placeholder:text-faint disabled:text-sub"
                  />
                  <div className="mt-1 flex items-center justify-between gap-3">
                    <span className="pl-1 text-[10.5px] text-faint">
                      {question.length} / {MAX_QUESTION_LENGTH}
                    </span>
                    <button
                      type="button"
                      onClick={() => void sendQuestion()}
                      disabled={loading || !accessToken || !question.trim()}
                      aria-label="AI에게 묻기"
                      className="flex h-9 w-9 flex-none cursor-pointer items-center justify-center rounded-full bg-brand text-white shadow-sm disabled:cursor-not-allowed disabled:opacity-40"
                    >
                      <span
                        aria-hidden="true"
                        className="material-symbols-outlined text-[20px]"
                      >
                        arrow_upward
                      </span>
                    </button>
                  </div>
                </div>
                <p className="mt-2 text-center text-[10px] leading-[1.45] text-faint">
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
        aria-label="식물을 키우다 궁금한 점이 있나요? 식물 도우미 열기"
        aria-hidden={expanded}
        tabIndex={expanded ? -1 : 0}
        className={`fixed bottom-[82px] right-4 z-[54] flex h-14 cursor-pointer items-center gap-2.5 rounded-full border border-[#d7e5c8] bg-brand-dark px-3.5 text-white [box-shadow:0_12px_32px_rgba(85,139,47,.32)] md:bottom-6 md:right-6 ${
          expanded
            ? "pointer-events-none translate-y-2 opacity-0"
            : "opacity-100"
        }`}
      >
        <span className="flex h-9 w-9 items-center justify-center rounded-full bg-white/15 text-xl">
          🌱
        </span>
        <span className="hidden pr-1 text-left sm:block">
          <span className="block text-[13px] font-extrabold">식물 도우미</span>
          <span className="block text-[10px] font-semibold text-white/75">
            궁금한 점을 물어보세요
          </span>
        </span>
      </button>
    </>
  );
}
