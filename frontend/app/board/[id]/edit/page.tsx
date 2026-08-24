'use client';
import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useStore } from '@/lib/store';
import { useUI } from '@/lib/ui';
import { ApiError, resolveImageUrl } from '@/lib/api';
import {
  BoardCategory,
  BoardPostData,
  deleteBoardImage,
  getBoardPost,
  updateBoardPost,
  uploadBoardImage,
} from '@/lib/board-api';

const TITLE_MAX = 100;
const CONTENT_MAX = 2000;
const MAX_IMAGES = 1;
const MAX_IMAGE_SIZE = 5 * 1024 * 1024;
const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

const CATEGORY_LABEL: Record<BoardCategory, string> = {
  NOTICE: '공지사항',
  FREE: '자유게시판',
  PLANT_QNA: '식물 Q&A',
};

export default function EditBoardPostPage({ params }: { params: { id: string } }) {
  const router = useRouter();
  const { state, hydrated } = useStore();
  const { showToast } = useUI();
  const postId = Number(params.id);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [post, setPost] = useState<BoardPostData | null>(null);
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [imageUrls, setImageUrls] = useState<string[]>([]);
  const [uploadingImage, setUploadingImage] = useState(false);

  useEffect(() => {
    if (!hydrated || Number.isNaN(postId)) return;
    const controller = new AbortController();
    setLoading(true);
    setError('');

    getBoardPost(postId, state.accessToken, controller.signal)
      .then((data) => {
        if (state.user?.id !== data.userId) {
          setError('본인이 작성한 글만 수정할 수 있어요.');
          setPost(null);
          return;
        }
        setPost(data);
        setTitle(data.title);
        setContent(data.content);
        setImageUrls(data.imageUrls);
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
  }, [hydrated, postId, state.accessToken, state.user?.id]);

  const pickImages = async (files: FileList | null) => {
    if (!files || files.length === 0 || !state.accessToken) return;
    const accessToken = state.accessToken;
    const remaining = MAX_IMAGES - imageUrls.length;
    if (remaining <= 0) return showToast(`이미지는 최대 ${MAX_IMAGES}장까지 첨부할 수 있어요.`, 'err');

    const files_ = Array.from(files).slice(0, remaining);
    setUploadingImage(true);
    try {
      for (const file of files_) {
        if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
          showToast('jpg, png, webp 형식만 가능해요.', 'err');
          continue;
        }
        if (file.size > MAX_IMAGE_SIZE) {
          showToast('5MB 이하 사진만 올릴 수 있어요.', 'err');
          continue;
        }
        const uploaded = await uploadBoardImage(file, accessToken);
        setImageUrls((prev) => [...prev, uploaded.imageUrl]);
      }
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError ? requestError.message : '이미지 업로드에 실패했어요.',
        'err',
      );
    } finally {
      setUploadingImage(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const removeImage = (url: string) => {
    setImageUrls((prev) => prev.filter((u) => u !== url));
    if (state.accessToken) deleteBoardImage(url, state.accessToken).catch(() => {});
  };

  const submit = async () => {
    if (!state.accessToken || !post) return;
    if (!title.trim()) return showToast('제목을 입력해 주세요.', 'err');
    if (!content.trim()) return showToast('내용을 입력해 주세요.', 'err');

    setSubmitting(true);
    try {
      await updateBoardPost(
        post.id,
        { title: title.trim(), content: content.trim(), imageUrls },
        state.accessToken,
      );
      showToast('게시글을 수정했어요.');
      router.push(`/board/${post.id}`);
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError ? requestError.message : '게시글 수정에 실패했어요. 잠시 후 다시 시도해 주세요.',
        'err',
      );
    } finally {
      setSubmitting(false);
    }
  };

  if (!hydrated || loading) {
    return (
      <div className="container">
        <div className="px-5 py-[60px] text-center text-sub">게시글을 불러오고 있어요</div>
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
          ← 뒤로
        </button>
        <div className="mt-4 px-5 py-[60px] text-center text-sub">{error || '게시글을 찾을 수 없어요.'}</div>
      </div>
    );
  }

  return (
    <div className="container">
      <button
        type="button"
        onClick={() => router.back()}
        className="cursor-pointer rounded-[10px] border-[1.5px] border-line bg-white px-3 py-2 text-sm font-semibold text-sub hover:bg-brand-soft hover:text-brand-dark"
      >
        ← 뒤로
      </button>
      <h1 className="mb-1 mt-3.5 text-[26px] font-extrabold">게시글 수정</h1>
      <p className="mb-[22px] text-[14.5px] text-sub">제목과 내용만 수정할 수 있어요. 카테고리는 바꿀 수 없어요.</p>

      <div className="max-w-[640px] rounded-[20px] bg-white p-6 shadow-card">
        <div className="mb-2.5 font-extrabold">카테고리</div>
        <div className="mb-[22px] inline-block rounded-full border-[1.5px] border-line bg-[#f9faf6] px-[15px] py-2 text-sm font-bold text-[#a9b3a0]">
          {CATEGORY_LABEL[post.category]}
        </div>

        <div className="mb-[5px] font-extrabold">사진 (선택, 최대 {MAX_IMAGES}장)</div>
        <input
          ref={fileInputRef}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          onChange={(e) => pickImages(e.target.files)}
          className="hidden"
        />
        <div className="mb-[22px] flex flex-wrap gap-2.5">
          {imageUrls.map((url) => (
            <div key={url} className="relative h-20 w-20 overflow-hidden rounded-[12px] border-[1.5px] border-line">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={resolveImageUrl(url)} alt="" className="h-full w-full object-cover" />
              <button
                type="button"
                onClick={() => removeImage(url)}
                className="absolute right-1 top-1 flex h-5 w-5 cursor-pointer items-center justify-center rounded-full bg-black/60 text-xs text-white"
              >
                ✕
              </button>
            </div>
          ))}
          {imageUrls.length < MAX_IMAGES && (
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              disabled={uploadingImage}
              className="flex h-20 w-20 cursor-pointer flex-col items-center justify-center gap-1 rounded-[12px] border-[1.5px] border-dashed border-line bg-[#f9faf6] text-[#a9b3a0] disabled:opacity-60"
            >
              <span className="material-symbols-outlined text-2xl">add_photo_alternate</span>
              <span className="text-[11px] font-bold">{uploadingImage ? '업로드 중...' : '사진 추가'}</span>
            </button>
          )}
        </div>

        <div className="mb-2.5 font-extrabold">제목</div>
        <input
          value={title}
          onChange={(e) => setTitle(e.target.value.slice(0, TITLE_MAX))}
          placeholder="제목을 입력해 주세요"
          maxLength={TITLE_MAX}
          className="mb-[5px] w-full rounded-[14px] border-[1.5px] border-line p-3.5 text-[15px] outline-none"
        />
        <div className="mb-[22px] text-right text-xs text-faint">{title.length} / {TITLE_MAX}</div>

        <div className="mb-2.5 font-extrabold">내용</div>
        <textarea
          value={content}
          onChange={(e) => setContent(e.target.value.slice(0, CONTENT_MAX))}
          placeholder="내용을 입력해 주세요"
          maxLength={CONTENT_MAX}
          className="min-h-[220px] w-full resize-y rounded-[14px] border-[1.5px] border-line p-3.5 text-[15px] leading-[1.6] outline-none"
        />
        <div className="mt-[5px] text-right text-xs text-faint">{content.length} / {CONTENT_MAX}</div>

        <button
          type="button"
          onClick={submit}
          disabled={submitting}
          className="mt-3 w-full cursor-pointer rounded-[14px] bg-brand p-[15px] text-base font-extrabold text-white disabled:opacity-60"
        >
          {submitting ? '저장 중...' : '수정 완료'}
        </button>
      </div>
    </div>
  );
}
