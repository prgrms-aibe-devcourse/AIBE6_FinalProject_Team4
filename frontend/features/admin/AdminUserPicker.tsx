"use client";

import { useEffect, useState } from "react";
import {
  AdminUserStatus,
  AdminUserSummary,
  getAdminUsers,
} from "@/features/admin/user-api";
import { ApiError, SpringPage } from "@/lib/api";

interface AdminUserPickerProps {
  accessToken: string | null;
  selectedUserId?: number;
  onSelect: (user: AdminUserSummary) => void;
  disabled?: boolean;
}

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

export default function AdminUserPicker({
  accessToken,
  selectedUserId,
  onSelect,
  disabled = false,
}: AdminUserPickerProps) {
  const [searchInput, setSearchInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [status, setStatus] = useState<AdminUserStatus | "">("ACTIVE");
  const [page, setPage] = useState(0);
  const [usersPage, setUsersPage] =
    useState<SpringPage<AdminUserSummary> | null>(null);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setKeyword(searchInput.trim());
      setPage(0);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [searchInput]);

  useEffect(() => {
    if (!accessToken) return;
    const controller = new AbortController();
    setLoading(true);
    setErrorMessage("");

    getAdminUsers({
      accessToken,
      keyword: keyword || undefined,
      status: status || undefined,
      page,
      size: 10,
      signal: controller.signal,
    })
      .then(setUsersPage)
      .catch((requestError) => {
        if (
          requestError instanceof DOMException &&
          requestError.name === "AbortError"
        )
          return;
        setUsersPage(null);
        setErrorMessage(
          requestError instanceof ApiError
            ? requestError.message
            : "회원 목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.",
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [accessToken, keyword, page, status]);

  const users = usersPage?.content ?? [];
  const totalPages = usersPage?.totalPages ?? 0;

  return (
    <section className="overflow-hidden rounded-[18px] bg-white shadow-card">
      <div className="border-b border-line p-5">
        <h2 className="text-lg font-extrabold">회원 선택</h2>
        <p className="mt-1 text-sm text-sub">
          회원 ID·이메일·닉네임·이름으로 검색할 수 있습니다.
        </p>
        <div className="mt-4 grid gap-2 sm:grid-cols-[minmax(0,1fr)_150px]">
          <label htmlFor="admin-user-search" className="sr-only">
            회원 검색
          </label>
          <div className="flex items-center rounded-xl border border-line bg-white focus-within:border-brand">
            <span
              aria-hidden="true"
              className="material-symbols-outlined ml-3 shrink-0 text-[20px] text-sub"
            >
              search
            </span>
            <input
              id="admin-user-search"
              type="search"
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
              disabled={disabled}
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
            disabled={disabled}
            onChange={(event) => {
              setStatus(event.target.value as AdminUserStatus | "");
              setPage(0);
            }}
            className="rounded-xl border border-line bg-white px-3.5 py-3 text-sm text-ink outline-none focus:border-brand"
          >
            <option value="ACTIVE">활성 회원</option>
            <option value="SUSPENDED">정지 회원</option>
            <option value="RESTRICTED">제한 회원</option>
            <option value="WITHDRAWN">탈퇴 회원</option>
            <option value="">전체 상태</option>
          </select>
        </div>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full min-w-[720px] text-left text-sm">
          <thead className="bg-[#f6f7f1] text-xs font-extrabold text-sub">
            <tr>
              <th className="px-5 py-3.5">회원</th>
              <th className="px-4 py-3.5">이메일</th>
              <th className="px-4 py-3.5">상태</th>
              <th className="px-4 py-3.5">가입일</th>
              <th className="px-5 py-3.5 text-right">선택</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={5} className="px-5 py-10 text-center text-sub">
                  회원을 불러오고 있어요.
                </td>
              </tr>
            ) : errorMessage ? (
              <tr>
                <td
                  colSpan={5}
                  role="alert"
                  className="px-5 py-10 text-center text-danger"
                >
                  {errorMessage}
                </td>
              </tr>
            ) : users.length === 0 ? (
              <tr>
                <td colSpan={5} className="px-5 py-10 text-center text-sub">
                  조건에 맞는 회원이 없습니다.
                </td>
              </tr>
            ) : (
              users.map((user) => {
                const selected = selectedUserId === user.id;
                return (
                  <tr
                    key={user.id}
                    className={`border-t border-[#f2f3ec] ${selected ? "bg-brand-soft/60" : ""}`}
                  >
                    <td className="px-5 py-3.5">
                      <div className="font-bold">{user.nickname}</div>
                      <div className="mt-0.5 text-xs text-sub">
                        {user.name} · #{user.id}
                      </div>
                    </td>
                    <td className="px-4 py-3.5 text-[#5f6d5b]">{user.email}</td>
                    <td className="px-4 py-3.5">
                      <span
                        className={`rounded-full px-2.5 py-1 text-xs font-extrabold ${STATUS_STYLES[user.status]}`}
                      >
                        {STATUS_LABELS[user.status]}
                      </span>
                    </td>
                    <td className="px-4 py-3.5 text-sub">
                      {new Date(user.createdAt).toLocaleDateString("ko-KR")}
                    </td>
                    <td className="px-5 py-3.5 text-right">
                      <button
                        type="button"
                        aria-label={`${user.nickname} 회원 선택`}
                        onClick={() => onSelect(user)}
                        disabled={disabled}
                        className={`rounded-[9px] px-3.5 py-2 text-xs font-extrabold ${
                          selected
                            ? "cursor-default bg-brand text-white"
                            : "cursor-pointer bg-brand-soft text-brand-dark hover:bg-brand hover:text-white"
                        }`}
                      >
                        {selected ? "선택됨" : "선택"}
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
        <span className="text-sub">총 {usersPage?.totalElements ?? 0}명</span>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setPage((current) => Math.max(0, current - 1))}
            disabled={disabled || page === 0 || loading}
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
            disabled={
              disabled || loading || totalPages === 0 || page + 1 >= totalPages
            }
            className="rounded-lg border border-line px-3 py-1.5 font-bold text-sub disabled:cursor-not-allowed disabled:opacity-40"
          >
            다음
          </button>
        </div>
      </div>
    </section>
  );
}
