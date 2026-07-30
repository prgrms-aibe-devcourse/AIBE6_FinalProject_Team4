'use client';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { ApiError } from '@/lib/api';
import { useStore } from '@/lib/store';
import { useUI } from '@/lib/ui';
import { deleteJournal, getJournal, PlantJournalData } from '@/lib/journal-api';
import { formatDate } from '@/lib/format';
import { createReport } from '@/lib/report-api';

const REASONS = [['spam', '스팸/광고'], ['inappropriate', '부적절한 콘텐츠'], ['stolen', '사진 도용'], ['etc', '기타']];

function representativeImage(journal: PlantJournalData): string | null {
  return journal.images.find((img) => img.representative)?.imageUrl || journal.images[0]?.imageUrl || null;
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
        <Link href="/journals" className="text-sm font-semibold text-sub">← 일지</Link>
        <div className="mt-4 px-5 py-[60px] text-center text-sub">{error || '일지를 찾을 수 없어요.'}</div>
      </div>
    );
  }

  const image = representativeImage(journal);

  return (
    <div className="container">
      <Link href="/journals" className="text-sm font-semibold text-sub">← 일지</Link>
      <div className="mt-4 grid items-start gap-6 [grid-template-columns:repeat(auto-fit,minmax(280px,1fr))]">
        <div className="flex aspect-square items-center justify-center overflow-hidden rounded-[20px] bg-brand-soft text-[150px]">
          {image ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={image} alt="" className="h-full w-full object-cover" />
          ) : (
            '🌿'
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
            <button type="button" onClick={confirmDelete} className="cursor-pointer rounded-[11px] border-[1.5px] border-[#e8bdad] bg-white px-[18px] py-[11px] font-bold text-[#b5502f]">
              <span className="material-symbols-outlined text-[17px]">delete</span> 삭제
            </button>
            <button type="button" onClick={() => setReportOpen(true)} className="cursor-pointer rounded-[11px] border-[1.5px] border-line bg-white px-4 py-[11px] font-bold text-sub">
              <span className="material-symbols-outlined text-[17px]">flag</span> 신고
            </button>
          </div>
        </div>
      </div>

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
