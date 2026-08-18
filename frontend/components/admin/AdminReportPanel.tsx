"use client";

import { ApiError } from "@/lib/api";
import {
  completeReport,
  getReportsForAdmin,
  rejectReport,
  ReportData,
  ReportStatus,
  ReportTargetType,
} from "@/lib/report-api";
import { useUI } from "@/lib/ui";
import { useCallback, useState } from "react";
import { useAdminPaginatedList } from "./use-admin-paginated-list";

const TARGET: Record<ReportTargetType, string> = {
  JOURNAL: "일지",
  USER: "사용자",
  POST: "게시글",
  COMMENT: "댓글",
};
const STAT: Record<ReportStatus, [string, string]> = {
  PENDING: ["검토 대기", "bg-[#FBEDE3] text-[#b5771a]"],
  COMPLETED: ["처리 완료", "bg-[#E8F3D8] text-brand-text"],
  REJECTED: ["반려됨", "bg-[#f0f1ea] text-[#8a8a8a]"],
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

export default function AdminReportPanel({
  accessToken,
}: {
  accessToken: string;
}) {
  const { showToast } = useUI();
  const [status, setStatus] = useState<ReportStatus | "">("");
  const [selected, setSelected] = useState<ReportData | null>(null);
  const [actionType, setActionType] = useState("");
  const [actionDetail, setActionDetail] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const fetchPage = useCallback(
    (page: number, signal?: AbortSignal) =>
      getReportsForAdmin(accessToken, status || undefined, page, 20, signal),
    [accessToken, status],
  );
  const {
    page,
    setPage,
    items: reports,
    totalPages,
    totalElements,
    loading,
    errorMessage,
    reload,
  } = useAdminPaginatedList(fetchPage, "신고 목록을 불러오지 못했어요.");

  const changeStatus = (next: ReportStatus | "") => {
    setStatus(next);
    setPage(0);
  };

  const openReport = (report: ReportData) => {
    setSelected(report);
    setActionType("");
    setActionDetail("");
  };

  const submitAction = async (kind: "complete" | "reject") => {
    if (!selected || submitting) return;
    if (!actionType.trim()) return showToast("조치 유형을 입력해 주세요.", "err");
    if (!actionDetail.trim()) return showToast("조치 내용을 입력해 주세요.", "err");

    setSubmitting(true);
    try {
      const payload = { actionType: actionType.trim(), actionDetail: actionDetail.trim() };
      if (kind === "complete") {
        await completeReport(selected.id, payload, accessToken);
      } else {
        await rejectReport(selected.id, payload, accessToken);
      }
      showToast(kind === "complete" ? "신고를 완료 처리했어요." : "신고를 반려했어요.");
      setSelected(null);
      reload();
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError
          ? requestError.message
          : "처리에 실패했어요.",
        "err",
      );
      // 다른 관리자가 먼저 처리해 백엔드가 409로 거부한 경우, 이 건은 이미 처리된 상태다 —
      // 목록을 새로고침하지 않으면 모달이 계속 처리 대기 상태로 남아 재시도를 유도하게 된다.
      if (requestError instanceof ApiError && requestError.status === 409) {
        setSelected(null);
        reload();
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <section className="overflow-hidden rounded-[18px] bg-white shadow-card">
      <div className="border-b border-line p-5">
        <h2 className="text-lg font-extrabold">신고 관리</h2>
        <p className="mt-1 text-sm text-sub">
          회원이 접수한 신고를 확인하고 조치합니다.
        </p>

        <div className="mt-4 flex gap-2">
          {(
            [
              ["", "전체"],
              ["PENDING", "검토 대기"],
              ["COMPLETED", "처리 완료"],
              ["REJECTED", "반려됨"],
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
              <th className="px-5 py-3.5">대상</th>
              <th className="px-4 py-3.5">사유</th>
              <th className="px-4 py-3.5">신고자</th>
              <th className="px-4 py-3.5">상태</th>
              <th className="px-4 py-3.5">신고일</th>
              <th className="px-5 py-3.5 text-right">관리</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={6} className="px-5 py-10 text-center text-sub">
                  신고 목록을 불러오고 있어요.
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
            ) : reports.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-5 py-10 text-center text-sub">
                  조건에 맞는 신고가 없어요.
                </td>
              </tr>
            ) : (
              reports.map((report) => {
                const st = STAT[report.status];
                return (
                  <tr key={report.id} className="border-t border-[#f2f3ec]">
                    <td className="px-5 py-3.5 font-bold">
                      {TARGET[report.targetType]} #{report.targetId}
                    </td>
                    <td className="max-w-[220px] truncate px-4 py-3.5 text-sub">
                      {report.reason}
                    </td>
                    <td className="px-4 py-3.5 text-sub">{report.reporterName}</td>
                    <td className="px-4 py-3.5">
                      <span
                        className={`rounded-full px-2.5 py-1 text-xs font-extrabold ${st[1]}`}
                      >
                        {st[0]}
                      </span>
                    </td>
                    <td className="whitespace-nowrap px-4 py-3.5 text-sub">
                      {formatDateTime(report.createdAt)}
                    </td>
                    <td className="px-5 py-3.5 text-right">
                      <button
                        type="button"
                        onClick={() => openReport(report)}
                        className="rounded-lg border border-line px-2.5 py-1.5 text-xs font-bold"
                      >
                        {report.status === "PENDING" ? "처리하기" : "상세 보기"}
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
            <h3 className="mb-1 text-[19px] font-extrabold">
              {TARGET[selected.targetType]} #{selected.targetId} 신고
            </h3>
            <p className="mb-4 whitespace-pre-wrap text-[13.5px] leading-[1.6] text-sub">
              사유: {selected.reason} · 신고자 {selected.reporterName} ·{" "}
              {formatDateTime(selected.createdAt)}
            </p>

            {selected.status !== "PENDING" ? (
              <>
                <div className="mb-4 rounded-2xl border-[1.5px] border-[#dcebc7] bg-[#F6F9EF] p-4">
                  <div className="mb-1.5 text-xs font-bold text-sub">
                    {selected.processedAdminName ?? "관리자"}
                    {selected.processedAt
                      ? ` · ${formatDateTime(selected.processedAt)}`
                      : ""}
                  </div>
                  <p className="text-sm font-bold">{selected.actionType}</p>
                  <p className="mt-1 whitespace-pre-wrap text-sm leading-[1.6]">
                    {selected.actionDetail}
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
                <input
                  value={actionType}
                  onChange={(e) => setActionType(e.target.value)}
                  maxLength={50}
                  placeholder="조치 유형 (예: 콘텐츠 숨김, 경고)"
                  className="mb-2.5 w-full rounded-xl border-[1.5px] border-line p-3.5 text-sm outline-none"
                />
                <textarea
                  value={actionDetail}
                  onChange={(e) => setActionDetail(e.target.value)}
                  maxLength={500}
                  placeholder="조치 내용을 입력해 주세요."
                  className="mb-4 min-h-[120px] w-full resize-y rounded-xl border-[1.5px] border-line p-3.5 text-sm leading-[1.6] outline-none"
                />
                <div className="flex gap-2.5">
                  <button
                    type="button"
                    onClick={() => void submitAction("complete")}
                    disabled={submitting}
                    className="flex-1 rounded-xl bg-danger px-4 py-[13px] font-extrabold text-white disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    {submitting ? "처리 중..." : "완료 처리"}
                  </button>
                  <button
                    type="button"
                    onClick={() => void submitAction("reject")}
                    disabled={submitting}
                    className="flex-1 rounded-xl bg-brand-soft px-4 py-[13px] font-extrabold text-brand-dark disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    {submitting ? "처리 중..." : "반려"}
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
