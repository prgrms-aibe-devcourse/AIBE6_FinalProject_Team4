'use client';
import { useEffect, useState } from 'react';
import { ApiError } from '@/lib/api';
import {
  createInquiry,
  getMyInquiries,
  InquiryCategory,
  InquiryData,
} from '@/lib/inquiry-api';
import { getMyReports, ReportData, ReportTargetType } from '@/lib/report-api';
import { useStore } from '@/lib/store';
import { useUI } from '@/lib/ui';

const CAT: Record<InquiryCategory, string> = { PAYMENT: '결제', DELIVERY: '배송', ACCOUNT: '계정', ETC: '기타' };
const STAT: Record<InquiryData['status'], [string, string]> = {
  OPEN: ['대기', 'bg-[#f0f1ea] text-[#8a8a8a]'],
  ANSWERED: ['답변완료', 'bg-[#E8F3D8] text-brand-text'],
};
const REPORT_TARGET: Record<ReportTargetType, string> = {
  JOURNAL: '일지', USER: '사용자', POST: '게시글', COMMENT: '댓글',
};
const REPORT_STAT: Record<ReportData['status'], [string, string]> = {
  PENDING: ['검토중', 'bg-[#f0f1ea] text-[#8a8a8a]'],
  COMPLETED: ['처리완료', 'bg-[#E8F3D8] text-brand-text'],
  REJECTED: ['반려됨', 'bg-[#FBEDE3] text-[#b5771a]'],
};

function formatDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' }).format(date);
}

function formatDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(date);
}

const CATS: [InquiryCategory, string][] = [['PAYMENT', '결제'], ['DELIVERY', '배송'], ['ACCOUNT', '계정'], ['ETC', '기타']];

const FIELD = 'w-full rounded-xl border-[1.5px] border-line px-[13px] py-3 outline-none';
const LABEL = 'text-[13px] font-bold text-[#6d7a68]';
const CHIP_CAT = 'rounded-full bg-brand-soft px-[11px] py-1 text-xs font-extrabold text-brand-dark';
const CHIP_STAT = 'rounded-full px-3 py-[5px] text-[12.5px] font-extrabold';

export default function Inquiries() {
  const { state, hydrated } = useStore();
  const { showToast } = useUI();
  const [inquiries, setInquiries] = useState<InquiryData[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [view, setView] = useState('list');
  const [cur, setCur] = useState<InquiryData | null>(null);
  const [form, setForm] = useState<{ cat: InquiryCategory | null; title: string; content: string }>({ cat: null, title: '', content: '' });
  const [submitting, setSubmitting] = useState(false);
  const [section, setSection] = useState<'inquiry' | 'report'>('inquiry');
  const [reports, setReports] = useState<ReportData[]>([]);
  const [reportsLoading, setReportsLoading] = useState(true);
  const [reportsError, setReportsError] = useState('');
  const [expandedReportId, setExpandedReportId] = useState<number | null>(null);

  useEffect(() => {
    if (!hydrated || !state.accessToken) return;
    const accessToken = state.accessToken;

    const controller = new AbortController();
    setLoading(true);
    setError('');

    getMyInquiries(accessToken, 0, 50, controller.signal)
      .then((page) => setInquiries(page.content))
      .catch((requestError) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return;
        setInquiries([]);
        setError(
          requestError instanceof ApiError
            ? requestError.message
            : '문의 내역을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.',
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [hydrated, state.accessToken]);

  // "신고 내역" 탭을 처음 열 때만 조회한다 — 문의 탭만 보는 사용자에게 불필요한 요청을 보내지
  // 않기 위해 section에 따라 지연 로딩한다.
  useEffect(() => {
    if (!hydrated || !state.accessToken || section !== 'report') return;
    const accessToken = state.accessToken;

    const controller = new AbortController();
    setReportsLoading(true);
    setReportsError('');

    getMyReports(accessToken, 0, 50, controller.signal)
      .then((page) => setReports(page.content))
      .catch((requestError) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return;
        setReports([]);
        setReportsError(
          requestError instanceof ApiError
            ? requestError.message
            : '신고 내역을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.',
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setReportsLoading(false);
      });

    return () => controller.abort();
  }, [hydrated, state.accessToken, section]);

  const submit = async () => {
    if (!form.cat) return showToast('문의 유형을 골라주세요.', 'err');
    if (!form.title.trim()) return showToast('제목을 입력해 주세요.', 'err');
    if (!form.content.trim()) return showToast('문의 내용을 입력해 주세요.', 'err');
    if (!state.accessToken) return showToast('로그인이 필요해요.', 'err');

    setSubmitting(true);
    try {
      const created = await createInquiry(
        { category: form.cat, title: form.title, content: form.content },
        state.accessToken,
      );
      setInquiries([created, ...inquiries]);
      setForm({ cat: null, title: '', content: '' });
      setView('list');
      showToast('문의가 접수됐어요. 정성껏 답변드릴게요 💌');
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError ? requestError.message : '문의 등록에 실패했어요. 잠시 후 다시 시도해 주세요.',
        'err',
      );
    } finally {
      setSubmitting(false);
    }
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
          <button type="button" onClick={submit} disabled={submitting} className="mt-[18px] w-full cursor-pointer rounded-[13px] bg-brand p-[15px] font-extrabold text-white disabled:cursor-not-allowed disabled:opacity-60">
            {submitting ? '접수 중...' : '문의 접수'}
          </button>
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
          <span className={CHIP_CAT}>{CAT[cur.category]}</span>
          <span className={`${CHIP_STAT} ${st[1]}`}>{st[0]}</span>
        </div>
        <h1 className="mb-1.5 text-[22px] font-extrabold">{cur.title}</h1>
        <div className="mb-[18px] text-[13px] text-faint">{formatDate(cur.createdAt)}</div>
        <div className="whitespace-pre-wrap rounded-2xl bg-white p-5 leading-[1.7] text-[#4a5647] shadow-card">{cur.content}</div>

        {cur.status === 'ANSWERED' ? (
          <div className="mt-4 rounded-2xl border-[1.5px] border-[#dcebc7] bg-[#F6F9EF] p-5">
            <div className="mb-2.5 flex items-center gap-2.5">
              <div className="flex h-[34px] w-[34px] items-center justify-center rounded-full bg-gradient-to-br from-[#AED581] to-[#7CB342] text-base">🌱</div>
              <div>
                <div className="text-sm font-extrabold">{cur.answerAdminName ?? '키워볼래 지기'}</div>
                <div className="text-xs text-sub">{cur.answeredAt ? formatDateTime(cur.answeredAt) : ''}</div>
              </div>
            </div>
            <p className="whitespace-pre-wrap leading-[1.7] text-[#4a5647]">{cur.answerContent}</p>
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
      <div className="mb-4 flex w-fit gap-1.5 rounded-[14px] bg-[#F0F2E8] p-[5px]">
        {(
          [
            ['inquiry', '1:1 문의'],
            ['report', '신고 내역'],
          ] as const
        ).map(([key, label]) => (
          <button
            key={key}
            type="button"
            onClick={() => setSection(key)}
            className={`cursor-pointer rounded-[11px] px-[18px] py-[9px] text-sm font-bold transition-colors duration-150 ${
              section === key
                ? 'bg-white text-ink shadow-[0_2px_8px_rgba(0,0,0,.06)]'
                : 'bg-transparent text-sub hover:bg-white/70 hover:text-ink'
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {section === 'inquiry' ? (
        <>
          <div className="mb-1.5 flex flex-wrap items-center justify-between gap-2.5">
            <h1 className="text-[26px] font-extrabold">1:1 문의</h1>
            <button type="button" onClick={() => setView('new')} className="cursor-pointer rounded-xl bg-brand px-5 py-[11px] font-bold text-white">문의하기</button>
          </div>
          <p className="mb-[22px] text-sub">궁금하거나 불편한 점이 있으면 편하게 남겨주세요. 정성껏 답해드릴게요 💌</p>

          {loading ? (
            <div className="rounded-[22px] bg-white py-14 text-center text-[15px] text-sub">문의 내역을 불러오고 있어요 💌</div>
          ) : error ? (
            <div className="rounded-[22px] bg-white px-5 py-14 text-center text-[15px] text-sub">{error}</div>
          ) : inquiries.length === 0 ? (
            <div className="rounded-[22px] bg-white py-14 text-center text-[15px] text-sub">아직 등록한 문의가 없어요.</div>
          ) : (
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
                    <span className={CHIP_CAT}>{CAT[q.category]}</span>
                    <div className="min-w-[150px] flex-1">
                      <div className="font-bold">{q.title}</div>
                      <div className="mt-[3px] text-[12.5px] text-faint">{formatDate(q.createdAt)}</div>
                    </div>
                    <span className={`${CHIP_STAT} ${st[1]}`}>{st[0]}</span>
                  </button>
                );
              })}
            </div>
          )}
        </>
      ) : (
        <>
          <h1 className="mb-1.5 text-[26px] font-extrabold">신고 내역</h1>
          <p className="mb-[22px] text-sub">내가 접수한 신고의 처리 상태를 확인할 수 있어요.</p>

          {reportsLoading ? (
            <div className="rounded-[22px] bg-white py-14 text-center text-[15px] text-sub">신고 내역을 불러오고 있어요</div>
          ) : reportsError ? (
            <div className="rounded-[22px] bg-white px-5 py-14 text-center text-[15px] text-sub">{reportsError}</div>
          ) : reports.length === 0 ? (
            <div className="rounded-[22px] bg-white py-14 text-center text-[15px] text-sub">아직 접수한 신고가 없어요.</div>
          ) : (
            <div className="flex flex-col gap-3">
              {reports.map((r) => {
                const st = REPORT_STAT[r.status];
                const expanded = expandedReportId === r.id;
                return (
                  <div key={r.id} className="rounded-2xl bg-white px-5 py-[18px] shadow-card">
                    <button
                      type="button"
                      onClick={() => setExpandedReportId(expanded ? null : r.id)}
                      className="flex w-full flex-wrap items-center gap-3.5 text-left"
                    >
                      <span className={CHIP_CAT}>{REPORT_TARGET[r.targetType]}</span>
                      <div className="min-w-[150px] flex-1">
                        <div className="font-bold">{r.reason}</div>
                        <div className="mt-[3px] text-[12.5px] text-faint">{formatDate(r.createdAt)}</div>
                      </div>
                      <span className={`${CHIP_STAT} ${st[1]}`}>{st[0]}</span>
                    </button>
                    {expanded && r.status !== 'PENDING' && (
                      <div className="mt-3.5 rounded-xl bg-[#f6f7f1] p-3.5">
                        <div className="mb-1 text-xs font-bold text-sub">
                          {r.processedAdminName ?? '관리자'}
                          {r.processedAt ? ` · ${formatDateTime(r.processedAt)}` : ''}
                        </div>
                        <p className="text-sm font-bold">{r.actionType}</p>
                        <p className="mt-1 whitespace-pre-wrap text-sm leading-[1.6]">{r.actionDetail}</p>
                      </div>
                    )}
                    {expanded && r.status === 'PENDING' && (
                      <div className="mt-3.5 rounded-xl bg-[#f6f7f1] p-3.5 text-sm text-sub">
                        아직 검토 중이에요. 처리되면 여기서 확인할 수 있어요.
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </>
      )}
    </div>
  );
}
