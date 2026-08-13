'use client';
import Link from 'next/link';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useEffect, useState } from 'react';
import { ApiError } from '@/lib/api';
import { formatDate } from '@/lib/format';
import { BOARD_LIST_URL_KEY, BoardCategory, BoardPostData, getBoardPosts } from '@/lib/board-api';
import { useStore } from '@/lib/store';

const CATEGORY_LABEL: Record<BoardCategory, string> = {
  NOTICE: '공지사항',
  FREE: '자유게시판',
  PLANT_QNA: '식물 Q&A',
};

const CATEGORY_TEXT: Record<BoardCategory, string> = {
  NOTICE: 'text-[#b5872f]',
  FREE: 'text-[#3a76a8]',
  PLANT_QNA: 'text-brand-dark',
};

const CATEGORY_BG: Record<BoardCategory, string> = {
  NOTICE: 'bg-[#FBF3E1]',
  FREE: 'bg-[#E8F1F8]',
  PLANT_QNA: 'bg-brand-soft',
};

const TABS: { key: 'ALL' | BoardCategory; label: string }[] = [
  { key: 'ALL', label: '전체' },
  { key: 'NOTICE', label: '공지사항' },
  { key: 'FREE', label: '자유게시판' },
  { key: 'PLANT_QNA', label: '식물 Q&A' },
];

const PAGE_SIZE = 15;
const CATEGORY_KEYS = new Set(['NOTICE', 'FREE', 'PLANT_QNA']);

function parseTab(value: string | null): 'ALL' | BoardCategory {
  return value && CATEGORY_KEYS.has(value) ? (value as BoardCategory) : 'ALL';
}

function parsePage(value: string | null): number {
  const page = Number(value);
  return Number.isFinite(page) && page > 0 ? Math.floor(page) - 1 : 0;
}

export default function BoardPage() {
  return (
    <Suspense fallback={null}>
      <BoardPageContent />
    </Suspense>
  );
}

function BoardPageContent() {
  const { state, hydrated } = useStore();
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const [tab, setTab] = useState<'ALL' | BoardCategory>(() => parseTab(searchParams.get('category')));
  const [page, setPage] = useState(() => parsePage(searchParams.get('page')));
  const [posts, setPosts] = useState<BoardPostData[]>([]);
  const [notices, setNotices] = useState<BoardPostData[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // 공지사항은 카테고리/페이지와 무관하게 항상 맨 위에 고정해서 보여준다.
  useEffect(() => {
    if (!hydrated) return;
    const controller = new AbortController();

    getBoardPosts('NOTICE', 0, 50, state.accessToken, controller.signal)
      .then((result) => setNotices(result.content))
      .catch((requestError) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return;
        setNotices([]);
      });

    return () => controller.abort();
  }, [hydrated, state.accessToken]);

  // 카테고리 탭/페이지를 URL 쿼리로 반영해 새로고침해도 그대로 유지되게 한다.
  useEffect(() => {
    const params = new URLSearchParams();
    if (tab !== 'ALL') params.set('category', tab);
    if (page > 0) params.set('page', String(page + 1));
    const query = params.toString();
    const url = query ? `${pathname}?${query}` : pathname;
    router.replace(url, { scroll: false });
    if (typeof window !== 'undefined') window.sessionStorage.setItem(BOARD_LIST_URL_KEY, url);
  }, [tab, page, pathname, router]);

  useEffect(() => {
    if (!hydrated) return;
    const controller = new AbortController();
    setLoading(true);
    setError('');

    getBoardPosts(tab === 'ALL' ? undefined : tab, page, PAGE_SIZE, state.accessToken, controller.signal)
      .then((result) => {
        setPosts(result.content);
        setTotalElements(result.totalElements);
        setTotalPages(result.totalPages);
      })
      .catch((requestError) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return;
        setPosts([]);
        setError(
          requestError instanceof ApiError
            ? requestError.message
            : '게시글을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.',
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [hydrated, tab, page, state.accessToken]);

  // NOTICE 탭 자체를 볼 때는 목록이 이미 전부 공지라 별도 고정 영역이 필요 없다.
  const pinned = tab === 'NOTICE' ? [] : notices;
  const normal = tab === 'NOTICE' ? posts : posts.filter((p) => p.category !== 'NOTICE');
  // ALL 탭만 공지가 실제로 목록에 섞여서 totalElements에 포함되니 그만큼 빼준다.
  // FREE/PLANT_QNA/NOTICE는 이미 카테고리로 필터링된 개수라 그대로 쓴다.
  const nonNoticeTotal = tab === 'ALL' ? Math.max(0, totalElements - notices.length) : totalElements;
  const baseNumber = nonNoticeTotal - page * PAGE_SIZE;

  const changeTab = (next: 'ALL' | BoardCategory) => {
    setTab(next);
    setPage(0);
  };

  // 모바일은 표 대신 카드형(제목 위, 작성자·시간·통계 아래)으로 보여줘서 제목이 잘리지 않게
  // 하고, sm 이상에서는 기존 표 레이아웃을 그대로 쓴다.
  const renderRow = (post: BoardPostData, pinned: boolean, number: number | null) => (
    <Link
      key={post.id}
      href={`/board/${post.id}`}
      className={`block px-4 py-3.5 text-ink hover:text-ink sm:px-5 ${
        pinned ? 'bg-brand-soft/40 hover:bg-brand-soft/70' : 'hover:bg-[#F8FAF3]'
      }`}
    >
      <div className="flex flex-col gap-1.5 sm:hidden">
        <div className="flex items-start gap-1.5">
          {pinned && <span className="mt-0.5 shrink-0 text-sm">📌</span>}
          <span className={`shrink-0 rounded px-1.5 py-[3px] text-[11px] font-extrabold ${CATEGORY_BG[post.category]} ${CATEGORY_TEXT[post.category]}`}>
            {CATEGORY_LABEL[post.category]}
          </span>
          <span className="min-w-0 flex-1 break-words font-bold leading-snug">
            {post.title}
            {post.commentCount > 0 && (
              <span className="ml-1.5 text-xs font-bold text-[#b5502f]">[{post.commentCount}]</span>
            )}
          </span>
        </div>
        <div className="flex flex-wrap items-center gap-x-1.5 gap-y-0.5 text-xs text-faint">
          <span className="font-bold text-sub">{post.nickname}</span>
          <span>·</span>
          <span>{formatDate(post.createdAt)}</span>
          <span className="ml-auto whitespace-nowrap">
            조회 {post.viewCount} · 추천 {post.likeCount}
          </span>
        </div>
      </div>

      <div className="hidden items-center gap-2 sm:grid sm:grid-cols-[60px_1fr_90px_84px_56px_56px]">
        <div className="text-center text-sm text-faint">{pinned ? '📌' : number}</div>
        <div className="flex min-w-0 items-baseline gap-1.5">
          <span className={`shrink-0 text-xs font-extrabold ${CATEGORY_TEXT[post.category]}`}>
            [{CATEGORY_LABEL[post.category]}]
          </span>
          <span className={`min-w-0 truncate ${pinned ? 'font-extrabold' : 'font-bold'}`}>{post.title}</span>
          {post.commentCount > 0 && (
            <span className="shrink-0 text-xs font-bold text-[#b5502f]">[{post.commentCount}]</span>
          )}
        </div>
        <div className="truncate text-center text-sm text-sub">{post.nickname}</div>
        <div className="text-center text-xs text-faint">{formatDate(post.createdAt)}</div>
        <div className="text-center text-sm text-faint">{post.viewCount}</div>
        <div className="text-center text-sm font-bold text-[#b5502f]">{post.likeCount}</div>
      </div>
    </Link>
  );

  return (
    <div className="container">
      <div className="mb-1.5 flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-[27px] font-extrabold">커뮤니티 게시판</h1>
        {state.accessToken && (
          <Link
            href="/board/new"
            className="rounded-xl bg-brand px-5 py-3 text-[15px] font-bold text-white hover:text-white"
          >
            + 글쓰기
          </Link>
        )}
      </div>
      <p className="mb-5 text-sub">다른 사람들과 식물 키우는 이야기를 나눠보세요.</p>

      <div className="mb-4 flex flex-wrap gap-2">
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            onClick={() => changeTab(t.key)}
            className={`cursor-pointer rounded-full border-[1.5px] px-[15px] py-2 text-sm font-bold ${
              tab === t.key ? 'border-brand bg-brand text-white' : 'border-line bg-white text-[#6d7a68]'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      <div className="overflow-hidden rounded-[18px] bg-white shadow-card">
        <div className="hidden items-center gap-2 border-b-2 border-ink/80 px-4 py-3 text-xs font-extrabold text-faint sm:grid sm:grid-cols-[60px_1fr_90px_84px_56px_56px] sm:px-5">
          <div className="text-center">번호</div>
          <div>제목</div>
          <div className="text-center">글쓴이</div>
          <div className="text-center">작성일</div>
          <div className="text-center">조회</div>
          <div className="text-center">추천</div>
        </div>

        {loading ? (
          <div className="px-5 py-[60px] text-center text-sub">게시글을 불러오고 있어요 🌱</div>
        ) : error ? (
          <div className="px-5 py-[60px] text-center text-sub">{error}</div>
        ) : posts.length === 0 && pinned.length === 0 ? (
          <div className="px-5 py-[60px] text-center text-sub">
            아직 게시글이 없어요. 첫 글을 남겨보세요 🌱
          </div>
        ) : (
          <div className="divide-y divide-[#f0f1ea]">
            {pinned.map((post) => renderRow(post, true, null))}

            {normal.length === 0 && (
              <div className="px-5 py-[40px] text-center text-sub">아직 게시글이 없어요.</div>
            )}

            {normal.map((post, index) => renderRow(post, false, baseNumber - index))}
          </div>
        )}
      </div>
      {!loading && !error && posts.length > 0 && (
        <>
          <div className="mt-3 text-right text-xs text-faint">전체 {totalElements}개</div>
          {totalPages > 1 && (
            <div className="mt-4 flex flex-wrap items-center justify-center gap-1.5">
              <button
                type="button"
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
                className="cursor-pointer rounded-lg border-[1.5px] border-line bg-white px-3 py-2 text-sm font-bold text-sub disabled:cursor-not-allowed disabled:opacity-40"
              >
                이전
              </button>
              {Array.from({ length: totalPages }, (_, i) => i).map((p) => (
                <button
                  key={p}
                  type="button"
                  onClick={() => setPage(p)}
                  className={`h-9 min-w-9 cursor-pointer rounded-lg border-[1.5px] px-2 text-sm font-bold ${
                    p === page ? 'border-brand bg-brand text-white' : 'border-line bg-white text-sub'
                  }`}
                >
                  {p + 1}
                </button>
              ))}
              <button
                type="button"
                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
                className="cursor-pointer rounded-lg border-[1.5px] border-line bg-white px-3 py-2 text-sm font-bold text-sub disabled:cursor-not-allowed disabled:opacity-40"
              >
                다음
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
