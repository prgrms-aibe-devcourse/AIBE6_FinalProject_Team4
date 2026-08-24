"use client";

import { ApiError } from "@/lib/api";
import { BoardCommentData, getBoardCommentAsAdmin, hideBoardCommentAsAdmin, hideBoardPostAsAdmin } from "@/lib/board-api";
import { getJournalAsAdmin, PlantJournalData } from "@/lib/journal-api";
import Link from "next/link";
import {
  completeReport,
  getReportsForAdmin,
  rejectReport,
  ReportData,
  ReportStatus,
  ReportTargetType,
} from "@/lib/report-api";
import { useUI } from "@/lib/ui";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useAdminPaginatedList } from "./use-admin-paginated-list";
import AdminPagination from "./AdminPagination";
import { useScrollOnPageLoad } from "./use-scroll-on-page-load";

const PAGE_SIZE = 10;
const COLUMN_COUNT = 6;
const ADMIN_ALL_SIZE = 2000;

export interface AdminReportTargetUserFilter {
  id: number;
  label: string;
}

type TargetPreview =
  | { type: "comment"; data: BoardCommentData }
  | { type: "journal"; data: PlantJournalData };

const TARGET: Record<ReportTargetType, string> = {
  JOURNAL: "일지",
  USER: "사용자",
  POST: "게시글",
  COMMENT: "댓글",
};
// 관리자 숨김 API가 있는 대상만 "완료 처리"가 실제로 콘텐츠를 숨긴다. JOURNAL/USER는 대응하는
// 숨김 기능이 백엔드에 없어, 완료 처리해도 신고 조치 기록만 남고 콘텐츠는 그대로 노출된다.
const HIDEABLE_TARGETS: ReportTargetType[] = ["POST", "COMMENT"];

const ACTION_TYPE_OPTIONS = ["콘텐츠 숨김", "경고 조치", "계정 정지", "조치 없음", "기타"];

const REPORT_STATUS_PRIORITY: Record<ReportStatus, number> = {
  PENDING: 0,
  COMPLETED: 1,
  REJECTED: 2,
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
  targetUser = null,
  onClearTargetUser,
}: {
  accessToken: string;
  // 유저 관리 탭의 "누적 신고수"를 눌러 넘어온 경우, 그 유저를 겨냥한 신고만 필터링해 보여준다.
  targetUser?: AdminReportTargetUserFilter | null;
  onClearTargetUser?: () => void;
}) {
  const { showToast } = useUI();
  const [status, setStatus] = useState<ReportStatus | "">("");
  const [selected, setSelected] = useState<ReportData | null>(null);
  const [actionType, setActionType] = useState("");
  const [actionDetail, setActionDetail] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [preview, setPreview] = useState<TargetPreview | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewError, setPreviewError] = useState("");
  const [openingCommentId, setOpeningCommentId] = useState<number | null>(null);
  // openReport가 빠르게 여러 번 클릭되면(다른 행을 연달아 열면) 먼저 보낸 조회가 나중에
  // 응답할 수 있다 — requestId로 가장 최근에 연 신고의 응답인지 확인해 뒤처진 응답이
  // 엉뚱한 신고의 미리보기를 덮어쓰지 않도록 막는다.
  const previewRequestId = useRef(0);
  const sectionRef = useRef<HTMLElement>(null);

  // 댓글 신고는 목록 응답에 postId가 없어(어느 게시글의 댓글인지는 댓글 자체를 조회해야
  // 알 수 있다), 클릭 시점에 댓글을 조회해 postId를 얻은 뒤 해당 게시글로 이동시킨다.
  const openCommentBoard = async (commentId: number) => {
    if (openingCommentId !== null) return;
    setOpeningCommentId(commentId);
    try {
      const comment = await getBoardCommentAsAdmin(commentId, accessToken);
      window.open(`/board/${comment.postId}`, "_blank");
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError
          ? requestError.message
          : "댓글이 속한 게시글을 찾지 못했어요.",
        "err",
      );
    } finally {
      setOpeningCommentId(null);
    }
  };

  // 처리 완료/반려된 신고는 이미 끝난 건이라 최신순으로, 그 외(검토 대기 등)는 아직
  // 처리해야 할 오래된 신고가 먼저 보이도록 오래된순으로 조회한다.
  const reportSort =
    status === "COMPLETED" || status === "REJECTED" ? "createdAt,DESC" : "createdAt,ASC";

  // "전체" 조회는 상태 우선순위(검토대기→처리완료→반려됨)로 전체를 재정렬해야 하는데,
  // 서버는 필드 하나로만 정렬할 수 있어 페이지 단위로는 커스텀 순서를 만들 수 없다 —
  // 그래서 이때만 한 번에 다 받아와 아래에서 클라이언트가 직접 정렬·페이지네이션한다.
  const fetchPage = useCallback(
    (page: number, signal?: AbortSignal) =>
      getReportsForAdmin(
        accessToken,
        status || undefined,
        status ? page : 0,
        status ? PAGE_SIZE : ADMIN_ALL_SIZE,
        signal,
        targetUser?.id,
        reportSort,
      ),
    [accessToken, status, targetUser?.id, reportSort],
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

  useScrollOnPageLoad(page, loading, sectionRef);

  // "전체" 조회일 때만 검토대기→처리완료→반려됨 순서로 묶어 보여준다. 특정 상태로
  // 필터링했을 때는 모든 항목이 같은 상태라 순서를 다시 매길 필요가 없다.
  const sortedAllReports = useMemo(() => {
    if (status) return reports;
    return [...reports].sort(
      (a, b) => REPORT_STATUS_PRIORITY[a.status] - REPORT_STATUS_PRIORITY[b.status],
    );
  }, [reports, status]);
  const displayReports = status
    ? sortedAllReports
    : sortedAllReports.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);
  const displayTotalPages = status ? totalPages : Math.ceil(totalElements / PAGE_SIZE);

  // 유저 관리 탭에서 다른 유저의 누적 신고수를 눌러 targetUser가 바뀌면(패널이 이미 열려 있는
  // 상태여도) 1페이지부터 다시 보여준다.
  useEffect(() => {
    setPage(0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [targetUser?.id]);

  const changeStatus = (next: ReportStatus | "") => {
    setStatus(next);
    setPage(0);
  };

  const openReport = (report: ReportData) => {
    setSelected(report);
    setActionType(HIDEABLE_TARGETS.includes(report.targetType) ? ACTION_TYPE_OPTIONS[0] : ACTION_TYPE_OPTIONS[3]);
    setActionDetail("");
    setPreview(null);
    setPreviewError("");

    if (report.targetType !== "COMMENT" && report.targetType !== "JOURNAL") return;
    const requestId = ++previewRequestId.current;
    setPreviewLoading(true);
    const fetchPreview =
      report.targetType === "COMMENT"
        ? getBoardCommentAsAdmin(report.targetId, accessToken).then(
            (data): TargetPreview => ({ type: "comment", data }),
          )
        : getJournalAsAdmin(report.targetId, accessToken).then(
            (data): TargetPreview => ({ type: "journal", data }),
          );
    fetchPreview
      .then((result) => {
        if (previewRequestId.current !== requestId) return;
        setPreview(result);
      })
      .catch((requestError) => {
        if (previewRequestId.current !== requestId) return;
        setPreviewError(
          requestError instanceof ApiError ? requestError.message : "콘텐츠를 불러오지 못했어요.",
        );
      })
      .finally(() => {
        if (previewRequestId.current === requestId) setPreviewLoading(false);
      });
  };

  const submitAction = async (kind: "complete" | "reject") => {
    if (!selected || submitting) return;
    if (!actionType.trim()) return showToast("조치 유형을 선택해 주세요.", "err");

    setSubmitting(true);
    try {
      const payload = { actionType: actionType.trim(), actionDetail: actionDetail.trim() };
      if (kind === "complete") {
        // 실제 숨김이 먼저 성공해야 완료 기록을 남긴다 — 순서를 반대로 하면 완료 처리는
        // 됐는데 콘텐츠는 그대로 노출되는 "거짓 성공" 상태가 남을 수 있다.
        if (selected.targetType === "POST") {
          await hideBoardPostAsAdmin(selected.targetId, accessToken);
        } else if (selected.targetType === "COMMENT") {
          await hideBoardCommentAsAdmin(selected.targetId, accessToken);
        }
        await completeReport(selected.id, payload, accessToken);
      } else {
        await rejectReport(selected.id, payload, accessToken);
      }
      showToast(
        kind === "complete"
          ? HIDEABLE_TARGETS.includes(selected.targetType)
            ? "콘텐츠를 숨기고 신고를 완료 처리했어요."
            : "신고를 완료 처리했어요."
          : "신고를 반려했어요.",
      );
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
    <section ref={sectionRef} className="overflow-hidden rounded-[18px] bg-white shadow-card">
      <div className="border-b border-line p-5">
        <h2 className="text-lg font-extrabold">신고 관리</h2>
        <p className="mt-1 text-sm text-sub">
          회원이 접수한 신고를 확인하고 조치합니다.
        </p>

        {targetUser && (
          <div className="mt-3 flex items-center gap-2 rounded-lg bg-brand-soft px-3 py-2 text-xs font-bold text-brand-dark">
            <span className="material-symbols-outlined text-[16px]">filter_alt</span>
            {targetUser.label}님을 대상으로 한 신고만 보는 중
            <button
              type="button"
              onClick={onClearTargetUser}
              className="ml-auto cursor-pointer rounded-md border border-brand-dark/30 bg-white px-2 py-0.5 text-[11px] font-bold text-brand-dark"
            >
              필터 해제
            </button>
          </div>
        )}

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
            {loading && reports.length === 0 ? (
              <tr>
                <td colSpan={COLUMN_COUNT} className="px-5 py-10 text-center text-sub">
                  조건에 맞는 신고 내역을 불러오고 있어요.
                </td>
              </tr>
            ) : errorMessage ? (
              <tr>
                <td
                  colSpan={COLUMN_COUNT}
                  role="alert"
                  className="px-5 py-10 text-center text-danger"
                >
                  {errorMessage}
                </td>
              </tr>
            ) : reports.length === 0 ? (
              <tr>
                <td colSpan={COLUMN_COUNT} className="px-5 py-10 text-center text-sub">
                  조건에 맞는 신고가 없어요.
                </td>
              </tr>
            ) : (
              displayReports.map((report) => {
                const st = STAT[report.status];
                return (
                  <tr key={report.id} className="border-t border-[#f2f3ec]">
                    <td className="px-5 py-3.5 font-bold">
                      {report.targetType === "POST" ? (
                        <Link
                          href={`/board/${report.targetId}`}
                          target="_blank"
                          onClick={(e) => e.stopPropagation()}
                          className="text-brand hover:text-brand-dark hover:underline"
                        >
                          {TARGET[report.targetType]} #{report.targetId}
                        </Link>
                      ) : report.targetType === "COMMENT" ? (
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            void openCommentBoard(report.targetId);
                          }}
                          disabled={openingCommentId === report.targetId}
                          className="text-brand hover:text-brand-dark hover:underline disabled:cursor-wait disabled:opacity-60"
                        >
                          {openingCommentId === report.targetId
                            ? "게시글 찾는 중..."
                            : `${TARGET[report.targetType]} #${report.targetId}`}
                        </button>
                      ) : (
                        <>
                          {TARGET[report.targetType]} #{report.targetId}
                        </>
                      )}
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

      <div className="flex flex-col items-center gap-3 border-t border-line px-5 pt-3.5 pb-5 text-sm sm:flex-row sm:justify-between">
        <span className="text-sub">총 {totalElements}건</span>
        <AdminPagination page={page} totalPages={displayTotalPages} onChange={setPage} />
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
            <p className="mb-1 whitespace-pre-wrap text-[13.5px] leading-[1.6] text-sub">
              사유: {selected.reason} · 신고자 {selected.reporterName} ·{" "}
              {formatDateTime(selected.createdAt)}
            </p>
            {selected.targetType === "POST" ? (
              <Link
                href={`/board/${selected.targetId}`}
                target="_blank"
                className="mb-4 inline-block text-[13px] font-bold text-brand hover:text-brand-dark"
              >
                신고된 게시글 보기 →
              </Link>
            ) : previewLoading ? (
              <p className="mb-4 text-[12.5px] text-sub">콘텐츠를 불러오고 있어요...</p>
            ) : previewError ? (
              <p className="mb-4 text-[12.5px] text-danger">{previewError}</p>
            ) : preview?.type === "comment" ? (
              <div className="mb-4 rounded-xl bg-[#f6f7f1] p-3.5">
                <div className="mb-1 text-xs font-bold text-sub">
                  {preview.data.nickname ?? "(삭제된 댓글)"}
                </div>
                <p className="mb-2 whitespace-pre-wrap text-sm leading-[1.6]">
                  {preview.data.content ?? "(삭제된 댓글)"}
                </p>
                <Link
                  href={`/board/${preview.data.postId}`}
                  target="_blank"
                  className="text-[13px] font-bold text-brand hover:text-brand-dark"
                >
                  게시글 보기 →
                </Link>
              </div>
            ) : preview?.type === "journal" ? (
              <div className="mb-4 rounded-xl bg-[#f6f7f1] p-3.5">
                <div className="mb-1 text-xs font-bold text-sub">
                  {preview.data.plantProfileNickname}
                </div>
                <p className="whitespace-pre-wrap text-sm leading-[1.6]">{preview.data.content}</p>
              </div>
            ) : null}

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
                <p className="mb-3 rounded-lg bg-[#f6f7f1] px-3 py-2.5 text-[12.5px] leading-[1.5] text-sub">
                  {HIDEABLE_TARGETS.includes(selected.targetType)
                    ? "완료 처리 시 해당 게시글/댓글이 즉시 숨겨져요."
                    : "이 신고 유형은 자동 숨김을 지원하지 않아요 — 완료 처리해도 조치 기록만 남습니다."}
                </p>
                <select
                  value={actionType}
                  onChange={(e) => setActionType(e.target.value)}
                  className="mb-2.5 w-full rounded-xl border-[1.5px] border-line bg-white p-3.5 text-sm outline-none"
                >
                  {ACTION_TYPE_OPTIONS.map((option) => (
                    <option key={option} value={option}>
                      {option}
                    </option>
                  ))}
                </select>
                <textarea
                  value={actionDetail}
                  onChange={(e) => setActionDetail(e.target.value)}
                  maxLength={500}
                  placeholder="조치 내용을 입력해 주세요. (선택)"
                  className="mb-4 min-h-[120px] w-full resize-y rounded-xl border-[1.5px] border-line p-3.5 text-sm leading-[1.6] outline-none"
                />
                <div className="flex gap-2.5">
                  <button
                    type="button"
                    onClick={() => void submitAction("complete")}
                    disabled={submitting}
                    className="flex-1 rounded-xl bg-danger px-4 py-[13px] font-extrabold text-white disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    {submitting
                      ? "처리 중..."
                      : HIDEABLE_TARGETS.includes(selected.targetType)
                        ? "숨김 처리"
                        : "완료 처리"}
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
