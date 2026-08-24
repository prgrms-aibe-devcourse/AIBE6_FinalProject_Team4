"use client";

import { ApiError } from "@/lib/api";
import {
  AdminGachaDraw,
  AdminGachaDrawStatus,
  getAdminGachaDraws,
  retryAdminGachaDraw,
} from "@/lib/admin-gacha-api";
import { useUI } from "@/lib/ui";
import { useCallback, useEffect, useRef, useState } from "react";
import AdminPagination from "./AdminPagination";
import { useScrollOnPageLoad } from "./use-scroll-on-page-load";

const PAGE_SIZE = 10;
const GRID_COLS = "grid-cols-[.55fr_1fr_1fr_1fr_.8fr_1.2fr_.7fr]";

const STATUS_LABEL: Record<AdminGachaDrawStatus, string> = {
  PENDING: "처리 대기",
  PROCESSING: "처리 중",
  COMPLETED: "완료",
  RETRYABLE_FAILED: "자동 재시도 대기",
  MANUAL_REVIEW: "수동 확인 필요",
  REFUNDED: "환불 완료",
};

const STATUS_STYLE: Record<AdminGachaDrawStatus, string> = {
  PENDING: "bg-[#fff3cc] text-[#8a6d00]",
  PROCESSING: "bg-[#e3f0fa] text-[#3a76a8]",
  COMPLETED: "bg-brand-soft text-brand-dark",
  RETRYABLE_FAILED: "bg-[#fff0e5] text-[#b35d20]",
  MANUAL_REVIEW: "bg-[#ffe6e6] text-danger",
  REFUNDED: "bg-[#eeeeee] text-sub",
};

function formatDate(value: string | null) {
  return value ? new Date(value).toLocaleString("ko-KR") : "-";
}

export default function AdminGachaOperationsPanel({
  accessToken,
}: {
  accessToken: string;
}) {
  const { showToast } = useUI();
  const sectionRef = useRef<HTMLElement>(null);
  const [draws, setDraws] = useState<AdminGachaDraw[]>([]);
  const [status, setStatus] = useState<AdminGachaDrawStatus | "">("");
  const [userIdInput, setUserIdInput] = useState("");
  const [appliedUserId, setAppliedUserId] = useState<number | undefined>();
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [error, setError] = useState("");

  useScrollOnPageLoad(page, loading, sectionRef);

  const load = useCallback(
    (signal?: AbortSignal) => {
      setLoading(true);
      setError("");
      return getAdminGachaDraws(accessToken, {
        status: status || undefined,
        userId: appliedUserId,
        page,
        size: PAGE_SIZE,
        signal,
      })
        .then((response) => {
          setDraws(response.content);
          setTotalPages(response.totalPages);
          setTotalElements(response.totalElements);
        })
        .catch((requestError) => {
          if (
            requestError instanceof DOMException &&
            requestError.name === "AbortError"
          )
            return;
          setError(
            requestError instanceof ApiError
              ? requestError.message
              : "가챠 처리 내역을 불러오지 못했어요.",
          );
        })
        .finally(() => {
          if (!signal?.aborted) setLoading(false);
        });
    },
    [accessToken, appliedUserId, page, status],
  );

  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, [load]);

  const applyUserFilter = () => {
    if (!userIdInput.trim()) {
      setAppliedUserId(undefined);
      setPage(0);
      return;
    }
    const next = Number(userIdInput);
    if (!Number.isInteger(next) || next < 1) {
      showToast("사용자 ID는 1 이상의 정수여야 합니다.", "err");
      return;
    }
    setAppliedUserId(next);
    setPage(0);
  };

  const retry = async (draw: AdminGachaDraw) => {
    if (busyId !== null || draw.status !== "MANUAL_REVIEW") return;
    setBusyId(draw.drawId);
    try {
      await retryAdminGachaDraw(draw.drawId, accessToken);
      showToast(`가챠 #${draw.drawId}을 다시 처리 대기 상태로 전환했어요.`);
      await load();
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError
          ? requestError.message
          : "재시도 요청에 실패했어요.",
        "err",
      );
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div className="flex flex-col gap-5">
      <section className="rounded-[18px] border border-line bg-white p-5 shadow-sm">
        <div className="mb-1 text-sm font-extrabold">가챠 장애 운영</div>
        <p className="mb-4 text-xs text-sub">
          실패 상태와 재시도 횟수를 확인하고 수동 확인 건만 다시 처리할 수
          있습니다.
        </p>
        <div className="flex flex-wrap gap-2">
          <select
            value={status}
            onChange={(event) => {
              setStatus(event.target.value as AdminGachaDrawStatus | "");
              setPage(0);
            }}
            className="rounded-xl border border-line px-3 py-2.5 text-sm"
          >
            <option value="">전체 상태</option>
            {Object.entries(STATUS_LABEL).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
          <input
            type="number"
            min={1}
            value={userIdInput}
            onChange={(event) => setUserIdInput(event.target.value)}
            onKeyDown={(event) => event.key === "Enter" && applyUserFilter()}
            placeholder="사용자 ID"
            className="w-40 rounded-xl border border-line px-3 py-2.5 text-sm"
          />
          <button
            type="button"
            onClick={applyUserFilter}
            className="rounded-xl bg-brand-soft px-4 py-2.5 text-sm font-bold text-brand-dark"
          >
            조회
          </button>
          <button
            type="button"
            onClick={() => void load()}
            className="rounded-xl border border-line px-4 py-2.5 text-sm font-bold"
          >
            새로고침
          </button>
          <span className="ml-auto self-center text-xs font-bold text-sub">
            총 {totalElements.toLocaleString()}건
          </span>
        </div>
      </section>

      <section ref={sectionRef} className="overflow-x-auto rounded-[18px] border border-line bg-white shadow-sm">
        <div className="min-w-[980px]">
          <div className={`grid gap-3 border-b border-line bg-[#fafbf7] px-4 py-3 text-xs font-extrabold text-sub ${GRID_COLS}`}>
            <div>ID</div>
            <div>사용자</div>
            <div>출처</div>
            <div>상태</div>
            <div>시도</div>
            <div>처리 정보</div>
            <div>관리</div>
          </div>
          {loading && draws.length === 0 ? (
            <div className="p-12 text-center text-sub">
              조건에 맞는 처리 내역을 불러오고 있어요.
            </div>
          ) : error ? (
            <div className="p-12 text-center text-danger">{error}</div>
          ) : draws.length === 0 ? (
            <div className="p-12 text-center text-sub">
              조건에 맞는 처리 내역이 없어요.
            </div>
          ) : (
            draws.map((draw) => (
              <div
                key={draw.drawId}
                className="grid grid-cols-[.55fr_1fr_1fr_1fr_.8fr_1.2fr_.7fr] items-center gap-3 border-b border-line px-4 py-3 text-xs last:border-b-0"
              >
                <b>#{draw.drawId}</b>
                <div>
                  <b>{draw.userNickname}</b>
                  <div className="text-sub">ID {draw.userId}</div>
                </div>
                <div>
                  <b>{draw.sourceType}</b>
                  <div className="text-sub">#{draw.sourceId}</div>
                </div>
                <div>
                  <span
                    className={`inline-block rounded-full px-2 py-1 font-bold ${STATUS_STYLE[draw.status]}`}
                  >
                    {STATUS_LABEL[draw.status]}
                  </span>
                </div>
                <div>
                  {draw.attemptCount}회
                  <div className="text-sub">카드 {draw.drawCount}장</div>
                </div>
                <div>
                  <div className="font-semibold text-danger">
                    {draw.lastErrorCode ?? "오류 없음"}
                  </div>
                  <div className="text-sub">
                    생성 {formatDate(draw.createdAt)}
                  </div>
                  <div className="text-sub">
                    다음 {formatDate(draw.nextRetryAt)}
                  </div>
                </div>
                <button
                  type="button"
                  disabled={busyId !== null || draw.status !== "MANUAL_REVIEW"}
                  onClick={() => void retry(draw)}
                  className="rounded-lg bg-brand px-2.5 py-2 font-bold text-white disabled:bg-line disabled:text-sub"
                >
                  재시도
                </button>
              </div>
            ))
          )}
        </div>
        <div className="border-t border-line px-4 pt-4 pb-6">
          <AdminPagination page={page} totalPages={totalPages} onChange={setPage} />
        </div>
      </section>
    </div>
  );
}
