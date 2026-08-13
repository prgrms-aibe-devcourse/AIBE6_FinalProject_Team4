'use client';
import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { ApiError, resolveImageUrl } from '@/lib/api';
import { useStore } from '@/lib/store';
import { useUI } from '@/lib/ui';
import { deleteJournal, deleteJournalImage, getJournal, PlantJournalData, updateJournal, uploadJournalImage } from '@/lib/journal-api';
import { formatDate } from '@/lib/format';
import { createReport } from '@/lib/report-api';
import JournalImageAnalysisPanel from '@/features/journal/JournalImageAnalysisPanel';

const REASONS = [['spam', '스팸/광고'], ['inappropriate', '부적절한 콘텐츠'], ['stolen', '사진 도용'], ['etc', '기타']];
const MAX_SIZE = 5 * 1024 * 1024;
const MAX_PHOTOS = 3;
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

// Raw (server-relative) image — pass this back to updateJournal as-is, never resolveImageUrl()
// it first, or we'd bake an absolute host into the stored row again.
function representativeJournalImage(journal: PlantJournalData) {
  return journal.images.find((img) => img.representative) || journal.images[0] || null;
}

// Display-ready version for <img src> — same data, resolved to an absolute URL.
function representativeImage(journal: PlantJournalData): string | null {
  const url = representativeJournalImage(journal)?.imageUrl || null;
  return url ? resolveImageUrl(url) : null;
}

// 수정 화면의 사진 한 장 — 기존에 저장돼 있던 사진(재업로드 없이 그대로 재사용)이거나
// 새로 첨부한 사진(저장 시 업로드 필요) 둘 중 하나다.
type EditPhoto =
  | { kind: 'existing'; imageUrl: string; imageHash: string }
  | { kind: 'new'; file: File; preview: string };

function editPhotoSrc(photo: EditPhoto): string {
  return photo.kind === 'existing' ? resolveImageUrl(photo.imageUrl) : photo.preview;
}

// 'new' 사진의 blob 미리보기 URL만 해제한다 ('existing'은 서버 URL이라 해제 대상이 아니다).
function revokeNewPhotoBlobs(photos: EditPhoto[]) {
  photos.forEach((photo) => {
    if (photo.kind === 'new') URL.revokeObjectURL(photo.preview);
  });
}

export default function JournalDetail({ params }: { params: { id: string } }) {
  const { state, hydrated } = useStore();
  const { showToast, askConfirm } = useUI();
  const router = useRouter();
  const id = Number(params.id);

  const [journal, setJournal] = useState<PlantJournalData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reportOpen, setReportOpen] = useState(false);
  const [reason, setReason] = useState<string | null>(null);
  const [submittingReport, setSubmittingReport] = useState(false);
  const [editing, setEditing] = useState(false);
  const [editContent, setEditContent] = useState('');
  const [editPhotos, setEditPhotos] = useState<EditPhoto[]>([]);
  const [editRepresentativeIndex, setEditRepresentativeIndex] = useState(0);
  const [saving, setSaving] = useState(false);
  const [viewIndex, setViewIndex] = useState(0);
  const editFileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!hydrated || !state.accessToken) return;
    const accessToken = state.accessToken;
    const controller = new AbortController();
    setLoading(true);
    setError('');

    getJournal(id, accessToken, controller.signal)
      .then((data) => setJournal(data))
      .catch((requestError) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return;
        setJournal(null);
        setError(
          requestError instanceof ApiError
            ? requestError.message
            : '일지를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.',
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [hydrated, state.accessToken, id]);

  useEffect(() => {
    if (!journal) return;
    const repIndex = journal.images.findIndex((img) => img.representative);
    setViewIndex(repIndex >= 0 ? repIndex : 0);
  }, [journal]);

  const confirmDelete = () => {
    if (!journal || !state.accessToken) return;
    const accessToken = state.accessToken;
    askConfirm({
      icon: 'delete', title: '이 일지를 삭제할까요?', ok: '삭제하기', danger: true,
      body: '삭제한 일지는 다시 볼 수 없어요. 정말 삭제할까요?',
      onOk: async () => {
        try {
          await deleteJournal(journal.id, accessToken);
          showToast('일지를 삭제했어요.');
          router.push('/journals');
        } catch (requestError) {
          showToast(
            requestError instanceof ApiError ? requestError.message : '삭제에 실패했어요. 잠시 후 다시 시도해 주세요.',
            'err',
          );
        }
      },
    });
  };

  const openEdit = () => {
    if (!journal) return;
    revokeNewPhotoBlobs(editPhotos);
    setEditContent(journal.content);
    setEditPhotos(journal.images.map((img) => ({ kind: 'existing', imageUrl: img.imageUrl, imageHash: img.imageHash })));
    const repIndex = journal.images.findIndex((img) => img.representative);
    setEditRepresentativeIndex(repIndex >= 0 ? repIndex : 0);
    setEditing(true);
  };

  const closeEdit = () => {
    revokeNewPhotoBlobs(editPhotos);
    setEditing(false);
  };

  const pickEditPhoto = (file: File | null) => {
    if (!file) return;
    if (!ALLOWED_TYPES.includes(file.type)) return showToast('jpg, png, webp 형식만 가능해요.', 'err');
    if (file.size > MAX_SIZE) return showToast('5MB 이하 사진만 올릴 수 있어요.', 'err');
    if (editPhotos.length >= MAX_PHOTOS) return showToast(`사진은 최대 ${MAX_PHOTOS}장까지 첨부할 수 있어요.`, 'err');
    setEditPhotos((prev) => [...prev, { kind: 'new', file, preview: URL.createObjectURL(file) }]);
    if (editFileInputRef.current) editFileInputRef.current.value = '';
  };

  const removeEditPhoto = (index: number) => {
    setEditPhotos((prev) => {
      const target = prev[index];
      if (target.kind === 'new') URL.revokeObjectURL(target.preview);
      return prev.filter((_, i) => i !== index);
    });
    setEditRepresentativeIndex((prev) => {
      if (index === prev) return 0;
      return index < prev ? prev - 1 : prev;
    });
  };

  const saveEdit = async () => {
    if (!journal || !state.accessToken) return;
    if (editPhotos.length === 0) return showToast('사진을 최소 한 장은 남겨주세요.', 'err');
    const accessToken = state.accessToken;

    setSaving(true);
    const newlyUploaded: string[] = [];
    try {
      const uploadedImages = [];
      for (const photo of editPhotos) {
        if (photo.kind === 'existing') {
          uploadedImages.push({ imageUrl: photo.imageUrl, imageHash: photo.imageHash });
        } else {
          const uploaded = await uploadJournalImage(photo.file, accessToken);
          newlyUploaded.push(uploaded.imageUrl);
          uploadedImages.push(uploaded);
        }
      }

      let updated;
      try {
        updated = await updateJournal(
          journal.id,
          {
            content: editContent,
            images: uploadedImages.map((img, i) => ({ ...img, representative: i === editRepresentativeIndex })),
          },
          accessToken,
        );
      } catch (updateError) {
        newlyUploaded.forEach((imageUrl) => deleteJournalImage(imageUrl, accessToken).catch(() => {}));
        throw updateError;
      }
      // 새로 업로드한 사진은 이제 서버에 반영됐으니 로컬 blob 미리보기는 더 이상 필요 없다.
      revokeNewPhotoBlobs(editPhotos);
      setJournal(updated);
      setEditing(false);
      showToast('일지를 수정했어요 🌿');
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError ? requestError.message : '수정에 실패했어요. 잠시 후 다시 시도해 주세요.',
        'err',
      );
    } finally {
      setSaving(false);
    }
  };

  const submitReport = async () => {
    if (!journal) return;
    if (!reason) return showToast('신고 사유를 골라주세요.', 'err');
    if (!state.accessToken) return showToast('로그인이 필요해요.', 'err');

    const label = REASONS.find(([k]) => k === reason)?.[1] ?? reason;
    setSubmittingReport(true);
    try {
      await createReport({ targetType: 'JOURNAL', targetId: journal.id, reason: label }, state.accessToken);
      setReportOpen(false); setReason(null);
      showToast('신고가 접수됐어요. 검토 후 조치할게요. 알려주셔서 고마워요.');
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError ? requestError.message : '신고 접수에 실패했어요. 잠시 후 다시 시도해 주세요.',
        'err',
      );
    } finally {
      setSubmittingReport(false);
    }
  };

  if (loading) {
    return (
      <div className="container">
        <div className="px-5 py-[60px] text-center text-sub">일지를 불러오고 있어요 🌿</div>
      </div>
    );
  }

  if (error || !journal) {
    return (
      <div className="container">
        <button type="button" onClick={() => router.back()} className="cursor-pointer rounded-[10px] border-[1.5px] border-line bg-white px-3 py-2 text-sm font-semibold text-sub hover:bg-brand-soft hover:text-brand-dark">← 뒤로</button>
        <div className="mt-4 px-5 py-[60px] text-center text-sub">{error || '일지를 찾을 수 없어요.'}</div>
      </div>
    );
  }

  if (editing) {
    return (
      <div className="container">
        <button type="button" onClick={closeEdit} className="cursor-pointer rounded-[10px] border-[1.5px] border-line bg-white px-3 py-2 text-sm font-semibold text-sub hover:bg-brand-soft hover:text-brand-dark">← 일지 상세</button>
        <h1 className="mb-[18px] mt-3.5 text-2xl font-extrabold">일지 수정</h1>
        <div className="max-w-[640px] rounded-[20px] bg-white p-6 shadow-card">
          <div className="mb-2 text-[13px] text-faint">
            <span>작성일 {formatDate(journal.writtenDate)}</span> · <span>{journal.plantProfileNickname}</span>
          </div>
          <div className="mb-2.5 text-[12.5px] text-[#a9b3a0]">최대 {MAX_PHOTOS}장, 탭해서 대표 사진 선택</div>
          <input
            ref={editFileInputRef}
            type="file"
            accept="image/jpeg,image/png,image/webp"
            onChange={(e) => pickEditPhoto(e.target.files?.[0] ?? null)}
            className="hidden"
          />
          <div className="mb-5 flex flex-wrap gap-2.5">
            {editPhotos.map((photo, i) => (
              <button
                key={photo.kind === 'existing' ? photo.imageUrl : photo.preview}
                type="button"
                onClick={() => setEditRepresentativeIndex(i)}
                className={`relative h-[100px] w-[100px] cursor-pointer overflow-hidden rounded-[14px] border-[2.5px] ${
                  i === editRepresentativeIndex ? 'border-brand' : 'border-transparent'
                }`}
              >
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img src={editPhotoSrc(photo)} alt="" className="h-full w-full object-cover" />
                {i === editRepresentativeIndex && (
                  <span className="absolute left-1 top-1 flex h-5 w-5 items-center justify-center rounded-full bg-brand text-[11px] text-white">★</span>
                )}
                <span
                  role="button"
                  tabIndex={0}
                  onClick={(e) => {
                    e.stopPropagation();
                    removeEditPhoto(i);
                  }}
                  className="absolute right-1 top-1 flex h-5 w-5 cursor-pointer items-center justify-center rounded-full bg-black/55 text-white"
                >
                  <span className="material-symbols-outlined text-[14px]">close</span>
                </span>
              </button>
            ))}
            {editPhotos.length < MAX_PHOTOS && (
              <button
                type="button"
                onClick={() => editFileInputRef.current?.click()}
                className="flex h-[100px] w-[100px] cursor-pointer flex-col items-center justify-center gap-1 rounded-[14px] border-[1.5px] border-dashed border-line bg-[#f9faf6] text-[#a9b3a0]"
              >
                <span className="material-symbols-outlined text-2xl">photo_camera</span>
                <span className="text-[11px] font-bold">사진 추가</span>
              </button>
            )}
          </div>
          {editPhotos.length === 0 && (
            <div className="mb-3 text-[12.5px] font-bold text-[#e5533b]">사진을 최소 한 장은 남겨주세요.</div>
          )}
          <textarea
            value={editContent}
            onChange={(e) => setEditContent(e.target.value)}
            maxLength={2000}
            className="min-h-[130px] w-full resize-y rounded-[14px] border-[1.5px] border-line p-3.5 text-[15px] leading-[1.6] outline-none"
          />
          <div className="mt-4 flex gap-2.5">
            <button
              type="button"
              onClick={saveEdit}
              disabled={saving || editPhotos.length === 0}
              className="flex-1 cursor-pointer rounded-[13px] bg-brand p-3.5 font-extrabold text-white disabled:opacity-60"
            >
              {saving ? '저장 중...' : '저장하기'}
            </button>
            <button type="button" onClick={closeEdit} className="cursor-pointer rounded-[13px] border-[1.5px] border-line bg-white px-[22px] py-3.5 font-bold text-sub">
              취소
            </button>
          </div>
        </div>
      </div>
    );
  }

  const image = representativeImage(journal);
  const activeImage = journal.images[viewIndex] ? resolveImageUrl(journal.images[viewIndex].imageUrl) : image;

  return (
    <div className="container">
      <button type="button" onClick={() => router.back()} className="cursor-pointer rounded-[10px] border-[1.5px] border-line bg-white px-3 py-2 text-sm font-semibold text-sub hover:bg-brand-soft hover:text-brand-dark">← 뒤로</button>
      <div className="mt-4 grid items-start gap-6 [grid-template-columns:repeat(auto-fit,minmax(280px,1fr))]">
        <div>
          <div className="flex aspect-square items-center justify-center overflow-hidden rounded-[20px] bg-brand-soft text-[150px]">
            {activeImage ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={activeImage} alt="" className="h-full w-full object-cover" />
            ) : (
              '🌿'
            )}
          </div>
          {journal.images.length > 1 && (
            <div className="mt-2.5 flex gap-2">
              {journal.images.map((img, i) => (
                <button
                  key={img.imageUrl}
                  type="button"
                  onClick={() => setViewIndex(i)}
                  className={`h-[60px] w-[60px] cursor-pointer overflow-hidden rounded-[10px] border-[2px] ${
                    i === viewIndex ? 'border-brand' : 'border-transparent'
                  }`}
                >
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img src={resolveImageUrl(img.imageUrl)} alt="" className="h-full w-full object-cover" />
                </button>
              ))}
            </div>
          )}
        </div>
        <div>
          <div className="mb-3 flex items-center gap-2">
            <Link href={`/plants/${journal.plantProfileId}`} className="rounded-full bg-brand-soft px-3 py-[5px] text-[13px] font-extrabold text-brand-dark">
              <span className="material-symbols-outlined text-sm">potted_plant</span> {journal.plantProfileNickname}
            </Link>
          </div>
          <div className="mb-3.5 text-sm text-faint">
            <span className="material-symbols-outlined text-[15px]">calendar_month</span> {formatDate(journal.writtenDate)}
          </div>
          <p className="mb-6 whitespace-pre-wrap text-base leading-[1.75] text-ink">{journal.content}</p>
          <div className="flex flex-wrap gap-2.5">
            <button type="button" onClick={openEdit} className="cursor-pointer rounded-[11px] bg-brand-soft px-[18px] py-[11px] font-bold text-brand-dark">
              <span className="material-symbols-outlined text-[17px]">edit</span> 수정
            </button>
            <button type="button" onClick={confirmDelete} className="cursor-pointer rounded-[11px] border-[1.5px] border-[#e8bdad] bg-white px-[18px] py-[11px] font-bold text-[#b5502f]">
              <span className="material-symbols-outlined text-[17px]">delete</span> 삭제
            </button>
            <button type="button" onClick={() => setReportOpen(true)} className="cursor-pointer rounded-[11px] border-[1.5px] border-line bg-white px-4 py-[11px] font-bold text-sub">
              <span className="material-symbols-outlined text-[17px]">flag</span> 신고
            </button>
          </div>
        </div>
      </div>

      {state.accessToken && (
        <JournalImageAnalysisPanel
          journalId={journal.id}
          images={journal.images}
          activeIndex={viewIndex}
          accessToken={state.accessToken}
        />
      )}

      {reportOpen && (
        <div onClick={() => setReportOpen(false)} className="fixed inset-0 z-[60] flex items-center justify-center bg-[rgba(46,54,42,.4)] p-5">
          <div onClick={(e) => e.stopPropagation()} className="w-full max-w-[420px] animate-pop rounded-[20px] bg-white p-6">
            <h3 className="mb-1 text-[19px] font-extrabold">일지 신고하기</h3>
            <p className="mb-4 text-[13.5px] text-sub">검토 후 조치할게요. 바로 처리되지는 않아요.</p>
            <div className="mb-4 flex items-center gap-2.5 rounded-xl bg-[#f6f7f1] p-2.5">
              <div className="flex h-11 w-11 items-center justify-center overflow-hidden rounded-[10px] bg-brand-soft text-[22px]">
                {image ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={image} alt="" className="h-full w-full object-cover" />
                ) : (
                  '🌿'
                )}
              </div>
              <div className="text-[13.5px] text-[#6d7a68]">{journal.plantProfileNickname} · {formatDate(journal.writtenDate)}</div>
            </div>
            <div className="mb-4 flex flex-col gap-2">
              {REASONS.map(([k, label]) => (
                <button
                  key={k}
                  type="button"
                  onClick={() => setReason(k)}
                  className={`cursor-pointer rounded-[11px] border-[1.5px] px-3.5 py-[11px] text-left font-semibold ${
                    reason === k ? 'border-brand bg-[#F3F8EA] text-ink' : 'border-[#eceee5] bg-white text-[#6d7a68]'
                  }`}
                >
                  {label}
                </button>
              ))}
            </div>
            <div className="flex gap-2.5">
              <button
                type="button"
                onClick={submitReport}
                disabled={submittingReport}
                className="flex-1 cursor-pointer rounded-xl bg-brand p-[13px] font-extrabold text-white disabled:cursor-not-allowed disabled:opacity-60"
              >
                {submittingReport ? '접수 중...' : '신고 접수'}
              </button>
              <button type="button" onClick={() => setReportOpen(false)} className="cursor-pointer rounded-xl border-[1.5px] border-line bg-white px-5 py-[13px] font-bold text-sub">닫기</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
