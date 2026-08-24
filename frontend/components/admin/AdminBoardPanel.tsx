"use client";

import Link from "next/link";
import { useCallback, useRef, useState } from "react";
import { ApiError } from "@/lib/api";
import { getBoardPostsForAdmin, restoreBoardPostAsAdmin } from "@/lib/board-api";
import { useUI } from "@/lib/ui";
import { useAdminPaginatedList } from "./use-admin-paginated-list";
import AdminPagination from "./AdminPagination";
import { useScrollOnPageLoad } from "./use-scroll-on-page-load";

const PAGE_SIZE = 10;
const COLUMN_COUNT = 6;

const CATEGORY_LABEL: Record<string, string> = {
  NOTICE: "공지",
  FREE: "자유",
  PLANT_QNA: "Q&A",
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

export default function AdminBoardPanel({ accessToken }: { accessToken: string }) {
  const { showToast, askConfirm } = useUI();
  const sectionRef = useRef<HTMLElement>(null);
  const [restoringIds, setRestoringIds] = useState<Set<number>>(new Set());

  const fetchPage = useCallback(
    (page: number, signal?: AbortSignal) =>
      getBoardPostsForAdmin("HIDDEN", page, PAGE_SIZE, accessToken, signal),
    [accessToken],
  );
  const {
    page,
    setPage,
    items: posts,
    totalPages,
    totalElements,
    loading,
    errorMessage,
    reload,
  } = useAdminPaginatedList(fetchPage, "숨김 처리된 게시글 목록을 불러오지 못했어요.");

  useScrollOnPageLoad(page, loading, sectionRef);

  const restorePost = (id: number) => {
    if (restoringIds.has(id)) return;
    askConfirm({
      icon: "visibility",
      title: "이 게시글의 숨김을 해제할까요?",
      ok: "숨김 해제",
      body: "다시 게시판에 노출돼요. 단, 숨김 처리 시점에 삭제된 첨부 이미지는 복원되지 않아요.",
      onOk: async () => {
        setRestoringIds((prev) => new Set(prev).add(id));
        try {
          await restoreBoardPostAsAdmin(id, accessToken);
          showToast("게시글 숨김을 해제했어요.");
          reload();
        } catch (requestError) {
          showToast(
            requestError instanceof ApiError ? requestError.message : "숨김 해제에 실패했어요.",
            "err",
          );
        } finally {
          setRestoringIds((prev) => {
            const next = new Set(prev);
            next.delete(id);
            return next;
          });
        }
      },
    });
  };

  return (
    <section ref={sectionRef} className="overflow-hidden rounded-[18px] bg-white shadow-card">
      <div className="border-b border-line p-5">
        <h2 className="text-lg font-extrabold">게시판 관리</h2>
        <p className="mt-1 text-sm text-sub">
          숨김 처리된 게시글 목록입니다. 관리자가 숨긴 글만 복원할 수 있고, 작성자가 직접 삭제한 글은
          복원할 수 없어요.
        </p>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full min-w-[720px] text-left text-sm">
          <thead className="bg-[#f6f7f1] text-xs font-extrabold text-sub">
            <tr>
              <th className="px-5 py-3.5">카테고리</th>
              <th className="px-4 py-3.5">제목</th>
              <th className="px-4 py-3.5">작성자</th>
              <th className="px-4 py-3.5">숨김 처리자</th>
              <th className="px-4 py-3.5">작성일</th>
              <th className="px-5 py-3.5 text-right">관리</th>
            </tr>
          </thead>
          <tbody>
            {loading && posts.length === 0 ? (
              <tr>
                <td colSpan={COLUMN_COUNT} className="px-5 py-10 text-center text-sub">
                  숨김 처리된 게시글을 불러오고 있어요.
                </td>
              </tr>
            ) : errorMessage ? (
              <tr>
                <td colSpan={COLUMN_COUNT} role="alert" className="px-5 py-10 text-center text-danger">
                  {errorMessage}
                </td>
              </tr>
            ) : posts.length === 0 ? (
              <tr>
                <td colSpan={COLUMN_COUNT} className="px-5 py-10 text-center text-sub">
                  숨김 처리된 게시글이 없어요.
                </td>
              </tr>
            ) : (
              posts.map((post) => (
                <tr key={post.id} className="border-t border-[#f2f3ec]">
                  <td className="px-5 py-3.5 text-sub">{CATEGORY_LABEL[post.category] ?? post.category}</td>
                  <td className="max-w-[280px] truncate px-4 py-3.5 font-bold">{post.title}</td>
                  <td className="px-4 py-3.5 text-sub">{post.nickname}</td>
                  <td className="px-4 py-3.5">
                    {post.hiddenBy === "ADMIN" ? (
                      <span className="rounded-full bg-[#FBEDE3] px-2.5 py-1 text-xs font-extrabold text-[#b5771a]">
                        관리자
                      </span>
                    ) : (
                      <span className="rounded-full bg-[#f0f1ea] px-2.5 py-1 text-xs font-extrabold text-[#8a8a8a]">
                        작성자 자진 삭제
                      </span>
                    )}
                  </td>
                  <td className="whitespace-nowrap px-4 py-3.5 text-sub">{formatDateTime(post.createdAt)}</td>
                  <td className="px-5 py-3.5 text-right">
                    <div className="flex justify-end gap-1.5">
                      <Link
                        href={`/board/${post.id}`}
                        target="_blank"
                        className="rounded-lg border border-line px-2.5 py-1.5 text-xs font-bold"
                      >
                        상세 보기
                      </Link>
                      {post.hiddenBy === "ADMIN" ? (
                        <button
                          type="button"
                          onClick={() => restorePost(post.id)}
                          disabled={restoringIds.has(post.id)}
                          className="cursor-pointer rounded-lg border border-line bg-brand-soft px-2.5 py-1.5 text-xs font-bold text-brand-dark disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          {restoringIds.has(post.id) ? "해제 중..." : "숨김 해제"}
                        </button>
                      ) : (
                        <span
                          title="작성자가 직접 삭제한 글은 복원할 수 없어요."
                          className="rounded-lg border border-line px-2.5 py-1.5 text-xs font-bold text-faint"
                        >
                          복원 불가
                        </span>
                      )}
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="flex flex-col items-center gap-3 border-t border-line px-5 pt-3.5 pb-5 text-sm sm:flex-row sm:justify-between">
        <span className="text-sub">총 {totalElements}건</span>
        <AdminPagination page={page} totalPages={totalPages} onChange={setPage} />
      </div>
    </section>
  );
}
