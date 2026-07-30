'use client';
import { useEffect, useState, Suspense } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useStore } from '@/lib/store';
import { useUI } from '@/lib/ui';
import { grads } from '@/lib/theme';
import { ApiError } from '@/lib/api';
import {
  createPlantJournal,
  getMyPlantProfiles,
  PlantProfileData,
} from '@/features/journal/api';

const PHOTOS = [
  { emoji: '🌱', grad: grads.sprout, imageUrl: '/journal-demo/photo-1.svg' },
  { emoji: '🍅', grad: grads.tomato, imageUrl: '/journal-demo/photo-2.svg' },
  { emoji: '🌿', grad: 'linear-gradient(135deg,#A5D6A7,#66BB6A)', imageUrl: '/journal-demo/photo-3.svg' },
  { emoji: '☀️', grad: grads.sun, imageUrl: '/journal-demo/photo-4.svg' },
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
  const router = useRouter();
  const params = useSearchParams();
  const preselect = params.get('plant');
  const { state, set, refreshWallet } = useStore();
  const { showToast } = useUI();
  const [plants, setPlants] = useState<PlantProfileData[]>([]);
  const [loadingPlants, setLoadingPlants] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [draft, setDraft] = useState<Draft>({ plantId: preselect ? Number(preselect) : null, photoIdx: null, content: '' });
  const [result, setResult] = useState<JournalResult | null>(null);

  useEffect(() => {
    if (!state.accessToken) {
      setLoadingPlants(false);
      return;
    }
    const controller = new AbortController();
    getMyPlantProfiles(state.accessToken, controller.signal)
      .then((profiles) => setPlants(profiles.filter((profile) => profile.status === 'GROWING')))
      .catch((cause: unknown) => {
        if (cause instanceof DOMException && cause.name === 'AbortError') return;
        showToast(
          cause instanceof ApiError ? cause.message : '내 식물 목록을 불러오지 못했어요.',
          'err',
        );
      })
      .finally(() => setLoadingPlants(false));
    return () => controller.abort();
  }, [showToast, state.accessToken]);

  const submit = async () => {
    if (submitting) return;
    if (draft.photoIdx === null) return showToast('앗, 사진이 꼭 필요해요. 오늘의 모습을 한 장 담아주세요 📷', 'err');
    if (draft.plantId === null) return showToast('먼저 어떤 식물인지 골라주세요 🌿', 'err');
    if (!state.accessToken) return showToast('로그인이 필요해요.', 'err');
    const photo = PHOTOS[draft.photoIdx];
    setSubmitting(true);
    try {
      const hashSource = new TextEncoder().encode(`journal-demo-photo:${draft.photoIdx}`);
      const digest = await crypto.subtle.digest('SHA-256', hashSource);
      const imageHash = Array.from(new Uint8Array(digest))
        .map((value) => value.toString(16).padStart(2, '0'))
        .join('');
      const journal = await createPlantJournal(state.accessToken, {
        plantProfileId: draft.plantId,
        content: draft.content.trim(),
        images: [{ imageUrl: photo.imageUrl, imageHash, representative: true }],
      });
      set((current) => ({
        wroteToday: true,
        rewardedToday: current.rewardedToday || journal.gachaReward.granted,
      }));
      await refreshWallet();

      if (journal.gachaReward.granted && journal.gachaReward.drawId) {
        router.push(`/gacha/open/${journal.gachaReward.drawId}`);
        return;
      }
      setResult({
        kind: 'neutral',
        emoji: '🌙',
        title: '일지가 저장됐어요!',
        body: '오늘의 카드팩은 이미 받으셨어요. 내일 다시 만나요 🌙',
      });
    } catch (cause: unknown) {
      showToast(
        cause instanceof ApiError ? cause.message : '일지를 저장하지 못했어요.',
        'err',
      );
    } finally {
      setSubmitting(false);
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
            {reward && (
              <Link
                href="/gacha"
                className="rounded-[11px] border border-[#d8bd52] bg-white px-5 py-[11px] font-bold text-[#6b5500]"
              >
                오늘의 카드팩 확인
              </Link>
            )}
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
            {loadingPlants && <span className="text-sm text-sub">내 식물을 불러오는 중...</span>}
            {!loadingPlants && plants.length === 0 && (
              <Link href="/plants" className="text-sm font-bold text-brand">
                먼저 재배 중인 식물을 등록해 주세요.
              </Link>
            )}
            {plants.map((p, index) => (
              <button
                key={p.id}
                type="button"
                onClick={() => setDraft({ ...draft, plantId: p.id })}
                className={`flex cursor-pointer items-center gap-[9px] rounded-[13px] border-2 px-3.5 py-[9px] ${
                  draft.plantId === p.id ? 'border-brand bg-[#F3F8EA]' : 'border-[#eceee5] bg-white'
                }`}
              >
                <span className="flex h-[34px] w-[34px] items-center justify-center rounded-[9px] text-lg" style={{ background: [grads.sprout, grads.tomato, grads.mint][index % 3] }}>🌿</span>
                <span className="font-bold">{p.nickname}</span>
              </button>
            ))}
          </div>

          <div className="mb-[5px] font-extrabold">2. 오늘의 사진 <span className="text-[#e5533b]">*</span></div>
          <div className="mb-3 text-[12.5px] text-[#a9b3a0]">S3 연결 전까지 제공되는 QA용 샘플 사진이에요.</div>
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
          <button type="button" onClick={submit} disabled={submitting || loadingPlants || plants.length === 0} className="mt-3 w-full cursor-pointer rounded-[14px] bg-brand p-[15px] text-base font-extrabold text-white disabled:cursor-not-allowed disabled:opacity-50">
            {submitting ? '기록을 저장하는 중...' : '기록하고 카드팩 받기 ✨'}
          </button>
        </div>
      )}
    </div>
  );
}

export default function NewJournal() {
  return (<Suspense fallback={<div className="container" />}><NewJournalInner /></Suspense>);
}
