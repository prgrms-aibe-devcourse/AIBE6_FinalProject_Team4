'use client';
import { useState } from 'react';
import Link from 'next/link';
import { useStore } from '@/lib/store';
import { useUI } from '@/lib/ui';
import { JOURNALS } from '@/lib/data';

const N = 30;
const REASONS = [['spam', '스팸/광고'], ['inappropriate', '부적절한 콘텐츠'], ['stolen', '사진 도용'], ['etc', '기타']];

export default function JournalDetail({ params }: { params: { id: string } }) {
  const { set, spend } = useStore();
  const { showToast, askConfirm } = useUI();
  const id = Number(params.id);
  const [journal, setJournal] = useState(JOURNALS.find((j) => j.id === id) || JOURNALS[0]);
  const [editing, setEditing] = useState(false);
  const [content, setContent] = useState(journal.content);
  const [reportOpen, setReportOpen] = useState(false);
  const [reason, setReason] = useState<string | null>(null);
  const [deleted, setDeleted] = useState(false);

  const confirmDelete = () => {
    if (journal.rewarded && journal.today) {
      askConfirm({ icon: 'warning', title: '오늘 받은 포인트가 회수돼요', ok: '삭제하기', danger: true,
        body: `오늘 받은 ${N} 포인트가 함께 회수돼요. 그래도 삭제할까요?`,
        onOk: () => { spend(N); set({ rewardedToday: false, wroteToday: false }); setDeleted(true); showToast(`일지를 삭제하고 ${N} 포인트를 회수했어요.`); } });
    } else {
      askConfirm({ icon: 'delete', title: '이 일지를 삭제할까요?', ok: '삭제하기', danger: true,
        body: '삭제한 일지는 다시 볼 수 없어요. 정말 삭제할까요?',
        onOk: () => { setDeleted(true); showToast('일지를 삭제했어요.'); } });
    }
  };

  const submitReport = () => {
    if (!reason) return showToast('신고 사유를 골라주세요.', 'err');
    setReportOpen(false); setReason(null);
    showToast('신고가 접수됐어요. 검토 후 조치할게요. 알려주셔서 고마워요.');
  };

  if (deleted) {
    return (
      <div className="container pt-[60px] text-center">
        <div className="text-[56px]">🌿</div>
        <p className="mb-5 mt-4 text-[#6d7a68]">일지가 삭제됐어요.</p>
        <Link href="/journals" className="rounded-xl bg-brand px-[22px] py-3 font-bold text-white hover:text-white">일지 목록으로</Link>
      </div>
    );
  }

  if (editing) {
    return (
      <div className="container">
        <button type="button" onClick={() => setEditing(false)} className="cursor-pointer text-sm font-semibold text-sub">← 일지 상세</button>
        <h1 className="mb-[18px] mt-3.5 text-2xl font-extrabold">일지 수정</h1>
        <div className="max-w-[640px] rounded-[20px] bg-white p-6 shadow-card">
          <div className="mb-5 flex flex-wrap gap-4">
            <div className="flex h-[110px] w-[110px] flex-none items-center justify-center rounded-[14px] text-[52px]" style={{ background: journal.grad }}>{journal.emoji}</div>
            <div className="flex min-w-[180px] flex-1 flex-col justify-center gap-2">
              <button
                type="button"
                onClick={() => showToast('사진 교체 데모예요. 새 사진을 고르면 반영돼요 📷')}
                className="cursor-pointer self-start rounded-[11px] bg-brand-soft px-4 py-2.5 font-bold text-brand-dark"
              >
                <span className="material-symbols-outlined text-base">photo_camera</span> 사진 교체
              </button>
              <div className="text-[12.5px] text-[#a9b3a0]">사진은 꼭 있어야 해요. 지우려면 새 사진으로 바꿔주세요.</div>
              <div className="mt-1 flex gap-3.5 text-[13px] text-faint">
                <span><span className="material-symbols-outlined text-sm">lock</span> 작성일 {journal.date}</span>
                <span><span className="material-symbols-outlined text-sm">lock</span> {journal.plantNickname}</span>
              </div>
            </div>
          </div>
          <textarea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            maxLength={2000}
            className="min-h-[130px] w-full resize-y rounded-[14px] border-[1.5px] border-line p-3.5 text-[15px] leading-[1.6] outline-none"
          />
          <div className="mt-4 flex gap-2.5">
            <button
              type="button"
              onClick={() => { setJournal({ ...journal, content }); setEditing(false); showToast('일지를 수정했어요 🌿'); }}
              className="flex-1 cursor-pointer rounded-[13px] bg-brand p-3.5 font-extrabold text-white"
            >
              저장하기
            </button>
            <button type="button" onClick={() => setEditing(false)} className="cursor-pointer rounded-[13px] border-[1.5px] border-line bg-white px-[22px] py-3.5 font-bold text-sub">
              취소
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="container">
      <Link href="/journals" className="text-sm font-semibold text-sub">← 일지</Link>
      <div className="mt-4 grid items-start gap-6 [grid-template-columns:repeat(auto-fit,minmax(280px,1fr))]">
        <div className="flex aspect-square items-center justify-center overflow-hidden rounded-[20px] text-[150px]" style={{ background: journal.grad }}>{journal.emoji}</div>
        <div>
          <div className="mb-3 flex items-center gap-2">
            <Link href={`/plants/${journal.plantId}`} className="rounded-full bg-brand-soft px-3 py-[5px] text-[13px] font-extrabold text-brand-dark">
              <span className="material-symbols-outlined text-sm">potted_plant</span> {journal.plantNickname}
            </Link>
            {journal.rewarded && (
              <span className="rounded-full bg-gold-soft px-3 py-[5px] text-[13px] font-extrabold text-gold-text">이날의 포인트 지급 ✨</span>
            )}
          </div>
          <div className="mb-3.5 text-sm text-faint">
            <span className="material-symbols-outlined text-[15px]">calendar_month</span> {journal.date}
          </div>
          <p className="mb-6 whitespace-pre-wrap text-base leading-[1.75] text-ink">{journal.content}</p>
          <div className="flex flex-wrap gap-2.5">
            <button type="button" onClick={() => { setContent(journal.content); setEditing(true); }} className="cursor-pointer rounded-[11px] bg-brand-soft px-[18px] py-[11px] font-bold text-brand-dark">
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

      {reportOpen && (
        <div onClick={() => setReportOpen(false)} className="fixed inset-0 z-[60] flex items-center justify-center bg-[rgba(46,54,42,.4)] p-5">
          <div onClick={(e) => e.stopPropagation()} className="w-full max-w-[420px] animate-pop rounded-[20px] bg-white p-6">
            <h3 className="mb-1 text-[19px] font-extrabold">일지 신고하기</h3>
            <p className="mb-4 text-[13.5px] text-sub">검토 후 조치할게요. 바로 처리되지는 않아요.</p>
            <div className="mb-4 flex items-center gap-2.5 rounded-xl bg-[#f6f7f1] p-2.5">
              <div className="flex h-11 w-11 items-center justify-center rounded-[10px] text-[22px]" style={{ background: journal.grad }}>{journal.emoji}</div>
              <div className="text-[13.5px] text-[#6d7a68]">{journal.plantNickname} · {journal.date}</div>
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
              <button type="button" onClick={submitReport} className="flex-1 cursor-pointer rounded-xl bg-brand p-[13px] font-extrabold text-white">신고 접수</button>
              <button type="button" onClick={() => setReportOpen(false)} className="cursor-pointer rounded-xl border-[1.5px] border-line bg-white px-5 py-[13px] font-bold text-sub">닫기</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
