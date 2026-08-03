'use client';
import { useEffect, useState, Suspense } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { ApiError } from '@/lib/api';
import { CardData, getCard } from '@/lib/card-api';
import { requestExchange } from '@/lib/exchange-api';
import { useStore } from '@/lib/store';
import { useUI } from '@/lib/ui';

const FIELD = 'w-full rounded-xl border-[1.5px] border-line px-[13px] py-3 outline-none';
const LABEL = 'text-[13px] font-bold text-[#6d7a68]';

function ExchangeNewInner() {
  const params = useSearchParams();
  const cardId = Number(params.get('cardId'));
  const { state, hydrated } = useStore();
  const { showToast } = useUI();

  const [card, setCard] = useState<CardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [receiverName, setReceiverName] = useState('');
  const [receiverPhone, setReceiverPhone] = useState('');
  const [address, setAddress] = useState('');
  const [addressDetail, setAddressDetail] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  useEffect(() => {
    if (!hydrated) return;
    if (!Number.isInteger(cardId) || cardId < 1) {
      setError('잘못된 카드 주소예요.');
      setLoading(false);
      return;
    }

    const controller = new AbortController();
    setLoading(true);
    setError('');

    getCard(cardId, state.accessToken, controller.signal)
      .then((response) => setCard(response))
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
    return <div className="container"><div className="rounded-[22px] bg-white py-14 text-center text-sub">불러오고 있어요 🍉</div></div>;
  }

  if (error || !card) {
    return (
      <div className="container">
        <div className="rounded-[22px] bg-white px-5 py-14 text-center text-sub">
          <p>{error || '카드를 찾을 수 없어요.'}</p>
          <Link href="/cards" className="mt-4 inline-block rounded-xl bg-brand px-5 py-2.5 font-bold text-white hover:text-white">카드 목록으로 돌아가기</Link>
        </div>
      </div>
    );
  }

  const owned = card.ownedCount ?? 0;
  const notEnough = owned < card.requiredCountForExchange;
  const outOfStock = card.exchangeProductStock <= 0;

  if (done) {
    return (
      <div className="container">
        <div className="mx-auto my-10 max-w-[480px] animate-pop rounded-[22px] bg-white px-[30px] py-11 text-center shadow-[0_8px_30px_rgba(124,179,66,.12)]">
          <div className="text-[70px]">🍉</div>
          <h1 className="mb-2 mt-4 text-2xl font-extrabold">신청이 접수됐어요!</h1>
          <p className="mb-[26px] leading-[1.6] text-[#6d7a68]">밭에서 가장 좋은 아이로 골라 보내드릴게요 🍉</p>
          <div className="flex flex-wrap justify-center gap-2.5">
            <Link href="/my/exchanges" className="rounded-xl bg-brand px-6 py-[13px] font-bold text-white hover:text-white">교환 내역 보기</Link>
            <Link href="/cards" className="rounded-xl border-[1.5px] border-[#cfe0b6] bg-white px-6 py-[13px] font-bold text-brand-dark">카드 더 모으기</Link>
          </div>
        </div>
      </div>
    );
  }

  const submit = async () => {
    if (!state.accessToken) return showToast('로그인이 필요해요.', 'err');
    if (!receiverName.trim()) return showToast('받는 분 이름을 입력해 주세요.', 'err');
    if (!receiverPhone.trim()) return showToast('연락처를 입력해 주세요.', 'err');
    if (!address.trim()) return showToast('주소를 입력해 주세요.', 'err');

    setSubmitting(true);
    try {
      await requestExchange(
        {
          cardId: card.id,
          receiverName: receiverName.trim(),
          receiverPhone: receiverPhone.trim(),
          address: address.trim(),
          addressDetail: addressDetail.trim() || undefined,
        },
        state.accessToken,
      );
      setDone(true);
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError ? requestError.message : '교환 신청에 실패했어요. 잠시 후 다시 시도해 주세요.',
        'err',
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="container">
      <Link href="/cards" className="text-sm font-semibold text-sub">← 카드 목록</Link>
      <h1 className="mb-5 mt-3.5 text-[26px] font-extrabold">실물 교환 신청</h1>
      <div className="grid items-start gap-[22px] [grid-template-columns:repeat(auto-fit,minmax(280px,1fr))]">
        <div>
          <div className="flex items-center gap-4 rounded-[18px] bg-white p-5 shadow-card">
            <div
              className="flex h-[66px] w-[66px] items-center justify-center rounded-[14px] bg-brand-soft bg-cover bg-center text-[34px]"
              style={card.imageUrl ? { backgroundImage: `url("${card.imageUrl}")` } : undefined}
            >
              {!card.imageUrl && '🃏'}
            </div>
            <div className="text-[26px] text-[#c2c9b8]">→</div>
            <div
              className="flex h-[66px] w-[66px] items-center justify-center rounded-[14px] bg-brand-soft bg-cover bg-center text-[34px]"
              style={card.exchangeProductImageUrl ? { backgroundImage: `url("${card.exchangeProductImageUrl}")` } : undefined}
            >
              {!card.exchangeProductImageUrl && '🎁'}
            </div>
            <div className="flex-1">
              <div className="font-extrabold">{card.exchangeProductName}</div>
              <div className="text-[13px] text-sub">카드 {card.requiredCountForExchange}장을 사용해요</div>
            </div>
          </div>

          {!notEnough && !outOfStock && (
            <>
              <div className="mb-3 mt-5 font-extrabold">배송지</div>
              <label className={LABEL}>받는 분 <span className="text-[#e5533b]">*</span></label>
              <input value={receiverName} onChange={(e) => setReceiverName(e.target.value)} maxLength={50} placeholder="이름" className={`${FIELD} mb-3.5 mt-1.5`} />
              <label className={LABEL}>연락처 <span className="text-[#e5533b]">*</span></label>
              <input value={receiverPhone} onChange={(e) => setReceiverPhone(e.target.value)} maxLength={20} placeholder="010-0000-0000" className={`${FIELD} mb-3.5 mt-1.5`} />
              <label className={LABEL}>주소 <span className="text-[#e5533b]">*</span></label>
              <input value={address} onChange={(e) => setAddress(e.target.value)} maxLength={200} placeholder="도로명 주소" className={`${FIELD} mb-3.5 mt-1.5`} />
              <label className={LABEL}>상세 주소</label>
              <input value={addressDetail} onChange={(e) => setAddressDetail(e.target.value)} maxLength={100} placeholder="동/호수 등" className={`${FIELD} mb-3.5 mt-1.5`} />
            </>
          )}
        </div>

        <div className="rounded-[18px] bg-white p-[22px] shadow-card">
          <div className="mb-3.5 font-extrabold">신청 요약</div>
          <div className="flex justify-between py-[9px] text-[14.5px]"><span className="text-sub">교환 상품</span><span className="font-bold">{card.exchangeProductName}</span></div>
          <div className="flex justify-between border-b border-[#f2f3ec] py-[9px] text-[14.5px]"><span className="text-sub">사용 카드</span><span className="font-bold">{card.name} {card.requiredCountForExchange}장</span></div>
          {notEnough ? (
            <>
              <div className="my-3.5 rounded-xl bg-danger-soft px-3.5 py-3 text-[13.5px] font-semibold text-[#b5502f]">
                아직 카드가 부족해요. {card.requiredCountForExchange - owned}장 더 모으면 교환할 수 있어요.
              </div>
              <button type="button" disabled className="mt-1.5 w-full cursor-not-allowed rounded-[13px] bg-line p-[15px] font-extrabold text-[#a9b3a0]">교환 불가</button>
            </>
          ) : outOfStock ? (
            <>
              <div className="my-3.5 rounded-xl bg-danger-soft px-3.5 py-3 text-[13.5px] font-semibold text-[#b5502f]">
                지금은 실물 수량이 모두 소진됐어요. 다시 채워지면 알려드릴게요.
              </div>
              <button type="button" disabled className="mt-1.5 w-full cursor-not-allowed rounded-[13px] bg-line p-[15px] font-extrabold text-[#a9b3a0]">교환 불가</button>
            </>
          ) : (
            <>
              <p className="my-3.5 text-[12.5px] text-[#a9b3a0]">한 번의 신청으로 실물 하나를 받아요. 여러 개는 나눠서 신청해 주세요.</p>
              <button
                type="button"
                onClick={submit}
                disabled={submitting}
                className="w-full cursor-pointer rounded-[13px] bg-brand p-[15px] font-extrabold text-white disabled:cursor-not-allowed disabled:opacity-60"
              >
                {submitting ? '신청 중...' : '교환 신청하기'}
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

export default function ExchangeNew() {
  return (<Suspense fallback={<div className="container" />}><ExchangeNewInner /></Suspense>);
}
