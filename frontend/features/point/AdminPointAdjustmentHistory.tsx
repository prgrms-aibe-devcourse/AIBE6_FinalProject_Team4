"use client";

import { useEffect, useRef, useState } from "react";
import {
  AdminPointAdjustmentDirection,
  AdminPointAdjustmentHistoryData,
  getAdminPointAdjustments,
  PointCurrencyType,
} from "@/features/point/api";
import { ApiError } from "@/lib/api";
import { getAdminPointAdjustmentReasonLabel } from "@/features/point/admin-adjustment-reasons";
import AdminPagination from "@/components/admin/AdminPagination";
import { useScrollOnPageLoad } from "@/components/admin/use-scroll-on-page-load";

const PAGE_SIZE = 10;

interface AdminPointAdjustmentHistoryProps {
  accessToken: string | null;
  selectedUserId?: number;
  refreshKey: number;
}

interface HistoryPage {
  content: AdminPointAdjustmentHistoryData[];
  number: number;
  totalElements: number;
  totalPages: number;
}

function formatPoint(value: number): string {
  return value.toLocaleString("ko-KR");
}

function toNextDayStart(date: string): string | undefined {
  if (!date) return undefined;
  const [year, month, day] = date.split("-").map(Number);
  const nextDay = new Date(Date.UTC(year, month - 1, day + 1));
  return `${nextDay.toISOString().slice(0, 10)}T00:00:00`;
}

export default function AdminPointAdjustmentHistory({
  accessToken,
  selectedUserId,
  refreshKey,
}: AdminPointAdjustmentHistoryProps) {
  const sectionRef = useRef<HTMLElement>(null);
  const [currencyType, setCurrencyType] = useState<PointCurrencyType | "">("");
  const [direction, setDirection] = useState<
    AdminPointAdjustmentDirection | ""
  >("");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [onlySelectedUser, setOnlySelectedUser] = useState(false);
  const [page, setPage] = useState(0);
  const [historyPage, setHistoryPage] = useState<HistoryPage | null>(null);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    if (!selectedUserId) setOnlySelectedUser(false);
  }, [selectedUserId]);

  useEffect(() => {
    if (!accessToken) return;
    const controller = new AbortController();
    setLoading(true);
    setErrorMessage("");

    getAdminPointAdjustments({
      accessToken,
      userId: onlySelectedUser ? selectedUserId : undefined,
      currencyType: currencyType || undefined,
      direction: direction || undefined,
      from: fromDate ? `${fromDate}T00:00:00` : undefined,
      to: toNextDayStart(toDate),
      page,
      size: PAGE_SIZE,
      signal: controller.signal,
    })
      .then(setHistoryPage)
      .catch((requestError) => {
        if (
          requestError instanceof DOMException &&
          requestError.name === "AbortError"
        )
          return;
        setHistoryPage(null);
        setErrorMessage(
          requestError instanceof ApiError
            ? requestError.message
            : "관리자 포인트 조정 내역을 불러오지 못했어요.",
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [
    accessToken,
    currencyType,
    direction,
    fromDate,
    onlySelectedUser,
    page,
    refreshKey,
    selectedUserId,
    toDate,
  ]);

  const changeFilter = (change: () => void) => {
    change();
    setPage(0);
  };
  const history = historyPage?.content ?? [];
  const totalPages = historyPage?.totalPages ?? 0;

  useScrollOnPageLoad(page, loading, sectionRef);

  return (
    <section ref={sectionRef} className="overflow-hidden rounded-[18px] bg-white shadow-card">
      <div className="border-b border-line p-5">
        <h2 className="text-lg font-extrabold">관리자 포인트 조정 내역</h2>
        <p className="mt-1 text-sm text-sub">
          관리자가 지급·차감한 모든 원장을 최신순으로 확인합니다.
        </p>

        <div className="mt-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-[140px_140px_1fr_1fr_auto]">
          <label className="text-xs font-bold text-sub">
            포인트 종류
            <select
              aria-label="내역 포인트 종류"
              value={currencyType}
              onChange={(event) =>
                changeFilter(() =>
                  setCurrencyType(event.target.value as PointCurrencyType | ""),
                )
              }
              className="mt-1.5 w-full rounded-xl border border-line bg-white px-3 py-2.5 text-sm text-ink outline-none focus:border-brand"
            >
              <option value="">전체</option>
              <option value="FREE">무상</option>
              <option value="PAID">유상</option>
            </select>
          </label>
          <label className="text-xs font-bold text-sub">
            조정 방식
            <select
              aria-label="내역 조정 방식"
              value={direction}
              onChange={(event) =>
                changeFilter(() =>
                  setDirection(
                    event.target.value as AdminPointAdjustmentDirection | "",
                  ),
                )
              }
              className="mt-1.5 w-full rounded-xl border border-line bg-white px-3 py-2.5 text-sm text-ink outline-none focus:border-brand"
            >
              <option value="">전체</option>
              <option value="GRANT">지급</option>
              <option value="DEDUCT">차감</option>
            </select>
          </label>
          <label className="text-xs font-bold text-sub">
            시작일
            <input
              aria-label="내역 시작일"
              type="date"
              value={fromDate}
              onChange={(event) =>
                changeFilter(() => setFromDate(event.target.value))
              }
              className="mt-1.5 w-full rounded-xl border border-line bg-white px-3 py-2.5 text-sm text-ink outline-none focus:border-brand"
            />
          </label>
          <label className="text-xs font-bold text-sub">
            종료일
            <input
              aria-label="내역 종료일"
              type="date"
              value={toDate}
              onChange={(event) =>
                changeFilter(() => setToDate(event.target.value))
              }
              className="mt-1.5 w-full rounded-xl border border-line bg-white px-3 py-2.5 text-sm text-ink outline-none focus:border-brand"
            />
          </label>
          <label
            className={`flex items-end gap-2 pb-2.5 text-sm font-bold ${selectedUserId ? "cursor-pointer text-ink" : "cursor-not-allowed text-sub"}`}
          >
            <input
              type="checkbox"
              checked={onlySelectedUser}
              disabled={!selectedUserId}
              onChange={(event) =>
                changeFilter(() => setOnlySelectedUser(event.target.checked))
              }
              className="h-4 w-4 accent-[#6d9f3b]"
            />
            선택 회원만
          </label>
        </div>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full min-w-[900px] text-left text-sm">
          <thead className="bg-[#f6f7f1] text-xs font-extrabold text-sub">
            <tr>
              <th className="px-5 py-3.5">처리 시각</th>
              <th className="px-4 py-3.5">대상 회원</th>
              <th className="px-4 py-3.5">구분</th>
              <th className="px-4 py-3.5">조정 사유</th>
              <th className="px-4 py-3.5 text-right">조정 금액</th>
              <th className="px-4 py-3.5 text-right">조정 후 잔액</th>
              <th className="px-4 py-3.5">관리자</th>
              <th className="px-5 py-3.5 text-right">원장</th>
            </tr>
          </thead>
          <tbody>
            {loading && history.length === 0 ? (
              <tr>
                <td colSpan={8} className="px-5 py-10 text-center text-sub">
                  조건에 맞는 조정 내역을 불러오고 있어요.
                </td>
              </tr>
            ) : errorMessage ? (
              <tr>
                <td
                  colSpan={8}
                  role="alert"
                  className="px-5 py-10 text-center text-danger"
                >
                  {errorMessage}
                </td>
              </tr>
            ) : history.length === 0 ? (
              <tr>
                <td colSpan={8} className="px-5 py-10 text-center text-sub">
                  조건에 맞는 조정 내역이 없습니다.
                </td>
              </tr>
            ) : (
              history.map((item) => (
                <tr
                  key={item.transactionId}
                  className="border-t border-[#f2f3ec]"
                >
                  <td className="whitespace-nowrap px-5 py-3.5 text-sub">
                    {new Date(item.createdAt).toLocaleString("ko-KR")}
                  </td>
                  <td className="px-4 py-3.5">
                    <div className="font-bold">
                      {item.targetNickname}{" "}
                      <span className="text-xs text-sub">
                        #{item.targetUserId}
                      </span>
                    </div>
                    <div className="mt-0.5 text-xs text-sub">
                      {item.targetEmail}
                    </div>
                  </td>
                  <td className="px-4 py-3.5">
                    <span className="rounded-full bg-[#f0f1ea] px-2.5 py-1 text-xs font-extrabold text-sub">
                      {item.currencyType === "FREE" ? "무상" : "유상"}
                    </span>
                  </td>
                  <td className="px-4 py-3.5 font-bold">
                    {getAdminPointAdjustmentReasonLabel(item.adjustmentReason)}
                  </td>
                  <td
                    className={`px-4 py-3.5 text-right font-extrabold ${item.amount < 0 ? "text-danger" : "text-brand-text"}`}
                  >
                    {item.amount > 0 ? "+" : ""}
                    {formatPoint(item.amount)}P
                  </td>
                  <td className="px-4 py-3.5 text-right font-bold">
                    {formatPoint(item.balanceAfter)}P
                  </td>
                  <td className="px-4 py-3.5 text-sub">
                    {item.adminUserId ? `#${item.adminUserId}` : "기록 없음"}
                  </td>
                  <td className="px-5 py-3.5 text-right text-xs text-sub">
                    #{item.transactionId}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="flex flex-col items-center gap-3 border-t border-line px-5 pt-3.5 pb-5 text-sm sm:flex-row sm:justify-between">
        <span className="text-sub">총 {historyPage?.totalElements ?? 0}건</span>
        <AdminPagination page={page} totalPages={totalPages} onChange={setPage} />
      </div>
    </section>
  );
}
