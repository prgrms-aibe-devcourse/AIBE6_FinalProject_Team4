"use client";

import { ApiError } from "@/lib/api";
import {
  answerInquiry,
  getInquiriesForAdmin,
  InquiryCategory,
  InquiryData,
  InquiryStatus,
} from "@/lib/inquiry-api";
import { useUI } from "@/lib/ui";
import { useCallback, useEffect, useRef, useState } from "react";

const CAT: Record<InquiryCategory, string> = {
  PAYMENT: "결제",
  DELIVERY: "배송",
  ACCOUNT: "계정",
  ETC: "기타",
};
const STAT: Record<InquiryStatus, [string, string]> = {
  OPEN: ["대기", "bg-[#f0f1ea] text-[#8a8a8a]"],
  ANSWERED: ["답변완료", "bg-[#E8F3D8] text-brand-text"],
};

function formatDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(date);
}

export default function AdminInquiryPanel({
  accessToken,
}: {
  accessToken: string;
}) {
  const { showToast } = useUI();
  const [status, setStatus] = useState<InquiryStatus | "">("");
  const [page, setPage] = useState(0);
  const [inquiries, setInquiries] = useState<InquiryData[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");
  const [selected, setSelected] = useState<InquiryData | null>(null);
  const [answerContent, setAnswerContent] = useState("");
  const [submitting, setSubmitting] = useState(false);

  // load()는 useEffect(page 변경 시 자동 취소)뿐 아니라 submitAnswer 성공 후에도 signal 없이
  // 직접 호출된다 — 그 사이 사용자가 페이지를 옮기면 먼저 보낸 요청이 나중에 응답할 수 있다.
  // requestId로 "가장 최근에 보낸 요청의 응답인지"를 확인해, 뒤처진 응답이 최신 state를 덮어쓰거나
  // (예: 이미 정상인 다른 페이지에서) totalPages 보정을 잘못 트리거하지 않도록 막는다.
  const requestIdRef = useRef(0);

  const load = useCallback(
    (signal?: AbortSignal) => {
      const requestId = ++requestIdRef.current;
      setLoading(true);
      setErrorMessage("");

      return getInquiriesForAdmin(accessToken, status || undefined, page, 20, signal)
        .then((result) => {
          if (requestIdRef.current !== requestId) return;
          // 답변 등록으로 필터 조건을 벗어난 항목이 빠지면서 마지막 페이지가 사라질 수 있다 —
          // 그 경우 빈 결과를 그대로 보여주는 대신 이전 페이지로 물러나 다시 불러온다.
          if (result.content.length === 0 && page > 0 && result.totalElements > 0) {
            setPage((current) => Math.max(0, current - 1));
            return;
          }
          setInquiries(result.content);
          setTotalPages(result.totalPages);
          setTotalElements(result.totalElements);
        })
        .catch((requestError) => {
          if (
            requestError instanceof DOMException &&
            requestError.name === "AbortError"
          )
            return;
          if (requestIdRef.current !== requestId) return;
          setInquiries([]);
          setErrorMessage(
            requestError instanceof ApiError
              ? requestError.message
              : "문의 목록을 불러오지 못했어요.",
          );
        })
        .finally(() => {
          if (requestIdRef.current === requestId) setLoading(false);
        });
    },
    [accessToken, page, status],
  );

  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, [load]);

  const changeStatus = (next: InquiryStatus | "") => {
    setStatus(next);
    setPage(0);
  };

  const openInquiry = (inquiry: InquiryData) => {
    setSelected(inquiry);
    setAnswerContent("");
  };

  const submitAnswer = async () => {
    if (!selected || submitting) return;
    if (!answerContent.trim())
      return showToast("답변 내용을 입력해 주세요.", "err");

    setSubmitting(true);
    try {
      await answerInquiry(selected.id, answerContent.trim(), accessToken);
      showToast("답변을 등록했어요.");
      setSelected(null);
      void load();
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError
          ? requestError.message
          : "답변 등록에 실패했어요.",
        "err",
      );
      // 다른 관리자가 먼저 답변해 백엔드가 409로 거부한 경우, 이 행은 이미 답변완료 상태다 —
      // 목록을 새로고침하지 않으면 버튼이 계속 "답변하기"로 남아 재시도를 유도하게 된다.
      if (requestError instanceof ApiError && requestError.status === 409) {
        setSelected(null);
        void load();
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <section className="overflow-hidden rounded-[18px] bg-white shadow-card">
      <div className="border-b border-line p-5">
        <h2 className="text-lg font-extrabold">문의 관리</h2>
        <p className="mt-1 text-sm text-sub">
          회원이 남긴 1:1 문의를 확인하고 답변합니다.
        </p>

        <div className="mt-4 flex gap-2">
          {(
            [
              ["", "전체"],
              ["OPEN", "대기"],
              ["ANSWERED", "답변완료"],
            ] as const
          ).map(([value, label]) => (
            <button
              key={value}
              type="button"
              onClick={() => changeStatus(value)}
              className={`rounded-lg border px-3 py-1.5 text-xs font-bold ${
                status === value
                  ? "border-brand bg-brand-soft text-brand-dark"
                  : "border-line text-sub"
              }`}
            >
              {label}
            </button>
          ))}
        </div>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full min-w-[760px] text-left text-sm">
          <thead className="bg-[#f6f7f1] text-xs font-extrabold text-sub">
            <tr>
              <th className="px-5 py-3.5">제목</th>
              <th className="px-4 py-3.5">작성자</th>
              <th className="px-4 py-3.5">유형</th>
              <th className="px-4 py-3.5">상태</th>
              <th className="px-4 py-3.5">작성일</th>
              <th className="px-5 py-3.5 text-right">관리</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={6} className="px-5 py-10 text-center text-sub">
                  문의 목록을 불러오고 있어요.
                </td>
              </tr>
            ) : errorMessage ? (
              <tr>
                <td
                  colSpan={6}
                  role="alert"
                  className="px-5 py-10 text-center text-danger"
                >
                  {errorMessage}
                </td>
              </tr>
            ) : inquiries.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-5 py-10 text-center text-sub">
                  조건에 맞는 문의가 없어요.
                </td>
              </tr>
            ) : (
              inquiries.map((inquiry) => {
                const st = STAT[inquiry.status];
                return (
                  <tr key={inquiry.id} className="border-t border-[#f2f3ec]">
                    <td className="max-w-[260px] truncate px-5 py-3.5 font-bold">
                      {inquiry.title}
                    </td>
                    <td className="px-4 py-3.5 text-sub">{inquiry.userName}</td>
                    <td className="px-4 py-3.5 text-sub">
                      {CAT[inquiry.category]}
                    </td>
                    <td className="px-4 py-3.5">
                      <span
                        className={`rounded-full px-2.5 py-1 text-xs font-extrabold ${st[1]}`}
                      >
                        {st[0]}
                      </span>
                    </td>
                    <td className="whitespace-nowrap px-4 py-3.5 text-sub">
                      {formatDateTime(inquiry.createdAt)}
                    </td>
                    <td className="px-5 py-3.5 text-right">
                      <button
                        type="button"
                        onClick={() => openInquiry(inquiry)}
                        className="rounded-lg border border-line px-2.5 py-1.5 text-xs font-bold"
                      >
                        {inquiry.status === "ANSWERED" ? "답변 보기" : "답변하기"}
                      </button>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      <div className="flex items-center justify-between border-t border-line px-5 py-3.5 text-sm">
        <span className="text-sub">총 {totalElements}건</span>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setPage((current) => Math.max(0, current - 1))}
            disabled={page === 0 || loading}
            className="rounded-lg border border-line px-3 py-1.5 font-bold text-sub disabled:cursor-not-allowed disabled:opacity-40"
          >
            이전
          </button>
          <span className="min-w-[64px] text-center font-bold">
            {totalPages === 0 ? 0 : page + 1} / {totalPages}
          </span>
          <button
            type="button"
            onClick={() => setPage((current) => current + 1)}
            disabled={loading || totalPages === 0 || page + 1 >= totalPages}
            className="rounded-lg border border-line px-3 py-1.5 font-bold text-sub disabled:cursor-not-allowed disabled:opacity-40"
          >
            다음
          </button>
        </div>
      </div>

      {selected && (
        <div
          onClick={() => !submitting && setSelected(null)}
          className="fixed inset-0 z-[60] flex items-center justify-center bg-[rgba(46,54,42,.4)] p-5"
        >
          <div
            onClick={(e) => e.stopPropagation()}
            className="w-full max-w-[480px] animate-pop rounded-[20px] bg-white p-6"
          >
            <h3 className="mb-1 text-[19px] font-extrabold">{selected.title}</h3>
            <p className="mb-4 whitespace-pre-wrap text-[13.5px] leading-[1.6] text-sub">
              {selected.content}
            </p>

            {selected.status === "ANSWERED" ? (
              <>
                <div className="mb-4 rounded-2xl border-[1.5px] border-[#dcebc7] bg-[#F6F9EF] p-4">
                  <div className="mb-1.5 text-xs font-bold text-sub">
                    {selected.answerAdminName ?? "관리자"}
                    {selected.answeredAt
                      ? ` · ${formatDateTime(selected.answeredAt)}`
                      : ""}
                  </div>
                  <p className="whitespace-pre-wrap text-sm leading-[1.6]">
                    {selected.answerContent}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setSelected(null)}
                  className="w-full rounded-xl border-[1.5px] border-line bg-white p-[13px] font-bold text-sub"
                >
                  닫기
                </button>
              </>
            ) : (
              <>
                <textarea
                  value={answerContent}
                  onChange={(e) => setAnswerContent(e.target.value)}
                  maxLength={1000}
                  placeholder="답변 내용을 입력해 주세요."
                  className="mb-4 min-h-[140px] w-full resize-y rounded-xl border-[1.5px] border-line p-3.5 text-sm leading-[1.6] outline-none"
                />
                <div className="flex gap-2.5">
                  <button
                    type="button"
                    onClick={() => void submitAnswer()}
                    disabled={submitting}
                    className="flex-1 rounded-xl bg-brand p-[13px] font-extrabold text-white disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    {submitting ? "등록 중..." : "답변 등록"}
                  </button>
                  <button
                    type="button"
                    onClick={() => setSelected(null)}
                    disabled={submitting}
                    className="rounded-xl border-[1.5px] border-line bg-white px-5 py-[13px] font-bold text-sub disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    닫기
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      )}
    </section>
  );
}
