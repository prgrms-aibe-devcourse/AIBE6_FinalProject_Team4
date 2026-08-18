'use client';
import Link from 'next/link';
import { useEffect, useState } from 'react';
import { ApiError } from '@/lib/api';
import { formatDate } from '@/lib/format';
import {
  BoardCategory,
  BoardCommentData,
  BoardPostData,
  getMyBoardComments,
  getMyBoardPosts,
} from '@/lib/board-api';
import { useStore } from '@/lib/store';

const CATEGORY_LABEL: Record<BoardCategory, string> = {
  NOTICE: '공지',
  FREE: '자유',
  PLANT_QNA: 'Q&A',
};

type Tab = 'posts' | 'comments';

export default function MyBoardPage() {
  const { state, hydrated } = useStore();
  const [tab, setTab] = useState<Tab>('posts');
  const [posts, setPosts] = useState<BoardPostData[]>([]);
  const [comments, setComments] = useState<BoardCommentData[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!hydrated || !state.accessToken) return;
    const accessToken = state.accessToken;
    let cancelled = false;
    setLoading(true);
    setError('');

    const request =
      tab === 'posts'
        ? getMyBoardPosts(accessToken, 0, 50).then((page) => setPosts(page.content))
        : getMyBoardComments(accessToken, 0, 50).then((page) => setComments(page.content));

    request
      .catch((requestError) => {
        if (cancelled) return;
        if (tab === 'posts') setPosts([]);
        else setComments([]);
        setError(
          requestError instanceof ApiError
            ? requestError.message
            : '목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.',
        );
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [hydrated, state.accessToken, tab]);

  if (!hydrated) {
    return <div className="container" />;
  }

  if (!state.accessToken) {
    return (
      <div className="container">
        <div className="px-5 py-[60px] text-center text-sub">로그인이 필요해요.</div>
      </div>
    );
  }

  return (
    <div className="container max-w-[800px]">
      <h1 className="mb-1 text-[26px] font-extrabold">내가 쓴 글/댓글</h1>
      <p className="mb-5 text-sub">커뮤니티 게시판에 남긴 글과 댓글을 모아 볼 수 있어요.</p>

      <div className="mb-4 flex gap-2">
        <button
          type="button"
          onClick={() => setTab('posts')}
          className={`cursor-pointer rounded-full border-[1.5px] px-[15px] py-2 text-sm font-bold ${
            tab === 'posts' ? 'border-brand bg-brand text-white' : 'border-line bg-white text-[#6d7a68]'
          }`}
        >
          내가 쓴 글
        </button>
        <button
          type="button"
          onClick={() => setTab('comments')}
          className={`cursor-pointer rounded-full border-[1.5px] px-[15px] py-2 text-sm font-bold ${
            tab === 'comments' ? 'border-brand bg-brand text-white' : 'border-line bg-white text-[#6d7a68]'
          }`}
        >
          내가 쓴 댓글
        </button>
      </div>

      <div className="flex flex-col gap-3">
        {loading ? (
          <div className="rounded-[14px] bg-white px-[18px] py-[60px] text-center text-sub shadow-card">
            불러오는 중이에요 🌱
          </div>
        ) : error ? (
          <div className="rounded-[14px] bg-white px-[18px] py-[60px] text-center text-sub shadow-card">{error}</div>
        ) : tab === 'posts' ? (
          posts.length === 0 ? (
            <div className="rounded-[14px] bg-white px-[18px] py-[60px] text-center text-sub shadow-card">
              아직 작성한 글이 없어요.
            </div>
          ) : (
            posts.map((post) => (
              <Link
                key={post.id}
                href={`/board/${post.id}`}
                className="rounded-[14px] bg-white px-[18px] py-4 text-ink shadow-card hover:text-ink"
              >
                <div className="flex flex-wrap items-center gap-2">
                  <span className="rounded-full bg-brand-soft px-[9px] py-[3px] text-xs font-extrabold text-brand-dark">
                    {CATEGORY_LABEL[post.category]}
                  </span>
                  <span className="font-bold">{post.title}</span>
                  {post.status === 'HIDDEN' && (
                    <span className="rounded-full bg-danger-soft px-[9px] py-[3px] text-xs font-extrabold text-danger">
                      숨김
                    </span>
                  )}
                </div>
                <div className="mt-2 flex items-center gap-3 text-xs text-faint">
                  <span>{formatDate(post.createdAt)}</span>
                  <span>조회 {post.viewCount}</span>
                  <span>좋아요 {post.likeCount}</span>
                  <span>댓글 {post.commentCount}</span>
                </div>
              </Link>
            ))
          )
        ) : comments.length === 0 ? (
          <div className="rounded-[14px] bg-white px-[18px] py-[60px] text-center text-sub shadow-card">
            아직 작성한 댓글이 없어요.
          </div>
        ) : (
          comments.map((comment) => (
            <Link
              key={comment.id}
              href={`/board/${comment.postId}`}
              className="rounded-[14px] bg-white px-[18px] py-4 text-ink shadow-card hover:text-ink"
            >
              <p className="line-clamp-2 text-[14.5px] leading-[1.6]">{comment.content}</p>
              <div className="mt-2 flex items-center gap-3 text-xs text-faint">
                <span>{formatDate(comment.createdAt)}</span>
                <span>좋아요 {comment.likeCount}</span>
                {comment.parentCommentId != null && <span>답글</span>}
              </div>
            </Link>
          ))
        )}
      </div>
    </div>
  );
}
