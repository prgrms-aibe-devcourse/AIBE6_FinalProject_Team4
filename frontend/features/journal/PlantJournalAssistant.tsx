"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import type { KeyboardEvent } from "react";
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
    <div className="max-w-[92%] rounded-2xl rounded-tl-sm border border-line bg-white px-4 py-3 text-[14px] leading-[1.65] text-[#4a5647] shadow-sm">
      <div className="mb-1.5 text-[11px] font-extrabold text-brand-text">
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

  const handleQuestionKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
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
    <section className="mt-4 overflow-hidden rounded-[16px] border-[1.5px] border-[#dce8ce] bg-[#f8fbf3]">
      <button
        type="button"
        onClick={() => setExpanded((current) => !current)}
        aria-expanded={expanded}
        aria-controls="plant-journal-assistant-content"
        className="flex w-full cursor-pointer items-center gap-3 px-4 py-3.5 text-left"
      >
        <span className="flex h-10 w-10 flex-none items-center justify-center rounded-full bg-brand text-xl text-white">
          🌱
        </span>
        <span className="min-w-0 flex-1">
          <span className="block text-[15px] font-extrabold text-ink">
            식물을 키우다 궁금한 점이 있나요?
          </span>
          <span className="mt-0.5 block text-[12px] text-sub">
            무료 준비 답변 또는 내 일지 기반 AI에게 물어보세요.
          </span>
        </span>
        <span
          aria-hidden="true"
          className="material-symbols-outlined flex-none text-brand-dark"
        >
          {expanded ? "expand_less" : "expand_more"}
        </span>
      </button>

      {expanded && (
        <div
          id="plant-journal-assistant-content"
          className="border-t border-[#dce8ce] px-4 pb-4 pt-3.5"
        >
          {!plant ? (
            <div className="rounded-xl bg-white px-4 py-4 text-center text-sm font-semibold text-sub">
              위에서 식물을 먼저 선택하면 준비 답변과 맞춤 AI 질문을 이용할 수
              있어요.
            </div>
          ) : (
            <>
              <div className="mb-3">
                <div className="text-[14px] font-extrabold text-ink">
                  {plant.nickname}({plant.speciesName})에 대해 무엇이
                  궁금한가요?
                </div>
                <p className="mt-1 text-xs leading-[1.55] text-sub">
                  준비 답변은 바로 확인할 수 있고 AI 호출 횟수를 사용하지
                  않아요.
                </p>
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

              <div className="mt-2.5 flex flex-wrap gap-2">
                {activeFaqs.map((faq) => (
                  <button
                    key={faq.id}
                    type="button"
                    onClick={() => selectFaq(faq)}
                    disabled={loading}
                    className="cursor-pointer rounded-xl border border-line bg-white px-3 py-2 text-left text-[13px] font-bold leading-[1.45] text-[#52604f] hover:border-brand hover:bg-brand-soft disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    {faq.question}
                  </button>
                ))}
              </div>

              {messages.length > 0 && (
                <div
                  role="log"
                  aria-live="polite"
                  aria-label="식물 도우미 대화"
                  className="mt-4 max-h-[480px] space-y-3 overflow-y-auto rounded-2xl bg-[#f1f5eb] p-3"
                >
                  {messages.map((message) =>
                    message.role === "USER" ? (
                      <div key={message.id} className="flex justify-end">
                        <div className="max-w-[88%] rounded-2xl rounded-tr-sm bg-brand px-3.5 py-2.5 text-[14px] leading-[1.6] text-white">
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
                </div>
              )}

              <div className="mt-4 border-t border-[#dce8ce] pt-3.5">
                <label
                  htmlFor="plant-journal-chat-question"
                  className="text-[13px] font-extrabold text-[#52604f]"
                >
                  내 식물 기록을 바탕으로 직접 질문하기
                </label>
                <div className="mt-2 flex items-end gap-2">
                  <textarea
                    id="plant-journal-chat-question"
                    value={question}
                    onChange={(event) => setQuestion(event.target.value)}
                    onKeyDown={handleQuestionKeyDown}
                    maxLength={MAX_QUESTION_LENGTH}
                    rows={2}
                    disabled={loading}
                    placeholder={`${plant.nickname}의 잎이나 물주기처럼 궁금한 점을 적어보세요.`}
                    className="min-h-[72px] flex-1 resize-y rounded-xl border-[1.5px] border-line bg-white px-3 py-2.5 text-[14px] leading-[1.5] outline-none focus:border-brand disabled:bg-[#f3f4ef]"
                  />
                  <button
                    type="button"
                    onClick={() => void sendQuestion()}
                    disabled={loading || !accessToken || !question.trim()}
                    className="h-[44px] flex-none cursor-pointer rounded-xl bg-brand px-4 text-sm font-extrabold text-white disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {loading ? "답변 중" : "AI에게 묻기"}
                  </button>
                </div>
                <div className="mt-1.5 flex items-center justify-between gap-3 text-[11px] text-faint">
                  <span>Enter로 전송 · Shift+Enter로 줄바꿈</span>
                  <span>
                    {question.length} / {MAX_QUESTION_LENGTH}
                  </span>
                </div>
              </div>

              {error && (
                <div
                  role="alert"
                  className="mt-3 rounded-xl bg-danger-soft px-3.5 py-3 text-[13px] font-semibold text-danger"
                >
                  {error}
                </div>
              )}

              <p className="mt-3 text-[11px] leading-[1.55] text-faint">
                {AI_DISCLAIMER}
              </p>

              {hasAnswer && (
                <div className="mt-3 flex flex-wrap items-center justify-between gap-2 rounded-xl bg-white px-3.5 py-3 text-[12.5px] text-sub">
                  <span>그래도 궁금한 점이 해결되지 않았나요?</span>
                  <Link
                    href="/my/inquiries"
                    className="font-extrabold text-brand-dark"
                  >
                    1:1 문의하기 →
                  </Link>
                </div>
              )}
            </>
          )}
        </div>
      )}
    </section>
  );
}
