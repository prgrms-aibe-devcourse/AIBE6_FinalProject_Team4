'use client';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { ApiError } from '@/lib/api';
import { CardData, getCard, purchaseCard } from '@/lib/card-api';
import { useStore, fmt } from '@/lib/store';
import { useUI } from '@/lib/ui';
import PointPrice from '@/components/PointPrice';

const CONFETTI = [
  { left: '10%', dur: '1.5s', delay: '0s', emoji: '🎉' }, { left: '28%', dur: '1.8s', delay: '.2s', emoji: '✨' },
  { left: '48%', dur: '1.4s', delay: '.1s', emoji: '🍉' }, { left: '66%', dur: '1.9s', delay: '.3s', emoji: '🥕' },
  { left: '82%', dur: '1.6s', delay: '.15s', emoji: '✨' }, { left: '92%', dur: '1.7s', delay: '.25s', emoji: '🎉' },
];

export default function CardDetail({ params }: { params: { id: string } }) {
  const router = useRouter();
  const {
    state,
    hydrated,
    walletLoading,
    walletLoaded,
    refreshWallet,
    set,
  } = useStore();
  const { showToast, askConfirm } = useUI();
  const cardId = Number(params.id);
  const [card, setCard] = useState<CardData | null>(null);
  const [owned, setOwned] = useState<number | null>(null);
  const [qty, setQty] = useState(1);
  const [celebrate, setCelebrate] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [purchasing, setPurchasing] = useState(false);

  useEffect(() => {
    if (!Number.isInteger(cardId) || cardId < 1) {
      setError('잘못된 카드 주소예요.');
      setLoading(false);
      return;
    }
    if (!hydrated) return;

    const controller = new AbortController();
    setLoading(true);
    setError('');

    getCard(cardId, state.accessToken, controller.signal)
      .then((response) => {
        setCard(response);
        setOwned(response.ownedCount);
      })
      .catch((requestError) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return;
        setCard(null);
        setError(
          requestError instanceof ApiError
            ? requestError.message
            : '카드를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.',
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [cardId, hydrated, state.accessToken]);

  if (loading) {
    return (
      <div className="container">
        <div className="rounded-[22px] bg-white py-14 text-center text-sub">
          카드를 불러오고 있어요 🃏
        </div>
      </div>
    );
  }

  if (error || !card) {
    return (
      <div className="container">
        <div className="rounded-[22px] bg-white px-5 py-14 text-center text-sub">
          <p>{error || '카드를 찾을 수 없어요.'}</p>
          <Link
            href="/cards"
            className="mt-4 inline-block rounded-xl bg-brand px-5 py-2.5 font-bold text-white hover:text-white"
          >
            카드 목록으로 돌아가기
          </Link>
        </div>
      </div>
    );
  }

  const total = card.pointPrice * qty;
  const availableFreePoint = Math.max(state.wallet.free, 0);
  const availablePaidPoint = Math.max(state.wallet.paid, 0);
  const usedFreePoint = Math.min(availableFreePoint, total);
  const usedPaidPoint = total - usedFreePoint;
  const pointShortage = Math.max(0, usedPaidPoint - availablePaidPoint);
  const ring = `conic-gradient(#7CB342 ${Math.min(
    360,
    ((owned ?? 0) / card.requiredCountForExchange) * 360,
  )}deg,#eef0e6 0)`;

  const buy = () => {
    if (!hydrated || !state.accessToken || owned === null) {
      showToast('카드 구매는 로그인 후 이용할 수 있어요.', 'err');
      return;
    }
    if (!walletLoaded) {
      showToast(
        walletLoading
          ? '포인트 잔액을 확인하고 있어요.'
          : '포인트 잔액을 확인하지 못했어요. 잠시 후 다시 시도해 주세요.',
        'err',
      );
      return;
    }
    if (pointShortage > 0) {
      showToast(
        `사용 가능한 포인트가 ${fmt(pointShortage)}P 부족해요.`,
        'err',
      );
      return;
    }
    askConfirm({ icon: 'eco', title: '카드를 구매할까요?', ok: '구매하기',
      body: `${card.name} ${qty}장 · 무상 포인트 ${fmt(usedFreePoint)}P${usedPaidPoint > 0 ? `와 유상 포인트 ${fmt(usedPaidPoint)}P` : ''}를 사용해요.`,
      onOk: async () => {
        const currentOwned = owned ?? 0;
        setPurchasing(true);
        try {
          const response = await purchaseCard(
            card.id,
            qty,
            state.accessToken!,
            crypto.randomUUID(),
          );
          await refreshWallet();
          setOwned(response.ownedCount);
          setQty(1);
          const reached =
            response.ownedCount >= card.requiredCountForExchange &&
            currentOwned < card.requiredCountForExchange;
          if (reached) {
            set((s) => ({ readyCards: s.readyCards + 1 }));
            setCelebrate(true);
          } else {
            showToast('카드를 구매했어요! 🃏');
          }
        } catch (purchaseError) {
          showToast(
            purchaseError instanceof ApiError
              ? purchaseError.message
              : '카드를 구매하지 못했어요. 잠시 후 다시 시도해 주세요.',
            'err',
          );
        } finally {
          setPurchasing(false);
        }
      } });
  };

  return (
    <div className="container">
      <Link href="/cards" className="text-sm font-semibold text-sub">← 카드</Link>
      <div className="mt-4 grid items-start gap-7 [grid-template-columns:repeat(auto-fit,minmax(280px,1fr))]">
        <div
          className="flex aspect-[3/4] max-h-[420px] items-center justify-center overflow-hidden rounded-[22px] bg-brand-soft bg-cover bg-center text-[150px] shadow-[0_12px_36px_rgba(0,0,0,.1)]"
          style={
            card.imageUrl
              ? { backgroundImage: `url("${card.imageUrl}")` }
              : undefined
          }
        >
          {!card.imageUrl && '🃏'}
        </div>
        <div>
          <h1 className="mb-1.5 text-[28px] font-extrabold">{card.name}</h1>
          <div className="mb-3 flex items-center gap-2">
            <span className="text-sm font-bold text-sub">1장당</span>
            <PointPrice value={card.pointPrice} size="lg" />
            <span className="rounded-full bg-brand-soft px-2.5 py-1 text-xs font-extrabold text-brand-dark">
              무상 포인트 우선
            </span>
          </div>
          <p className="mb-5 text-[14.5px] leading-[1.7] text-[#6d7a68]">
            {card.description || '카드 설명을 준비하고 있어요.'}
          </p>

          {owned === null ? (
            <div className="mb-5 rounded-[18px] bg-white px-5 py-[18px] text-[13.5px] text-sub shadow-card">
              로그인하면 내 보유 수량과 교환 진행도를 확인할 수 있어요.
            </div>
          ) : (
            <div className="mb-5 flex items-center gap-5 rounded-[18px] bg-white px-5 py-[18px] shadow-card">
              <div
                className="flex h-[92px] w-[92px] flex-none items-center justify-center rounded-full"
                style={{ background: ring }}
              >
                <div className="flex h-[70px] w-[70px] flex-col items-center justify-center rounded-full bg-white font-extrabold">
                  <span className="text-xl text-brand">{owned}</span>
                  <span className="text-xs text-faint">
                    / {card.requiredCountForExchange}
                  </span>
                </div>
              </div>
              <div>
                <div className="text-base font-extrabold">내 진행도</div>
                <div className="mt-[3px] text-[13.5px] text-sub">
                  {owned >= card.requiredCountForExchange
                    ? '모두 모았어요! 교환할 수 있어요 🎉'
                    : `${card.requiredCountForExchange - owned}장만 더 모으면 교환할 수 있어요`}
                </div>
              </div>
            </div>
          )}

          <div className="mb-[22px] flex items-center gap-3.5 rounded-[18px] bg-white px-5 py-[18px] shadow-card">
            <div
              className="flex h-16 w-16 flex-none items-center justify-center rounded-[14px] bg-brand-soft bg-cover bg-center text-[34px]"
              style={
                card.exchangeProductImageUrl
                  ? { backgroundImage: `url("${card.exchangeProductImageUrl}")` }
                  : undefined
              }
            >
              {!card.exchangeProductImageUrl && '🎁'}
            </div>
            <div>
              <div className="text-xs font-bold text-faint">교환 상품</div>
              <div className="font-extrabold">{card.exchangeProductName}</div>
              <div className="mt-0.5 text-[13px] text-sub">
                {card.exchangeProductDescription || '교환 상품 설명을 준비하고 있어요.'}
                {' · '}
                {card.requiredCountForExchange}장 필요
              </div>
            </div>
          </div>

          <div className="mb-4 flex items-center gap-3.5">
            <span className="font-bold text-[#6d7a68]">수량</span>
            <div className="flex items-center overflow-hidden rounded-[11px] border-[1.5px] border-line">
              <button type="button" onClick={() => setQty(Math.max(1, qty - 1))} className="flex h-10 w-10 cursor-pointer items-center justify-center text-xl text-[#6d7a68]">−</button>
              <div className="w-[46px] text-center text-base font-extrabold">{qty}</div>
              <button type="button" onClick={() => setQty(Math.min(99, qty + 1))} className="flex h-10 w-10 cursor-pointer items-center justify-center text-xl text-[#6d7a68]">+</button>
            </div>
            <span className="ml-auto flex items-center gap-1.5 font-extrabold text-gold-text">
              합계 <PointPrice value={total} size="sm" />
            </span>
          </div>

          {owned !== null && (
            <div className="mb-4 rounded-[14px] border border-[#e4ead8] bg-[#FAFCF6] px-4 py-3.5">
              <div className="flex items-center justify-between text-sm">
                <span className="font-bold text-sub">보유 무상 포인트</span>
                <span className="font-extrabold text-brand-dark">
                  {walletLoading && !walletLoaded ? '확인 중…' : `${fmt(state.wallet.free)}P`}
                </span>
              </div>
              <div className="mt-1.5 flex items-center justify-between text-sm">
                <span className="font-bold text-sub">보유 유상 포인트</span>
                <span className="font-extrabold text-brand-dark">
                  {walletLoading && !walletLoaded ? '확인 중…' : `${fmt(state.wallet.paid)}P`}
                </span>
              </div>
              <p className={`mt-2 text-xs ${pointShortage > 0 && walletLoaded ? 'font-semibold text-danger' : 'text-sub'}`}>
                {pointShortage > 0 && walletLoaded
                  ? `사용 가능한 포인트가 ${fmt(pointShortage)}P 부족해요.`
                  : usedPaidPoint > 0
                    ? `무상 ${fmt(usedFreePoint)}P를 먼저 사용하고 유상 ${fmt(usedPaidPoint)}P를 사용해요.`
                    : `무상 포인트 ${fmt(usedFreePoint)}P를 먼저 사용해요.`}
              </p>
            </div>
          )}

          <button
            type="button"
            onClick={buy}
            disabled={purchasing || (walletLoaded && pointShortage > 0)}
            className="w-full cursor-pointer rounded-[14px] bg-brand p-[15px] text-base font-extrabold text-white disabled:cursor-not-allowed disabled:opacity-60"
          >
            {purchasing
              ? '구매 처리 중...'
              : walletLoaded && pointShortage > 0
                ? '포인트 부족'
                : '포인트로 구매하기'}
          </button>
        </div>
      </div>

      {celebrate && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-[rgba(46,54,42,.45)] p-5">
          <div className="relative w-full max-w-[400px] animate-pop overflow-hidden rounded-3xl bg-white px-7 py-8 text-center">
            {CONFETTI.map((c, i) => (
              <span
                key={i}
                className="absolute -top-2 animate-confettiFall text-base"
                style={{ left: c.left, animationDuration: c.dur, animationDelay: c.delay, animationIterationCount: 'infinite' }}
              >
                {c.emoji}
              </span>
            ))}
            <div className="text-[66px]">🎉</div>
            <h3 className="mb-2 mt-3.5 text-xl font-extrabold">축하해요!</h3>
            <p className="mb-6 leading-[1.6] text-[#6d7a68]">{card.name}가 모두 모였어요.<br />지금 바로 교환할 수 있어요 🎉</p>
            <div className="flex gap-2.5">
              <button type="button" onClick={() => router.push(`/exchange/new?cardId=${card.id}`)} className="flex-1 cursor-pointer rounded-xl bg-brand p-[13px] font-extrabold text-white">
                교환 신청하기
              </button>
              <button type="button" onClick={() => setCelebrate(false)} className="cursor-pointer rounded-xl border-[1.5px] border-line bg-white px-[18px] py-[13px] font-bold text-sub">
                나중에
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
