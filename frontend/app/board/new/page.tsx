'use client';
import { useEffect, useRef, useState, Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useStore } from '@/lib/store';
import { useUI } from '@/lib/ui';
import { ApiError, resolveImageUrl } from '@/lib/api';
import { BoardCategory, createBoardPost, deleteBoardImage, uploadBoardImage } from '@/lib/board-api';
import { getJournals, PlantJournalData } from '@/lib/journal-api';

const TITLE_MAX = 100;
const CONTENT_MAX = 2000;
const MAX_IMAGES = 1;
const MAX_IMAGE_SIZE = 5 * 1024 * 1024;
const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

const BASE_CATEGORIES: { key: BoardCategory; label: string }[] = [
  { key: 'FREE', label: '자유게시판' },
  { key: 'PLANT_QNA', label: '식물 Q&A' },
];

function NewBoardPostInner() {
  const router = useRouter();
  const params = useSearchParams();
  const preselectJournalId = params.get('journalId');
  const { state, hydrated } = useStore();
  const { showToast } = useUI();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const isAdmin = state.user?.role === 'ADMIN';
  const categories = isAdmin ? [{ key: 'NOTICE' as BoardCategory, label: '공지사항' }, ...BASE_CATEGORIES] : BASE_CATEGORIES;

  const [category, setCategory] = useState<BoardCategory>(preselectJournalId ? 'PLANT_QNA' : 'FREE');
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [journalId, setJournalId] = useState<number | null>(preselectJournalId ? Number(preselectJournalId) : null);
  const [journals, setJournals] = useState<PlantJournalData[]>([]);
  const [journalsLoading, setJournalsLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [imageUrls, setImageUrls] = useState<string[]>([]);
  const [uploadingImage, setUploadingImage] = useState(false);

  useEffect(() => {
    if (!hydrated || !state.accessToken || category !== 'PLANT_QNA') return;
    const accessToken = state.accessToken;
    const controller = new AbortController();
    setJournalsLoading(true);

    getJournals({ size: 50 }, accessToken, controller.signal)
      .then((page) => setJournals(page.content))
      .catch((requestError) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return;
        setJournals([]);
      })
      .finally(() => {
        if (!controller.signal.aborted) setJournalsLoading(false);
      });

    return () => controller.abort();
  }, [hydrated, state.accessToken, category]);

  const changeCategory = (next: BoardCategory) => {
    setCategory(next);
    if (next !== 'PLANT_QNA') setJournalId(null);
  };

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
    if (!state.accessToken) return showToast('로그인이 필요해요.', 'err');
    if (!title.trim()) return showToast('제목을 입력해 주세요.', 'err');
    if (!content.trim()) return showToast('내용을 입력해 주세요.', 'err');
    if (category === 'PLANT_QNA' && journalId === null) {
      return showToast('연동할 성장 일지를 선택해 주세요.', 'err');
    }

    setSubmitting(true);
    try {
      const post = await createBoardPost(
        {
          category,
          title: title.trim(),
          content: content.trim(),
          journalId: category === 'PLANT_QNA' ? journalId : null,
          imageUrls,
        },
        state.accessToken,
      );
      showToast('게시글을 등록했어요.');
      router.push(`/board/${post.id}`);
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError ? requestError.message : '게시글 등록에 실패했어요. 잠시 후 다시 시도해 주세요.',
        'err',
      );
    } finally {
      setSubmitting(false);
    }
  };

  if (hydrated && !state.accessToken) {
    return (
      <div className="container">
        <div className="px-5 py-[60px] text-center text-sub">글을 쓰려면 로그인해 주세요.</div>
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
      <h1 className="mb-1 mt-3.5 text-[26px] font-extrabold">게시글 작성</h1>
      <p className="mb-[22px] text-[14.5px] text-sub">다른 사람들과 식물 키우는 이야기를 나눠보세요.</p>

      <div className="max-w-[640px] rounded-[20px] bg-white p-6 shadow-card">
        <div className="mb-3 font-extrabold">카테고리</div>
        <div className="mb-[22px] flex flex-wrap gap-2">
          {categories.map((c) => (
            <button
              key={c.key}
              type="button"
              onClick={() => changeCategory(c.key)}
              className={`cursor-pointer rounded-full border-[1.5px] px-[15px] py-2 text-sm font-bold ${
                category === c.key ? 'border-brand bg-brand text-white' : 'border-line bg-white text-[#6d7a68]'
              }`}
            >
              {c.label}
            </button>
          ))}
        </div>

        {category === 'PLANT_QNA' && (
          <>
            <div className="mb-2.5 font-extrabold">연동할 성장 일지</div>
            {journalsLoading ? (
              <div className="mb-[22px] text-sm text-sub">일지 목록을 불러오고 있어요...</div>
            ) : journals.length === 0 ? (
              <div className="mb-[22px] text-sm text-sub">연동할 수 있는 성장 일지가 없어요. 먼저 일지를 작성해 주세요.</div>
            ) : (
              <div className="mb-[22px] flex max-h-[220px] flex-col gap-2 overflow-y-auto">
                {journals.map((j) => (
                  <button
                    key={j.id}
                    type="button"
                    onClick={() => setJournalId(j.id)}
                    className={`flex cursor-pointer items-center gap-3 rounded-[13px] border-2 p-2.5 text-left ${
                      journalId === j.id ? 'border-brand bg-[#F3F8EA]' : 'border-[#eceee5] bg-white hover:border-brand'
                    }`}
                  >
                    <span className="flex-1 min-w-0">
                      <span className="block font-bold">{j.plantProfileNickname}</span>
                      <span className="block truncate text-xs text-sub">{j.content}</span>
                    </span>
                    <span className="shrink-0 text-xs text-faint">{j.writtenDate}</span>
                  </button>
                ))}
              </div>
            )}
          </>
        )}

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
          {submitting ? '등록 중...' : '등록하기'}
        </button>
      </div>
    </div>
  );
}

export default function NewBoardPost() {
  return (
    <Suspense fallback={<div className="container" />}>
      <NewBoardPostInner />
    </Suspense>
  );
}
