import Link from "next/link";
import CommerceCardImage from "@/features/commerce/CommerceCardImage";
import {
  formatCommerceDateTime,
  formatPoint,
} from "@/features/commerce/presentation";
import type {
  MarketListing,
  MarketNegotiation,
  MarketSellableCard,
} from "@/features/card-market/api";

const STATUS_LABEL: Record<string, string> = {
  OPEN: "판매 중",
  SOLD: "판매 완료",
  CANCELLED: "취소",
  EXPIRED: "기간 만료",
  NEGOTIATING: "협상 중",
  ACCEPTED: "거래 완료",
  REJECTED: "거절",
  LISTING_CLOSED: "판매글 종료",
};

export function WalletCard({
  icon,
  label,
  value,
  tone,
}: {
  icon: string;
  label: string;
  value: string;
  tone: "gold" | "green";
}) {
  const style =
    tone === "gold"
      ? "border-[#e8d394] bg-[#fffaf0] text-[#806419]"
      : "border-[#cdddc5] bg-[#f5faf2] text-[#476341]";
  return (
    <div className={`rounded-[24px] border p-6 shadow-sm ${style}`}>
      <div className="flex items-center gap-2 text-xs font-black">
        <span className="material-symbols-outlined text-xl">{icon}</span>
        {label}
      </div>
      <p className="mt-3 text-2xl font-black">{value}</p>
    </div>
  );
}

export function SectionTitle({
  eyebrow,
  title,
}: {
  eyebrow: string;
  title: string;
}) {
  return (
    <div className="mb-5">
      <p className="text-xs font-black uppercase tracking-[0.18em] text-[#8b947f]">
        {eyebrow}
      </p>
      <h2 className="mt-1 text-2xl font-black">{title}</h2>
    </div>
  );
}

export function PrivatePager({
  page,
  totalPages,
  onChange,
}: {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
}) {
  if (totalPages <= 1) return null;
  return (
    <div className="mt-6 flex items-center justify-center gap-3 border-t border-[#dfe5da] pt-5">
      <button
        type="button"
        disabled={page <= 0}
        onClick={() => onChange(page - 1)}
        className="rounded-xl border border-[#cad5c5] bg-white px-4 py-2 text-sm font-black disabled:opacity-35"
      >
        이전
      </button>
      <span className="text-sm font-black text-[#667260]">
        {page + 1} / {totalPages}
      </span>
      <button
        type="button"
        disabled={page + 1 >= totalPages}
        onClick={() => onChange(page + 1)}
        className="rounded-xl border border-[#cad5c5] bg-white px-4 py-2 text-sm font-black disabled:opacity-35"
      >
        다음
      </button>
    </div>
  );
}

export function ListingCard({
  listing,
  mine,
  onBuy,
}: {
  listing: MarketListing;
  mine: boolean;
  onBuy: () => void;
}) {
  return (
    <article className="group overflow-hidden rounded-[24px] border border-[#d9e0d4] bg-white shadow-sm transition hover:-translate-y-1 hover:shadow-xl">
      <div className="relative aspect-[3/4] bg-[#edf0e9] p-3">
        <CommerceCardImage src={listing.imageUrl} name={listing.cardName} />
        <span
          className={`absolute left-4 top-4 rounded-full px-3 py-1 text-[11px] font-black ${listing.assetType === "GOLDEN_RARE" ? "bg-[#f5d96a] text-[#634d0d]" : "bg-[#7050a0] text-white"}`}
        >
          {listing.assetType === "GOLDEN_RARE" ? "GOLDEN" : "HYPER"}
        </span>
      </div>
      <div className="p-5">
        <h3 className="truncate text-lg font-black">{listing.cardName}</h3>
        <p className="mt-1 text-xs text-[#7a8476]">
          {listing.sellerNickname} · 제안 {listing.activeOfferCount}개
        </p>
        <div className="mt-4 flex items-end justify-between">
          <div>
            <p className="text-[11px] font-bold text-[#8a9384]">즉시 구매</p>
            <p className="text-xl font-black text-[#765b12]">
              {formatPoint(listing.askingPrice)}
            </p>
          </div>
          <button
            type="button"
            disabled={mine}
            onClick={onBuy}
            className="rounded-xl bg-[#344b32] px-4 py-2 text-xs font-black text-white disabled:bg-[#dfe4db] disabled:text-[#929a8f]"
          >
            {mine ? "내 판매" : "거래하기"}
          </button>
        </div>
        <p className="mt-4 text-[11px] text-[#929a8f]">
          {formatCommerceDateTime(listing.expiresAt)}까지
        </p>
        <Link
          href={`/card-market/listings/${listing.id}`}
          className="mt-3 block text-center text-xs font-black text-[#52694d] underline-offset-4 hover:underline"
        >
          매물 상세 보기
        </Link>
      </div>
    </article>
  );
}

export function SellableCardRow({
  card,
  price,
  goldenId,
  busy,
  onPrice,
  onGolden,
  onSubmit,
}: {
  card: MarketSellableCard;
  price: string;
  goldenId?: number;
  busy: boolean;
  onPrice: (value: string) => void;
  onGolden: (value: number) => void;
  onSubmit: () => void;
}) {
  const validGolden = card.rarity !== "GOLDEN_RARE" || Boolean(goldenId);
  return (
    <div className="rounded-2xl border border-[#dce3d7] bg-white p-4">
      <div className="flex gap-4">
        <div className="h-28 w-20 shrink-0 overflow-hidden rounded-lg bg-[#eef1eb]">
          <CommerceCardImage src={card.imageUrl} name={card.cardName} />
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex items-center justify-between gap-3">
            <div>
              <h3 className="font-black">{card.cardName}</h3>
              <p className="mt-1 text-xs text-[#788273]">
                보유 {card.ownedCount}장 · 판매 가능 {card.sellableCount}장
              </p>
            </div>
            <span className="rounded-full bg-[#eee8f8] px-3 py-1 text-[10px] font-black text-[#684995]">
              {card.rarity === "GOLDEN_RARE" ? "GOLDEN" : "HYPER"}
            </span>
          </div>
          {card.rarity === "GOLDEN_RARE" ? (
            <select
              value={goldenId ?? ""}
              onChange={(event) => onGolden(Number(event.target.value))}
              className="mt-3 w-full rounded-xl border border-[#d3dacd] px-3 py-2 text-sm font-bold"
            >
              <option value="">판매할 골든 개체 선택</option>
              {card.goldenInstances
                .filter((item) => !item.listed)
                .map((item) => (
                  <option key={item.id} value={item.id}>
                    {card.cardName} · 개체 #{item.id}
                  </option>
                ))}
            </select>
          ) : null}
          <div className="mt-3 flex gap-2">
            <input
              type="number"
              min={100}
              max={99999999}
              value={price}
              onChange={(event) => onPrice(event.target.value)}
              placeholder="판매 가격 100P 이상"
              className="min-w-0 flex-1 rounded-xl border border-[#d3dacd] px-3 py-2 text-sm font-bold"
            />
            <button
              type="button"
              disabled={
                busy ||
                Number(price) < 100 ||
                Number(price) > 99999999 ||
                !validGolden
              }
              onClick={onSubmit}
              className="rounded-xl bg-[#344b32] px-4 text-xs font-black text-white disabled:opacity-35"
            >
              등록
            </button>
          </div>
          <p className="mt-2 text-[11px] text-[#8b947f]">
            판매 가능 가격 100P ~ 99,999,999P · 판매 완료 시 수수료 20%가
            차감됩니다.
          </p>
        </div>
      </div>
    </div>
  );
}

export function NegotiationCard({
  negotiation,
  userId,
  price,
  busy,
  onPrice,
  onAccept,
  onReject,
  onCancel,
  onPropose,
}: {
  negotiation: MarketNegotiation;
  userId: number;
  price: string;
  busy: boolean;
  onPrice: (value: string) => void;
  onAccept: () => void;
  onReject: () => void;
  onCancel: () => void;
  onPropose: () => void;
}) {
  const role = negotiation.buyerUserId === userId ? "BUYER" : "SELLER";
  const myTurn =
    negotiation.status === "NEGOTIATING" && negotiation.turn === role;
  return (
    <article className="rounded-[24px] border border-[#dce3d7] bg-white p-5 shadow-sm">
      <div className="flex gap-4">
        <div className="h-28 w-20 shrink-0 overflow-hidden rounded-lg bg-[#edf0e9]">
          <CommerceCardImage
            src={negotiation.imageUrl}
            name={negotiation.cardName}
          />
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex items-center justify-between gap-2">
            <h3 className="truncate font-black">{negotiation.cardName}</h3>
            <span className="rounded-full bg-[#eef2eb] px-3 py-1 text-[10px] font-black text-[#667260]">
              {STATUS_LABEL[negotiation.status] ?? negotiation.status}
            </span>
          </div>
          <p className="mt-2 text-sm text-[#747e70]">
            판매가 <strong>{formatPoint(negotiation.askingPrice)}</strong>
          </p>
          <p className="mt-1 text-xl font-black text-[#765b12]">
            현재 제안 {formatPoint(negotiation.currentPrice)}
          </p>
          <p className="mt-1 text-xs text-[#879083]">
            제안 금액 {formatPoint(negotiation.escrowedPaidPoint)} 보관 중 ·{" "}
            {formatCommerceDateTime(negotiation.expiresAt)}까지
          </p>
        </div>
      </div>
      <div className="mt-4 flex flex-wrap gap-2">
        {negotiation.proposals.map((proposal) => (
          <span
            key={proposal.id}
            className={`rounded-full px-3 py-1.5 text-xs font-bold ${proposal.proposerType === "BUYER" ? "bg-[#e9f1e5] text-[#496541]" : "bg-[#f7ebc9] text-[#785e18]"}`}
          >
            {proposal.proposerType === "BUYER" ? "구매자" : "판매자"}{" "}
            {formatPoint(proposal.proposedPrice)}
          </span>
        ))}
      </div>
      {myTurn ? (
        <div className="mt-5 border-t border-[#edf0e9] pt-4">
          <div className="flex gap-2">
            <input
              type="number"
              min={100}
              max={99999999}
              value={price}
              onChange={(event) => onPrice(event.target.value)}
              placeholder="새 가격"
              className="min-w-0 flex-1 rounded-xl border border-[#d3dacd] px-3 py-2 text-sm font-bold"
            />
            <button
              type="button"
              disabled={busy || Number(price) < 100}
              onClick={onPropose}
              className="rounded-xl bg-[#e7eee2] px-4 text-xs font-black text-[#405d3a] disabled:opacity-35"
            >
              역제안
            </button>
          </div>
          <div className="mt-2 grid grid-cols-2 gap-2">
            <button
              type="button"
              disabled={busy}
              onClick={onAccept}
              className="rounded-xl bg-[#344b32] py-3 text-xs font-black text-white"
            >
              현재 가격 수락
            </button>
            <button
              type="button"
              disabled={busy}
              onClick={onReject}
              className="rounded-xl border border-[#d8bcb2] py-3 text-xs font-black text-[#9c4d3c]"
            >
              거절
            </button>
          </div>
        </div>
      ) : null}
      {role === "BUYER" && negotiation.status === "NEGOTIATING" ? (
        <button
          type="button"
          disabled={busy}
          onClick={onCancel}
          className="mt-3 text-xs font-bold text-[#899184] underline"
        >
          내 제안 취소
        </button>
      ) : null}
    </article>
  );
}

export function MarketTradeModal({
  listing,
  offerPrice,
  busy,
  onOfferPrice,
  onOffer,
  onBuy,
  onClose,
}: {
  listing: MarketListing;
  offerPrice: string;
  busy: boolean;
  onOfferPrice: (value: string) => void;
  onOffer: (price: number) => void;
  onBuy: () => void;
  onClose: () => void;
}) {
  const numericOffer = Number(offerPrice);
  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-black/55 p-4 backdrop-blur-sm md:items-center"
      onMouseDown={onClose}
    >
      <div
        className="w-full max-w-lg rounded-[28px] bg-white p-6 shadow-2xl"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="flex gap-5">
          <div className="h-40 w-28 shrink-0 overflow-hidden rounded-xl bg-[#eef1eb]">
            <CommerceCardImage src={listing.imageUrl} name={listing.cardName} />
          </div>
          <div className="flex-1">
            <p className="text-xs font-black text-[#9a7a25]">
              {listing.assetType === "GOLDEN_RARE"
                ? "GOLDEN RARE"
                : "HYPER RARE"}
            </p>
            <h2 className="mt-1 text-2xl font-black">{listing.cardName}</h2>
            <p className="mt-3 text-sm text-[#788273]">
              판매자 {listing.sellerNickname}
            </p>
            <p className="mt-2 text-2xl font-black text-[#6e5618]">
              {formatPoint(listing.askingPrice)}
            </p>
          </div>
        </div>
        <div className="mt-6 rounded-2xl bg-[#f5f1df] p-4 text-sm font-bold leading-6 text-[#6f5b25]">
          구매와 가격 제안에는 거래 가능 포인트가 사용되며, 완료된 거래는 취소할
          수 없습니다.
        </div>
        <label className="mt-5 block text-sm font-black">
          더 저렴한 가격 제안
        </label>
        <div className="mt-2 flex gap-2">
          <input
            type="number"
            min={100}
            max={listing.askingPrice - 1}
            value={offerPrice}
            onChange={(event) => onOfferPrice(event.target.value)}
            placeholder="최소 100P"
            className="min-w-0 flex-1 rounded-xl border border-[#ced7c8] px-4 py-3 font-bold outline-none focus:border-[#5d7955]"
          />
          <button
            type="button"
            disabled={
              busy || numericOffer < 100 || numericOffer >= listing.askingPrice
            }
            onClick={() => onOffer(numericOffer)}
            className="rounded-xl bg-[#e7eee2] px-4 font-black text-[#405d3a] disabled:opacity-40"
          >
            제안
          </button>
        </div>
        <button
          type="button"
          disabled={busy}
          onClick={onBuy}
          className="mt-4 w-full rounded-2xl bg-[#344b32] py-4 font-black text-white shadow-lg disabled:opacity-40"
        >
          {formatPoint(listing.askingPrice)} 바로 구매
        </button>
      </div>
    </div>
  );
}
