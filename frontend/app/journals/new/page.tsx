'use client';
import { useState, Suspense } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useStore } from '@/lib/store';
import { useUI } from '@/lib/ui';
import { PLANTS } from '@/lib/data';
import { grads } from '@/lib/theme';

const N = 30;
const PHOTOS = [
  { emoji: '🌱', grad: grads.sprout, reused: false },
  { emoji: '🍅', grad: grads.tomato, reused: false },
  { emoji: '🌿', grad: 'linear-gradient(135deg,#A5D6A7,#66BB6A)', reused: false },
  { emoji: '☀️', grad: grads.sun, reused: true },
];
const CONFETTI = [
  { left: '8%', dur: '1.4s', delay: '0s', emoji: '🌿' }, { left: '26%', dur: '1.7s', delay: '.2s', emoji: '✨' },
  { left: '46%', dur: '1.3s', delay: '.1s', emoji: '🍅' }, { left: '64%', dur: '1.8s', delay: '.35s', emoji: '🌱' },
  { left: '82%', dur: '1.5s', delay: '.15s', emoji: '✨' }, { left: '92%', dur: '1.6s', delay: '.28s', emoji: '💚' },
];

interface Draft {
  plantId: number | null;
  photoIdx: number | null;
  content: string;
}

interface JournalResult {
  kind: 'neutral' | 'reward';
  emoji: string;
  title: string;
  body: string;
}

function NewJournalInner() {
  const params = useSearchParams();
  const preselect = params.get('plant');
  const { state, set, creditFree } = useStore();
  const { showToast } = useUI();
  const plants = PLANTS.filter((p) => !p.archived);
  const [draft, setDraft] = useState<Draft>({ plantId: preselect ? Number(preselect) : null, photoIdx: null, content: '' });
  const [result, setResult] = useState<JournalResult | null>(null);

  const submit = () => {
    if (draft.photoIdx === null) return showToast('앗, 사진이 꼭 필요해요. 오늘의 모습을 한 장 담아주세요 📷', 'err');
    if (draft.plantId === null) return showToast('먼저 어떤 식물인지 골라주세요 🌿', 'err');
    const photo = PHOTOS[draft.photoIdx];
    // 보상 판정: 이미 오늘 받음 / 중복 사진(최근 30일) / 신규 → 지급
    if (state.rewardedToday) {
      setResult({ kind: 'neutral', emoji: '🌙', title: '일지가 저장됐어요!', body: '오늘의 포인트는 이미 받으셨네요. 내일 또 만나요 🌙' });
      set({ wroteToday: true });
    } else if (photo.reused) {
      setResult({ kind: 'neutral', emoji: '🔁', title: '일지가 저장됐어요!', body: '새로운 사진으로 기록하면 포인트를 받을 수 있어요.' });
      set({ wroteToday: true });
    } else {
      setResult({ kind: 'reward', emoji: '✨', title: '오늘도 정성껏 기록해 주셨네요!', body: `${N} 포인트가 지급되었어요 ✨` });
      creditFree(N);
      set({ rewardedToday: true, wroteToday: true, lastReward: N });
    }
  };

  const reward = result && result.kind === 'reward';

  return (
    <div className="container">
      <Link href="/journals" className="text-sm font-semibold text-sub">← 일지</Link>
      <h1 className="mb-1 mt-3.5 text-[26px] font-extrabold">오늘의 일지 쓰기</h1>
      <p className="mb-2 text-[14.5px] text-sub">오늘 이 아이의 모습을 남겨주세요.</p>
      <div className="mb-[22px] inline-flex items-center gap-1.5 rounded-full bg-gold-soft px-[13px] py-[7px] text-[12.5px] font-bold text-gold-text">
        ⓘ 포인트는 하루에 한 번, 새로운 사진으로 기록할 때 지급돼요.
      </div>

      {result ? (
        <div
          className={`relative max-w-[640px] animate-pop overflow-hidden rounded-[18px] p-6 ${
            reward ? 'bg-gradient-to-br from-[#FFE9A6] to-[#FFD54F]' : 'bg-brand-soft'
          }`}
        >
          {reward && CONFETTI.map((c, i) => (
            <span
              key={i}
              className="absolute -top-2 animate-confettiFall text-[15px]"
              style={{ left: c.left, animationDuration: c.dur, animationDelay: c.delay, animationIterationCount: 'infinite' }}
            >
              {c.emoji}
            </span>
          ))}
          <div className="text-[34px]">{result.emoji}</div>
          <div className={`mt-2 text-lg font-extrabold ${reward ? 'text-[#6b5500]' : 'text-ink'}`}>{result.title}</div>
          <div className={`mt-[5px] text-[14.5px] leading-[1.55] opacity-90 ${reward ? 'text-[#6b5500]' : 'text-ink'}`}>{result.body}</div>
          <div className="mt-[18px] flex flex-wrap gap-2.5">
            <Link href="/journals" className={`rounded-[11px] px-5 py-[11px] font-bold text-white hover:text-white ${reward ? 'bg-[#6b5500]' : 'bg-ink'}`}>
              일지 목록으로
            </Link>
            <button
              type="button"
              onClick={() => { setResult(null); setDraft({ plantId: null, photoIdx: null, content: '' }); }}
              className={`cursor-pointer rounded-[11px] bg-white/70 px-5 py-[11px] font-bold ${reward ? 'text-[#6b5500]' : 'text-ink'}`}
            >
              다른 식물도 기록
            </button>
          </div>
        </div>
      ) : (
        <div className="max-w-[640px] rounded-[20px] bg-white p-6 shadow-card">
          <div className="mb-3 font-extrabold">1. 어떤 식물인가요?</div>
          <div className="mb-[26px] flex flex-wrap gap-2.5">
            {plants.map((p) => (
              <button
                key={p.id}
                type="button"
                onClick={() => setDraft({ ...draft, plantId: p.id })}
                className={`flex cursor-pointer items-center gap-[9px] rounded-[13px] border-2 px-3.5 py-[9px] ${
                  draft.plantId === p.id ? 'border-brand bg-[#F3F8EA]' : 'border-[#eceee5] bg-white'
                }`}
              >
                <span className="flex h-[34px] w-[34px] items-center justify-center rounded-[9px] text-lg" style={{ background: p.grad }}>{p.emoji}</span>
                <span className="font-bold">{p.nickname}</span>
              </button>
            ))}
          </div>

          <div className="mb-[5px] font-extrabold">2. 오늘의 사진 <span className="text-[#e5533b]">*</span></div>
          <div className="mb-3 text-[12.5px] text-[#a9b3a0]">jpg · png · webp / 5MB 이하 · (🔁 표시는 최근 30일 내 쓴 사진이에요)</div>
          <div className="mb-[26px] flex flex-wrap gap-3">
            {PHOTOS.map((ph, i) => (
              <button
                key={i}
                type="button"
                onClick={() => setDraft({ ...draft, photoIdx: i })}
                className={`relative flex h-[90px] w-[90px] cursor-pointer items-center justify-center rounded-[14px] border-[3px] text-[40px] ${
                  draft.photoIdx === i ? 'border-brand' : 'border-transparent'
                }`}
                style={{ background: ph.grad }}
              >
                {ph.emoji}
                {ph.reused && <span className="absolute bottom-1 left-1 rounded-full bg-black/35 px-1.5 py-0.5 text-[11px] text-white">🔁</span>}
                {draft.photoIdx === i && (
                  <span className="absolute right-1 top-1 flex h-5 w-5 items-center justify-center rounded-full bg-brand text-xs text-white">✓</span>
                )}
              </button>
            ))}
          </div>

          <div className="mb-2.5 font-extrabold">3. 오늘의 기록</div>
          <textarea
            value={draft.content}
            onChange={(e) => setDraft({ ...draft, content: e.target.value })}
            placeholder="오늘 이 아이는 어떤 모습이었나요?"
            maxLength={2000}
            className="min-h-[130px] w-full resize-y rounded-[14px] border-[1.5px] border-line p-3.5 text-[15px] leading-[1.6] outline-none"
          />
          <div className="mt-[5px] text-right text-xs text-faint">{draft.content.length} / 2000</div>
          <button type="button" onClick={submit} className="mt-3 w-full cursor-pointer rounded-[14px] bg-brand p-[15px] text-base font-extrabold text-white">
            기록하고 포인트 받기 ✨
          </button>
        </div>
      )}
    </div>
  );
}

export default function NewJournal() {
  return (<Suspense fallback={<div className="container" />}><NewJournalInner /></Suspense>);
}
