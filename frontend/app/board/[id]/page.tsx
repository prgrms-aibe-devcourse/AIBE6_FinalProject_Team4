'use client';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useMemo, useState } from 'react';
import { ApiError, resolveImageUrl } from '@/lib/api';
import { formatDate } from '@/lib/format';
import { useStore } from '@/lib/store';
import { useUI } from '@/lib/ui';
import { PlantJournalData } from '@/lib/journal-api';
import { createReport } from '@/lib/report-api';
import {
  BoardCategory,
  BoardCommentData,
  BoardPostData,
  createBoardComment,
  deleteBoardComment,
  deleteBoardPost,
  getBoardComments,
  getBoardPost,
  getBoardPostJournal,
  likeBoardComment,
  likeBoardPost,
  unlikeBoardComment,
  unlikeBoardPost,
  updateBoardComment,
} from '@/lib/board-api';

const CATEGORY_LABEL: Record<BoardCategory, string> = {
  NOTICE: '공지',
  FREE: '자유',
  PLANT_QNA: 'Q&A',
};

const CATEGORY_TEXT: Record<BoardCategory, string> = {
  NOTICE: 'text-[#b5872f]',
  FREE: 'text-[#3a76a8]',
  PLANT_QNA: 'text-brand-dark',
};

function representativeImage(journal: PlantJournalData): string | null {
  const url = journal.images.find((img) => img.representative)?.imageUrl || journal.images[0]?.imageUrl || null;
  return url ? resolveImageUrl(url) : null;
}

export default function BoardDetailPage({ params }: { params: { id: string } }) {
  const router = useRouter();
  const { state, hydrated } = useStore();
  const { showToast, askConfirm } = useUI();
  const postId = Number(params.id);

  const [post, setPost] = useState<BoardPostData | null>(null);
  const [comments, setComments] = useState<BoardCommentData[]>([]);
  const [journal, setJournal] = useState<PlantJournalData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [liked, setLiked] = useState(false);
  const [likeCount, setLikeCount] = useState(0);
  const [likedComments, setLikedComments] = useState<Record<number, boolean>>({});
  const [likePending, setLikePending] = useState(false);
  const [commentLikePendingIds, setCommentLikePendingIds] = useState<Set<number>>(new Set());

  const [commentDraft, setCommentDraft] = useState('');
  const [replyingTo, setReplyingTo] = useState<number | null>(null);
  const [replyDraft, setReplyDraft] = useState('');
  const [editingComment, setEditingComment] = useState<number | null>(null);
  const [editDraft, setEditDraft] = useState('');
  const [reportingComment, setReportingComment] = useState<number | null>(null);
  const [reportingPost, setReportingPost] = useState(false);

  useEffect(() => {
    if (!hydrated || Number.isNaN(postId)) return;
    const controller = new AbortController();
    setLoading(true);
    setError('');

    Promise.all([
      getBoardPost(postId, state.accessToken, controller.signal),
      getBoardComments(postId, state.accessToken, controller.signal),
    ])
      .then(([postData, commentList]) => {
        setPost(postData);
        setLikeCount(postData.likeCount);
        setLiked(postData.likedByMe);
        setComments(commentList);
        setLikedComments(
          Object.fromEntries(commentList.map((comment) => [comment.id, comment.likedByMe])),
        );
        if (postData.journalId) {
          getBoardPostJournal(postId, controller.signal)
            .then((j) => setJournal(j))
            .catch(() => setJournal(null));
        }
      })
      .catch((requestError) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return;
        setPost(null);
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
  }, [hydrated, postId, state.accessToken]);

  const toggleLike = async () => {
    if (!state.accessToken) return showToast('로그인이 필요해요.', 'err');
    if (likePending) return;
    const wasLiked = liked;
    setLikePending(true);
    setLiked(!wasLiked);
    setLikeCount((prev) => (wasLiked ? prev - 1 : prev + 1));
    try {
      if (wasLiked) {
        await unlikeBoardPost(postId, state.accessToken);
      } else {
        await likeBoardPost(postId, state.accessToken);
      }
    } catch (requestError) {
      // 서버가 "이미 좋아요를 눌렀다"고 답하면 화면 상태를 되돌리지 않고 좋아요 상태 그대로 맞춘다.
      const alreadyLiked = requestError instanceof ApiError && requestError.message.includes('이미 좋아요');
      if (!alreadyLiked) {
        setLiked(wasLiked);
        setLikeCount((prev) => (wasLiked ? prev + 1 : prev - 1));
      } else {
        setLiked(true);
      }
      showToast(
        requestError instanceof ApiError ? requestError.message : '좋아요 처리에 실패했어요.',
        'err',
      );
    } finally {
      setLikePending(false);
    }
  };

  const toggleCommentLike = async (comment: BoardCommentData) => {
    if (!state.accessToken) return showToast('로그인이 필요해요.', 'err');
    if (commentLikePendingIds.has(comment.id)) return;
    const wasLiked = !!likedComments[comment.id];
    setCommentLikePendingIds((prev) => new Set(prev).add(comment.id));
    setLikedComments((prev) => ({ ...prev, [comment.id]: !wasLiked }));
    setComments((prev) =>
      prev.map((c) => (c.id === comment.id ? { ...c, likeCount: c.likeCount + (wasLiked ? -1 : 1) } : c)),
    );
    try {
      if (wasLiked) {
        await unlikeBoardComment(comment.id, state.accessToken);
      } else {
        await likeBoardComment(comment.id, state.accessToken);
      }
    } catch (requestError) {
      const alreadyLiked = requestError instanceof ApiError && requestError.message.includes('이미 좋아요');
      if (!alreadyLiked) {
        setLikedComments((prev) => ({ ...prev, [comment.id]: wasLiked }));
        setComments((prev) =>
          prev.map((c) => (c.id === comment.id ? { ...c, likeCount: c.likeCount + (wasLiked ? 1 : -1) } : c)),
        );
      } else {
        setLikedComments((prev) => ({ ...prev, [comment.id]: true }));
      }
      showToast(
        requestError instanceof ApiError ? requestError.message : '좋아요 처리에 실패했어요.',
        'err',
      );
    } finally {
      setCommentLikePendingIds((prev) => {
        const next = new Set(prev);
        next.delete(comment.id);
        return next;
      });
    }
  };

  const submitComment = async () => {
    if (!commentDraft.trim()) return;
    if (!state.accessToken) return showToast('로그인이 필요해요.', 'err');
    try {
      const created = await createBoardComment(postId, { content: commentDraft.trim() }, state.accessToken);
      setComments((prev) => [...prev, created]);
      setCommentDraft('');
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError ? requestError.message : '댓글 등록에 실패했어요.',
        'err',
      );
    }
  };

  const submitReply = async (parentId: number) => {
    if (!replyDraft.trim()) return;
    if (!state.accessToken) return showToast('로그인이 필요해요.', 'err');
    try {
      const created = await createBoardComment(
        postId,
        { content: replyDraft.trim(), parentCommentId: parentId },
        state.accessToken,
      );
      setComments((prev) => [...prev, created]);
      setReplyDraft('');
      setReplyingTo(null);
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError ? requestError.message : '답글 등록에 실패했어요.',
        'err',
      );
    }
  };

  const startEdit = (comment: BoardCommentData) => {
    setEditingComment(comment.id);
    setEditDraft(comment.content);
  };

  const cancelEdit = () => {
    setEditingComment(null);
    setEditDraft('');
  };

  const submitEdit = async (commentId: number) => {
    if (!editDraft.trim()) return;
    if (!state.accessToken) return showToast('로그인이 필요해요.', 'err');
    try {
      const updated = await updateBoardComment(commentId, editDraft.trim(), state.accessToken);
      setComments((prev) => prev.map((c) => (c.id === commentId ? updated : c)));
      cancelEdit();
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError ? requestError.message : '댓글 수정에 실패했어요.',
        'err',
      );
    }
  };

  const removeComment = (comment: BoardCommentData) => {
    if (!state.accessToken) return;
    const accessToken = state.accessToken;
    askConfirm({
      icon: 'delete',
      title: '댓글을 삭제할까요?',
      ok: '삭제하기',
      danger: true,
      body: '삭제한 댓글은 되돌릴 수 없어요.',
      onOk: async () => {
        try {
          await deleteBoardComment(comment.id, accessToken);
          setComments((prev) => prev.filter((c) => c.id !== comment.id));
          if (post) setPost({ ...post, commentCount: post.commentCount - 1 });
        } catch (requestError) {
          showToast(
            requestError instanceof ApiError ? requestError.message : '댓글 삭제에 실패했어요.',
            'err',
          );
        }
      },
    });
  };

  const removePost = () => {
    if (!state.accessToken) return;
    const accessToken = state.accessToken;
    askConfirm({
      icon: 'delete',
      title: '게시글을 삭제할까요?',
      ok: '삭제하기',
      danger: true,
      body: '삭제한 게시글은 되돌릴 수 없어요.',
      onOk: async () => {
        try {
          await deleteBoardPost(postId, accessToken);
          showToast('게시글을 삭제했어요.');
          router.push('/board');
        } catch (requestError) {
          showToast(
            requestError instanceof ApiError ? requestError.message : '게시글 삭제에 실패했어요.',
            'err',
          );
        }
      },
    });
  };

  const submitPostReport = async () => {
    if (!state.accessToken) return showToast('로그인이 필요해요.', 'err');
    try {
      await createReport(
        { targetType: 'POST', targetId: postId, reason: '부적절한 게시글' },
        state.accessToken,
      );
      setReportingPost(false);
      showToast('신고가 접수됐어요. 검토 후 조치할게요.');
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError ? requestError.message : '신고 접수에 실패했어요.',
        'err',
      );
    }
  };

  const submitCommentReport = async () => {
    if (reportingComment == null) return;
    if (!state.accessToken) return showToast('로그인이 필요해요.', 'err');
    try {
      await createReport(
        { targetType: 'COMMENT', targetId: reportingComment, reason: '부적절한 댓글' },
        state.accessToken,
      );
      setReportingComment(null);
      showToast('신고가 접수됐어요. 검토 후 조치할게요.');
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError ? requestError.message : '신고 접수에 실패했어요.',
        'err',
      );
    }
  };

  const childrenOf = (parentId: number | null) => comments.filter((c) => c.parentCommentId === parentId);

  const renderComment = (comment: BoardCommentData, depth: number) => {
    const children = childrenOf(comment.id);
    const isMine = state.user?.id === comment.userId;
    return (
      <div key={comment.id} style={{ marginLeft: depth > 0 ? 28 : 0 }}>
        <div className={`flex items-start justify-between gap-3 py-3.5 ${depth > 0 ? 'border-l-2 border-[#eceee5] pl-3.5' : ''}`}>
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2 text-[13.5px]">
              {depth > 0 && <span className="text-faint">↳</span>}
              <span className="font-extrabold text-ink">{comment.nickname}</span>
              {isMine && (
                <span className="rounded-full bg-brand-soft px-2 py-[2px] text-[11px] font-bold text-brand-dark">
                  나
                </span>
              )}
              <span className="text-xs text-faint">{formatDate(comment.createdAt)}</span>
            </div>
            {editingComment === comment.id ? (
              <div className="mt-2 flex gap-2">
                <input
                  autoFocus
                  value={editDraft}
                  onChange={(e) => setEditDraft(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && submitEdit(comment.id)}
                  className="flex-1 rounded-[10px] border-[1.5px] border-line px-3 py-2 text-[13.5px] outline-none"
                />
                <button
                  type="button"
                  onClick={() => submitEdit(comment.id)}
                  className="shrink-0 cursor-pointer rounded-[10px] bg-brand px-4 py-2 text-[13px] font-bold text-white"
                >
                  저장
                </button>
                <button
                  type="button"
                  onClick={cancelEdit}
                  className="shrink-0 cursor-pointer rounded-[10px] border-[1.5px] border-line bg-white px-3 py-2 text-[13px] font-bold text-sub"
                >
                  취소
                </button>
              </div>
            ) : (
              <p className="mt-1 whitespace-pre-wrap text-[14.5px] leading-[1.6] text-ink">{comment.content}</p>
            )}
            <div className="mt-1.5 flex items-center gap-3">
              <button
                type="button"
                onClick={() => {
                  setReplyingTo(replyingTo === comment.id ? null : comment.id);
                  setReplyDraft('');
                }}
                className="cursor-pointer text-xs font-bold text-faint hover:text-brand-dark"
              >
                답글 달기
              </button>
              {isMine ? (
                <>
                  <button
                    type="button"
                    onClick={() => startEdit(comment)}
                    className="cursor-pointer text-xs font-bold text-faint hover:text-brand-dark"
                  >
                    수정
                  </button>
                  <button
                    type="button"
                    onClick={() => removeComment(comment)}
                    className="cursor-pointer text-xs font-bold text-faint hover:text-[#b5502f]"
                  >
                    삭제
                  </button>
                </>
              ) : (
                <button
                  type="button"
                  onClick={() => setReportingComment(comment.id)}
                  className="cursor-pointer text-xs font-bold text-faint hover:text-[#b5502f]"
                >
                  신고
                </button>
              )}
            </div>

            {replyingTo === comment.id && (
              <div className="mt-2.5 flex gap-2">
                <input
                  autoFocus
                  value={replyDraft}
                  onChange={(e) => setReplyDraft(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && submitReply(comment.id)}
                  placeholder={`${comment.nickname}님에게 답글 남기기`}
                  className="flex-1 rounded-[10px] border-[1.5px] border-line px-3 py-2 text-[13.5px] outline-none"
                />
                <button
                  type="button"
                  onClick={() => submitReply(comment.id)}
                  className="shrink-0 cursor-pointer rounded-[10px] bg-brand px-4 py-2 text-[13px] font-bold text-white"
                >
                  등록
                </button>
                <button
                  type="button"
                  onClick={() => setReplyingTo(null)}
                  className="shrink-0 cursor-pointer rounded-[10px] border-[1.5px] border-line bg-white px-3 py-2 text-[13px] font-bold text-sub"
                >
                  취소
                </button>
              </div>
            )}
          </div>
          <button
            type="button"
            onClick={() => toggleCommentLike(comment)}
            disabled={commentLikePendingIds.has(comment.id)}
            className={`flex shrink-0 cursor-pointer items-center gap-1 text-xs font-bold disabled:cursor-not-allowed disabled:opacity-60 ${
              likedComments[comment.id] ? 'text-[#b5502f]' : 'text-faint'
            }`}
          >
            <span className="material-symbols-outlined text-[15px]">favorite</span>
            {comment.likeCount}
          </button>
        </div>
        {children.map((child) => renderComment(child, depth + 1))}
      </div>
    );
  };

  const roots = useMemo(() => childrenOf(null), [comments]);

  if (!hydrated || loading) {
    return (
      <div className="container">
        <div className="px-5 py-[60px] text-center text-sub">게시글을 불러오고 있어요 🌱</div>
      </div>
    );
  }

  if (error || !post) {
    return (
      <div className="container">
        <button
          type="button"
          onClick={() => router.back()}
          className="cursor-pointer rounded-[10px] border-[1.5px] border-line bg-white px-3 py-2 text-sm font-semibold text-sub hover:bg-brand-soft hover:text-brand-dark"
        >
          ← 목록으로
        </button>
        <div className="mt-4 px-5 py-[60px] text-center text-sub">{error || '게시글을 찾을 수 없어요.'}</div>
      </div>
    );
  }

  const isPostMine = state.user?.id === post.userId;
  const journalImage = journal ? representativeImage(journal) : null;

  return (
    <div className="container">
      <button
        type="button"
        onClick={() => router.back()}
        className="cursor-pointer rounded-[10px] border-[1.5px] border-line bg-white px-3 py-2 text-sm font-semibold text-sub hover:bg-brand-soft hover:text-brand-dark"
      >
        ← 목록으로
      </button>

      <div className="mt-4 rounded-[20px] bg-white p-6 shadow-card sm:p-7">
        <div className="mb-2.5 flex items-center gap-2">
          <span className={`text-sm font-extrabold ${CATEGORY_TEXT[post.category]}`}>
            [{CATEGORY_LABEL[post.category]}]
          </span>
        </div>
        <h1 className="mb-3 text-[22px] font-extrabold leading-snug">{post.title}</h1>
        <div className="mb-5 flex flex-wrap items-center gap-x-3 gap-y-1 border-b border-[#f0f1ea] pb-4 text-[13px] text-faint">
          <span className="font-bold text-sub">{post.nickname}</span>
          <span>{formatDate(post.createdAt)}</span>
          <span className="ml-auto flex items-center gap-3">
            <span>조회 {post.viewCount}</span>
            <span>좋아요 {likeCount}</span>
            <span>댓글 {comments.length}</span>
          </span>
        </div>

        {post.journalId && (
          <Link
            href={`/journals/${post.journalId}?viaBoardPost=${post.id}`}
            className="mb-5 flex gap-4 rounded-[16px] bg-[#F8FAF3] p-4 text-ink hover:text-ink"
          >
            <div className="flex h-[104px] w-[104px] shrink-0 items-center justify-center overflow-hidden rounded-[13px] bg-brand-soft text-[46px]">
              {journalImage ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img src={journalImage} alt="" className="h-full w-full object-cover" />
              ) : (
                '🌿'
              )}
            </div>
            <div className="min-w-0 flex-1">
              {journal ? (
                <>
                  <div className="mb-1 flex flex-wrap items-center gap-1.5">
                    <span className="rounded-full bg-brand-soft px-[9px] py-[3px] text-xs font-extrabold text-brand-dark">
                      {journal.plantProfileNickname}
                    </span>
                    <span className="ml-auto text-xs text-faint">{formatDate(journal.writtenDate)}</span>
                  </div>
                  <p className="line-clamp-3 text-[13.5px] leading-[1.55] text-[#4a5647]">{journal.content}</p>
                </>
              ) : (
                <div className="text-[13.5px] text-sub">연동된 성장 일지</div>
              )}
              <div className="mt-1.5 flex items-center gap-1 text-xs font-bold text-brand-dark">
                일지 보기 <span className="material-symbols-outlined text-[15px]">chevron_right</span>
              </div>
            </div>
          </Link>
        )}

        {post.imageUrls.length > 0 && (
          <div className="mb-5 flex flex-wrap gap-2.5">
            {post.imageUrls.map((url) => (
              <a key={url} href={resolveImageUrl(url)} target="_blank" rel="noopener noreferrer">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src={resolveImageUrl(url)}
                  alt=""
                  className="max-h-[480px] max-w-full cursor-pointer rounded-[16px]"
                />
              </a>
            ))}
          </div>
        )}

        <p className="mb-6 whitespace-pre-wrap text-[15px] leading-[1.75] text-ink">{post.content}</p>

        <div className="flex flex-wrap items-center gap-2.5">
          <button
            type="button"
            onClick={toggleLike}
            disabled={likePending}
            className={`flex cursor-pointer items-center gap-1.5 rounded-[11px] border-[1.5px] px-[18px] py-[11px] font-bold disabled:cursor-not-allowed disabled:opacity-60 ${
              liked ? 'border-[#e8bdad] bg-[#FBF3EF] text-[#b5502f]' : 'border-line bg-white text-sub'
            }`}
          >
            <span className="material-symbols-outlined text-[17px]">favorite</span> 좋아요 {likeCount}
          </button>
          {isPostMine ? (
            <>
              <Link
                href={`/board/${post.id}/edit`}
                className="flex cursor-pointer items-center gap-1.5 rounded-[11px] border-[1.5px] border-line bg-white px-4 py-[11px] font-bold text-sub hover:text-sub"
              >
                <span className="material-symbols-outlined text-[17px]">edit</span> 수정
              </Link>
              <button
                type="button"
                onClick={removePost}
                className="cursor-pointer rounded-[11px] border-[1.5px] border-[#e8bdad] bg-white px-4 py-[11px] font-bold text-[#b5502f]"
              >
                <span className="material-symbols-outlined text-[17px]">delete</span> 삭제
              </button>
            </>
          ) : (
            <button
              type="button"
              onClick={() => setReportingPost(true)}
              className="cursor-pointer rounded-[11px] border-[1.5px] border-line bg-white px-4 py-[11px] font-bold text-sub"
            >
              <span className="material-symbols-outlined text-[17px]">flag</span> 신고
            </button>
          )}
        </div>
      </div>

      <div className="mt-5 rounded-[20px] bg-white p-6 shadow-card sm:p-7">
        <h2 className="mb-4 text-[17px] font-extrabold">댓글 {comments.length}개</h2>

        {state.accessToken ? (
          <div className="mb-5 flex gap-2.5">
            <input
              value={commentDraft}
              onChange={(e) => setCommentDraft(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && submitComment()}
              placeholder="댓글을 남겨보세요"
              className="flex-1 rounded-[11px] border-[1.5px] border-line px-3.5 py-[11px] text-[14px] outline-none"
            />
            <button
              type="button"
              onClick={submitComment}
              className="shrink-0 cursor-pointer rounded-[11px] bg-brand px-5 py-[11px] font-bold text-white"
            >
              등록
            </button>
          </div>
        ) : (
          <div className="mb-5 rounded-[11px] bg-[#F8FAF3] px-4 py-3 text-sm text-sub">
            댓글을 남기려면 로그인해 주세요.
          </div>
        )}

        {comments.length === 0 ? (
          <div className="py-8 text-center text-sm text-sub">아직 댓글이 없어요. 첫 댓글을 남겨보세요!</div>
        ) : (
          <div className="flex flex-col divide-y divide-[#f0f1ea]">{roots.map((c) => renderComment(c, 0))}</div>
        )}
      </div>

      {reportingPost && (
        <div
          onClick={() => setReportingPost(false)}
          className="fixed inset-0 z-[60] flex items-center justify-center bg-[rgba(46,54,42,.4)] p-5"
        >
          <div onClick={(e) => e.stopPropagation()} className="w-full max-w-[380px] animate-pop rounded-[20px] bg-white p-6">
            <h3 className="mb-1 text-[18px] font-extrabold">게시글 신고하기</h3>
            <p className="mb-5 text-[13px] text-sub">검토 후 조치할게요. 바로 처리되지는 않아요.</p>
            <div className="flex gap-2.5">
              <button
                type="button"
                onClick={submitPostReport}
                className="flex-1 cursor-pointer rounded-xl bg-brand p-[12px] font-extrabold text-white"
              >
                신고 접수
              </button>
              <button
                type="button"
                onClick={() => setReportingPost(false)}
                className="cursor-pointer rounded-xl border-[1.5px] border-line bg-white px-5 py-[12px] font-bold text-sub"
              >
                취소
              </button>
            </div>
          </div>
        </div>
      )}

      {reportingComment != null && (
        <div
          onClick={() => setReportingComment(null)}
          className="fixed inset-0 z-[60] flex items-center justify-center bg-[rgba(46,54,42,.4)] p-5"
        >
          <div onClick={(e) => e.stopPropagation()} className="w-full max-w-[380px] animate-pop rounded-[20px] bg-white p-6">
            <h3 className="mb-1 text-[18px] font-extrabold">댓글 신고하기</h3>
            <p className="mb-5 text-[13px] text-sub">검토 후 조치할게요. 바로 처리되지는 않아요.</p>
            <div className="flex gap-2.5">
              <button
                type="button"
                onClick={submitCommentReport}
                className="flex-1 cursor-pointer rounded-xl bg-brand p-[12px] font-extrabold text-white"
              >
                신고 접수
              </button>
              <button
                type="button"
                onClick={() => setReportingComment(null)}
                className="cursor-pointer rounded-xl border-[1.5px] border-line bg-white px-5 py-[12px] font-bold text-sub"
              >
                취소
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
