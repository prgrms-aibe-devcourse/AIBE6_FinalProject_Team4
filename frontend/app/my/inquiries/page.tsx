'use client';
import { useState } from 'react';
import { useUI } from '@/lib/ui';

const CAT: Record<string, string> = { PAYMENT: '결제', DELIVERY: '배송', ACCOUNT: '계정', ETC: '기타' };
const STAT: Record<string, [string, string]> = {
  PENDING: ['대기', 'bg-[#f0f1ea] text-[#8a8a8a]'],
  ANSWERED: ['답변완료', 'bg-[#E8F3D8] text-brand-text'],
};

interface Inquiry {
  id: number;
  cat: string;
  title: string;
  date: string;
  status: string;
  content: string;
  answer?: string;
  answeredAt?: string;
}

const INITIAL: Inquiry[] = [
  { id: 1, cat: 'DELIVERY', title: '배송이 언제 시작되나요?', date: '2026.07.20', status: 'ANSWERED', content: '어제 주문한 상추 모종이 아직 준비중으로 표시돼요. 언제쯤 받아볼 수 있을까요?', answer: '안녕하세요, 초록님! 주문하신 상추 모종은 오늘 오후 발송 예정이에요. 발송이 시작되면 알림으로 알려드릴게요. 기다려 주셔서 고마워요 🌿', answeredAt: '2026.07.20 18:20' },
  { id: 2, cat: 'PAYMENT', title: '포인트 환불은 어떻게 하나요?', date: '2026.07.19', status: 'PENDING', content: '충전한 포인트를 환불받고 싶은데 방법을 알고 싶어요.' },
  { id: 3, cat: 'ACCOUNT', title: '닉네임을 바꾸고 싶어요', date: '2026.07.15', status: 'ANSWERED', content: '가입할 때 정한 닉네임을 변경할 수 있을까요?', answer: '물론이에요! 마이페이지 > 프로필 수정에서 언제든 닉네임을 바꾸실 수 있어요. 다만 이미 사용 중인 닉네임은 선택할 수 없답니다 🌱', answeredAt: '2026.07.15 11:05' },
];
const CATS = [['PAYMENT', '결제'], ['DELIVERY', '배송'], ['ACCOUNT', '계정'], ['ETC', '기타']];
const REASONS = [['spam', '스팸/광고'], ['inappropriate', '부적절한 콘텐츠'], ['stolen', '사진 도용'], ['etc', '기타']];

const FIELD = 'w-full rounded-xl border-[1.5px] border-line px-[13px] py-3 outline-none';
const LABEL = 'text-[13px] font-bold text-[#6d7a68]';
const CHIP_CAT = 'rounded-full bg-brand-soft px-[11px] py-1 text-xs font-extrabold text-brand-dark';
const CHIP_STAT = 'rounded-full px-3 py-[5px] text-[12.5px] font-extrabold';

export default function Inquiries() {
  const { showToast } = useUI();
  const [inquiries, setInquiries] = useState<Inquiry[]>(INITIAL);
  const [view, setView] = useState('list');
  const [cur, setCur] = useState<Inquiry | null>(null);
  const [form, setForm] = useState<{ cat: string | null; title: string; content: string }>({ cat: null, title: '', content: '' });
  const [reportOpen, setReportOpen] = useState(false);
  const [reason, setReason] = useState<string | null>(null);
  const [reported, setReported] = useState(false);

  const submit = () => {
    if (!form.cat) return showToast('문의 유형을 골라주세요.', 'err');
    if (!form.title.trim()) return showToast('제목을 입력해 주세요.', 'err');
    if (!form.content.trim()) return showToast('문의 내용을 입력해 주세요.', 'err');
    setInquiries([{ id: Date.now(), cat: form.cat, title: form.title, date: '2026.07.21', status: 'PENDING', content: form.content }, ...inquiries]);
    setForm({ cat: null, title: '', content: '' }); setView('list');
    showToast('문의가 접수됐어요. 정성껏 답변드릴게요 💌');
  };
  const submitReport = () => {
    if (!reason) return showToast('신고 사유를 골라주세요.', 'err');
    if (reported) return showToast('이미 신고해 주신 내용이에요. 검토 중이니 조금만 기다려 주세요.', 'err');
    setReportOpen(false); setReason(null); setReported(true);
    showToast('신고가 접수됐어요. 검토 후 조치할게요. 알려주셔서 고마워요.');
  };

  if (view === 'new') {
    return (
      <div className="container max-w-[820px]">
        <button type="button" onClick={() => setView('list')} className="cursor-pointer text-sm font-semibold text-sub">← 1:1 문의</button>
        <h1 className="mb-5 mt-3.5 text-2xl font-extrabold">문의하기</h1>
        <div className="rounded-[20px] bg-white p-6 shadow-card">
          <label className={LABEL}>문의 유형 <span className="text-[#e5533b]">*</span></label>
          <div className="mb-5 mt-2 flex flex-wrap gap-2">
            {CATS.map(([k, label]) => (
              <button
                key={k}
                type="button"
                onClick={() => setForm({ ...form, cat: k })}
                className={`cursor-pointer rounded-[11px] border-[1.5px] px-4 py-[9px] text-sm font-bold ${
                  form.cat === k ? 'border-brand bg-[#F3F8EA] text-ink' : 'border-[#eceee5] bg-white text-[#6d7a68]'
                }`}
              >
                {label}
              </button>
            ))}
          </div>

          <label className={LABEL}>제목 <span className="text-[#e5533b]">*</span></label>
          <input
            value={form.title}
            onChange={(e) => setForm({ ...form, title: e.target.value })}
            maxLength={200}
            placeholder="어떤 점이 궁금하신가요?"
            className={`${FIELD} mb-[18px] mt-1.5`}
          />

          <label className={LABEL}>내용 <span className="text-[#e5533b]">*</span></label>
          <textarea
            value={form.content}
            onChange={(e) => setForm({ ...form, content: e.target.value })}
            maxLength={2000}
            placeholder="자세히 알려주시면 더 정확하게 도와드릴 수 있어요."
            className="mt-1.5 min-h-[150px] w-full resize-y rounded-xl border-[1.5px] border-line p-3.5 text-[15px] leading-[1.6] outline-none"
          />
          <button type="button" onClick={submit} className="mt-[18px] w-full cursor-pointer rounded-[13px] bg-brand p-[15px] font-extrabold text-white">문의 접수</button>
        </div>
      </div>
    );
  }

  if (view === 'detail' && cur) {
    const st = STAT[cur.status];
    return (
      <div className="container max-w-[820px]">
        <button type="button" onClick={() => setView('list')} className="cursor-pointer text-sm font-semibold text-sub">← 1:1 문의</button>
        <div className="my-4 flex items-center gap-2.5">
          <span className={CHIP_CAT}>{CAT[cur.cat]}</span>
          <span className={`${CHIP_STAT} ${st[1]}`}>{st[0]}</span>
        </div>
        <h1 className="mb-1.5 text-[22px] font-extrabold">{cur.title}</h1>
        <div className="mb-[18px] text-[13px] text-faint">{cur.date}</div>
        <div className="whitespace-pre-wrap rounded-2xl bg-white p-5 leading-[1.7] text-[#4a5647] shadow-card">{cur.content}</div>

        {cur.status === 'ANSWERED' ? (
          <div className="mt-4 rounded-2xl border-[1.5px] border-[#dcebc7] bg-[#F6F9EF] p-5">
            <div className="mb-2.5 flex items-center gap-2.5">
              <div className="flex h-[34px] w-[34px] items-center justify-center rounded-full bg-gradient-to-br from-[#AED581] to-[#7CB342] text-base">🌱</div>
              <div>
                <div className="text-sm font-extrabold">키워볼래 지기</div>
                <div className="text-xs text-sub">{cur.answeredAt}</div>
              </div>
            </div>
            <p className="whitespace-pre-wrap leading-[1.7] text-[#4a5647]">{cur.answer}</p>
          </div>
        ) : (
          <div className="mt-4 rounded-2xl bg-[#f5f2ee] px-5 py-[18px] text-sm font-semibold text-[#8a7d6f]">
            답변을 준비하고 있어요. 조금만 기다려 주세요 💌
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="container max-w-[820px]">
      <div className="mb-1.5 flex flex-wrap items-center justify-between gap-2.5">
        <h1 className="text-[26px] font-extrabold">1:1 문의</h1>
        <button type="button" onClick={() => setView('new')} className="cursor-pointer rounded-xl bg-brand px-5 py-[11px] font-bold text-white">문의하기</button>
      </div>
      <p className="mb-[22px] text-sub">궁금하거나 불편한 점이 있으면 편하게 남겨주세요. 정성껏 답해드릴게요 💌</p>

      <div className="flex flex-col gap-3">
        {inquiries.map((q) => {
          const st = STAT[q.status];
          return (
            <button
              key={q.id}
              type="button"
              onClick={() => { setCur(q); setView('detail'); }}
              className="flex flex-wrap items-center gap-3.5 rounded-2xl bg-white px-5 py-[18px] text-left shadow-card"
            >
              <span className={CHIP_CAT}>{CAT[q.cat]}</span>
              <div className="min-w-[150px] flex-1">
                <div className="font-bold">{q.title}</div>
                <div className="mt-[3px] text-[12.5px] text-faint">{q.date}</div>
              </div>
              <span className={`${CHIP_STAT} ${st[1]}`}>{st[0]}</span>
            </button>
          );
        })}
      </div>

      <div className="mt-[30px] text-center">
        <button type="button" onClick={() => setReportOpen(true)} className="cursor-pointer rounded-[11px] border-[1.5px] border-line bg-white px-[18px] py-[11px] font-bold text-sub">
          <span className="material-symbols-outlined text-[17px]">flag</span> 신고 기능 미리보기
        </button>
      </div>

      {reportOpen && (
        <div onClick={() => setReportOpen(false)} className="fixed inset-0 z-[60] flex items-center justify-center bg-[rgba(46,54,42,.4)] p-5">
          <div onClick={(e) => e.stopPropagation()} className="w-full max-w-[420px] animate-pop rounded-[20px] bg-white p-6">
            <h3 className="mb-1 text-[19px] font-extrabold">신고하기</h3>
            <p className="mb-4 text-[13.5px] text-sub">검토 후 조치할게요. 바로 처리되지는 않아요.</p>
            <div className="mb-4 flex items-center gap-2.5 rounded-xl bg-[#f6f7f1] p-2.5">
              <div className="flex h-11 w-11 items-center justify-center rounded-[10px] bg-gradient-to-br from-[#FFCC80] to-[#FF8A65] text-[22px]">🍅</div>
              <div className="text-[13.5px] text-[#6d7a68]">토실이 · 2026.07.21 일지</div>
            </div>
            <div className="mb-3.5 flex flex-col gap-2">
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
            {reason === 'etc' && (
              <textarea placeholder="신고 사유를 적어주세요" className="mb-3.5 min-h-[70px] w-full resize-y rounded-xl border-[1.5px] border-line p-3 outline-none" />
            )}
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
