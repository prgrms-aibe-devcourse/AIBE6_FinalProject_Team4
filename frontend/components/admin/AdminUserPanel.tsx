"use client";

import { useCallback, useEffect, useState } from "react";
import { ApiError, SpringPage } from "@/lib/api";
import {
  AdminUserStatus,
  AdminUserSummary,
  getAdminUsers,
  reactivateAdminUser,
  suspendAdminUser,
} from "@/features/admin/user-api";
import { useUI } from "@/lib/ui";

const STATUS_LABELS: Record<AdminUserStatus, string> = {
  ACTIVE: "활성",
  SUSPENDED: "정지",
  RESTRICTED: "제한",
  WITHDRAWN: "탈퇴",
};

const STATUS_STYLES: Record<AdminUserStatus, string> = {
  ACTIVE: "bg-[#E8F3D8] text-brand-text",
  SUSPENDED: "bg-[#FBEDE3] text-[#b5771a]",
  RESTRICTED: "bg-[#FFF3CC] text-gold-text",
  WITHDRAWN: "bg-[#f0f1ea] text-[#7a8176]",
};

function formatDate(value: string): string {
  return new Date(value).toLocaleDateString("ko-KR");
}

export default function AdminUserPanel({
  accessToken,
  onViewReports,
}: {
  accessToken: string;
  // "누적 신고수"를 눌렀을 때 부모(admin 페이지)가 신고 관리 탭으로 전환하고 그 유저로
  // 필터링하도록 위임한다 — 신고 목록 자체는 AdminReportPanel이 별도로 갖고 있어서다.
  onViewReports: (userId: number, label: string) => void;
}) {
  const { showToast, askConfirm } = useUI();
  const [searchInput, setSearchInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [status, setStatus] = useState<AdminUserStatus | "">("");
  const [page, setPage] = useState(0);
  const [usersPage, setUsersPage] = useState<SpringPage<AdminUserSummary> | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");
  const [suspendTarget, setSuspendTarget] = useState<AdminUserSummary | null>(null);
  const [suspendReason, setSuspendReason] = useState("");
  const [submittingId, setSubmittingId] = useState<number | null>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setKeyword(searchInput.trim());
      setPage(0);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [searchInput]);

  const load = useCallback(
    (signal?: AbortSignal) =>
      getAdminUsers({
        accessToken,
        keyword: keyword || undefined,
        status: status || undefined,
        page,
        size: 20,
        signal,
      })
        .then(setUsersPage)
        .catch((requestError) => {
          if (requestError instanceof DOMException && requestError.name === "AbortError") return;
          setUsersPage(null);
          setErrorMessage(
            requestError instanceof ApiError
              ? requestError.message
              : "회원 목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.",
          );
        }),
    [accessToken, keyword, page, status],
  );

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setErrorMessage("");
    load(controller.signal).finally(() => {
      if (!controller.signal.aborted) setLoading(false);
    });
    return () => controller.abort();
  }, [load]);

  const changeStatus = (next: AdminUserStatus | "") => {
    setStatus(next);
    setPage(0);
  };

  const openSuspend = (user: AdminUserSummary) => {
    setSuspendTarget(user);
    setSuspendReason("");
  };

  const submitSuspend = async () => {
    if (!suspendTarget) return;
    if (!suspendReason.trim()) return showToast("정지 사유를 입력해 주세요.", "err");
    setSubmittingId(suspendTarget.id);
    try {
      await suspendAdminUser(suspendTarget.id, suspendReason.trim(), accessToken);
      showToast(`${suspendTarget.nickname}님을 정지 처리했어요.`);
      setSuspendTarget(null);
      void load();
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError ? requestError.message : "정지 처리에 실패했어요.",
        "err",
      );
    } finally {
      setSubmittingId(null);
    }
  };

  const reactivate = (user: AdminUserSummary) => {
    askConfirm({
      icon: "lock_open",
      title: `${user.nickname}님의 정지를 해제할까요?`,
      ok: "정지 해제",
      body: "바로 활성 상태로 돌아가고 서비스를 다시 이용할 수 있어요.",
      onOk: async () => {
        setSubmittingId(user.id);
        try {
          await reactivateAdminUser(user.id, accessToken);
          showToast(`${user.nickname}님의 정지를 해제했어요.`);
          void load();
        } catch (requestError) {
          showToast(
            requestError instanceof ApiError ? requestError.message : "정지 해제에 실패했어요.",
            "err",
          );
        } finally {
          setSubmittingId(null);
        }
      },
    });
  };

  const users = usersPage?.content ?? [];
  const totalPages = usersPage?.totalPages ?? 0;

  return (
    <section className="overflow-hidden rounded-[18px] bg-white shadow-card">
      <div className="border-b border-line p-5">
        <h2 className="text-lg font-extrabold">유저 관리</h2>
        <p className="mt-1 text-sm text-sub">
          회원 상태와 누적 피신고 건수를 확인하고, 필요 시 정지 조치를 합니다.
        </p>

        <div className="mt-4 grid gap-2 sm:grid-cols-[minmax(0,1fr)_150px]">
          <label htmlFor="admin-user-search" className="sr-only">
            회원 검색
          </label>
          <div className="flex items-center rounded-xl border border-line bg-white focus-within:border-brand">
            <span aria-hidden="true" className="material-symbols-outlined ml-3 shrink-0 text-[20px] text-sub">
              search
            </span>
            <input
              id="admin-user-search"
              type="search"
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
              placeholder="이메일, 닉네임, 이름 또는 회원 ID"
              className="min-w-0 flex-1 bg-transparent px-3 py-3 text-sm text-ink outline-none"
            />
          </div>
          <label htmlFor="admin-user-status" className="sr-only">
            회원 상태
          </label>
          <select
            id="admin-user-status"
            value={status}
            onChange={(event) => changeStatus(event.target.value as AdminUserStatus | "")}
            className="rounded-xl border border-line bg-white px-3.5 py-3 text-sm text-ink outline-none focus:border-brand"
          >
            <option value="">전체 상태</option>
            <option value="ACTIVE">활성 회원</option>
            <option value="SUSPENDED">정지 회원</option>
            <option value="RESTRICTED">제한 회원</option>
            <option value="WITHDRAWN">탈퇴 회원</option>
          </select>
        </div>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full min-w-[820px] text-left text-sm">
          <thead className="bg-[#f6f7f1] text-xs font-extrabold text-sub">
            <tr>
              <th className="px-5 py-3.5">회원</th>
              <th className="px-4 py-3.5">이메일</th>
              <th className="px-4 py-3.5">상태</th>
              <th className="px-4 py-3.5">누적 신고수</th>
              <th className="px-4 py-3.5">가입일</th>
              <th className="px-5 py-3.5 text-right">관리</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={6} className="px-5 py-10 text-center text-sub">
                  회원을 불러오고 있어요.
                </td>
              </tr>
            ) : errorMessage ? (
              <tr>
                <td colSpan={6} role="alert" className="px-5 py-10 text-center text-danger">
                  {errorMessage}
                </td>
              </tr>
            ) : users.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-5 py-10 text-center text-sub">
                  조건에 맞는 회원이 없어요.
                </td>
              </tr>
            ) : (
              users.map((user) => (
                <tr key={user.id} className="border-t border-[#f2f3ec]">
                  <td className="px-5 py-3.5">
                    <div className="font-bold">{user.nickname}</div>
                    <div className="mt-0.5 text-xs text-sub">
                      {user.name} · #{user.id}
                    </div>
                  </td>
                  <td className="px-4 py-3.5 text-[#5f6d5b]">{user.email}</td>
                  <td className="px-4 py-3.5">
                    <span className={`rounded-full px-2.5 py-1 text-xs font-extrabold ${STATUS_STYLES[user.status]}`}>
                      {STATUS_LABELS[user.status]}
                    </span>
                    {user.status === "SUSPENDED" && user.suspendedReason && (
                      <div className="mt-1 max-w-[160px] truncate text-[11px] text-sub" title={user.suspendedReason}>
                        사유: {user.suspendedReason}
                      </div>
                    )}
                    {user.status === "WITHDRAWN" && user.withdrawnAt && (
                      <div className="mt-1 text-[11px] text-sub">{formatDate(user.withdrawnAt)} 탈퇴</div>
                    )}
                  </td>
                  <td className="px-4 py-3.5">
                    <button
                      type="button"
                      onClick={() => onViewReports(user.id, user.nickname)}
                      disabled={user.reportCount === 0}
                      className={`rounded-full px-2.5 py-1 text-xs font-extrabold ${
                        user.reportCount > 0
                          ? "cursor-pointer bg-[#FBEDE3] text-[#b5771a] hover:underline"
                          : "cursor-default bg-[#f0f1ea] text-[#8a8a8a]"
                      }`}
                    >
                      {user.reportCount}건
                    </button>
                  </td>
                  <td className="px-4 py-3.5 text-sub">{formatDate(user.createdAt)}</td>
                  <td className="px-5 py-3.5 text-right">
                    {user.status === "SUSPENDED" ? (
                      <button
                        type="button"
                        onClick={() => reactivate(user)}
                        disabled={submittingId === user.id}
                        className="rounded-lg border border-line bg-brand-soft px-2.5 py-1.5 text-xs font-bold text-brand-dark disabled:cursor-not-allowed disabled:opacity-60"
                      >
                        정지 해제
                      </button>
                    ) : user.status === "WITHDRAWN" || user.role === "ADMIN" ? (
                      <span className="text-xs text-faint">-</span>
                    ) : (
                      <button
                        type="button"
                        onClick={() => openSuspend(user)}
                        disabled={submittingId === user.id}
                        className="rounded-lg border border-[#e8bdad] bg-white px-2.5 py-1.5 text-xs font-bold text-[#b5502f] disabled:cursor-not-allowed disabled:opacity-60"
                      >
                        정지
                      </button>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="flex items-center justify-between border-t border-line px-5 py-3.5 text-sm">
        <span className="text-sub">총 {usersPage?.totalElements ?? 0}명</span>
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

      {suspendTarget && (
        <div
          onClick={() => submittingId == null && setSuspendTarget(null)}
          className="fixed inset-0 z-[60] flex items-center justify-center bg-[rgba(46,54,42,.4)] p-5"
        >
          <div onClick={(e) => e.stopPropagation()} className="w-full max-w-[380px] animate-pop rounded-[20px] bg-white p-6">
            <h3 className="mb-1 text-[18px] font-extrabold">{suspendTarget.nickname}님을 정지할까요?</h3>
            <p className="mb-4 text-[13px] text-sub">정지 사유를 입력해 주세요. 계정 상태가 즉시 "정지"로 바뀌어요.</p>
            <textarea
              autoFocus
              value={suspendReason}
              onChange={(e) => setSuspendReason(e.target.value)}
              maxLength={200}
              placeholder="정지 사유를 입력해 주세요."
              className="mb-4 min-h-[100px] w-full resize-y rounded-xl border-[1.5px] border-line p-3.5 text-sm leading-[1.6] outline-none"
            />
            <div className="flex gap-2.5">
              <button
                type="button"
                onClick={() => void submitSuspend()}
                disabled={submittingId === suspendTarget.id}
                className="flex-1 cursor-pointer rounded-xl bg-danger p-[12px] font-extrabold text-white disabled:cursor-not-allowed disabled:opacity-60"
              >
                {submittingId === suspendTarget.id ? "처리 중..." : "정지"}
              </button>
              <button
                type="button"
                onClick={() => setSuspendTarget(null)}
                disabled={submittingId === suspendTarget.id}
                className="cursor-pointer rounded-xl border-[1.5px] border-line bg-white px-5 py-[12px] font-bold text-sub disabled:cursor-not-allowed disabled:opacity-60"
              >
                취소
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
