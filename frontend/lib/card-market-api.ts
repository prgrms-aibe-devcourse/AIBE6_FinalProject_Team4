import { request } from "@/lib/api";
import type { GachaRarity } from "@/lib/gacha-api";

export type MarketAssetType = "HYPER_RARE" | "GOLDEN_RARE";
export type MarketListingStatus = "OPEN" | "SOLD" | "CANCELLED" | "EXPIRED";
export type MarketNegotiationStatus =
  | "NEGOTIATING"
  | "ACCEPTED"
  | "REJECTED"
  | "CANCELLED"
  | "EXPIRED"
  | "LISTING_CLOSED";
export type MarketParticipantType = "BUYER" | "SELLER";
export type MarketMessageCode =
  "PRICE_ADJUST_REQUEST" | "READY_TO_BUY" | "MAXIMUM_OFFER" | "CONSIDERING";

export interface MarketListing {
  id: number;
  sellerUserId: number;
  sellerNickname: string;
  cardId: number;
  goldenInstanceId: number | null;
  cardCode: string;
  cardName: string;
  rarity: GachaRarity;
  imageUrl: string | null;
  assetType: MarketAssetType;
  askingPrice: number;
  status: MarketListingStatus;
  activeOfferCount: number;
  expiresAt: string;
  createdAt: string;
}

export interface MarketPage<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface MarketProposal {
  id: number;
  proposerUserId: number;
  proposerType: MarketParticipantType;
  sequenceNo: number;
  proposedPrice: number;
  messageCode: MarketMessageCode | null;
  createdAt: string;
}

export interface MarketNegotiation {
  id: number;
  listingId: number;
  buyerUserId: number;
  buyerNickname: string;
  sellerUserId: number;
  cardId: number;
  cardName: string;
  imageUrl: string | null;
  askingPrice: number;
  status: MarketNegotiationStatus;
  turn: MarketParticipantType;
  currentProposerType: MarketParticipantType;
  currentPrice: number;
  escrowedPaidPoint: number;
  expiresAt: string;
  proposals: MarketProposal[];
}

export interface MarketTrade {
  id: number;
  listingId: number;
  sellerUserId: number;
  buyerUserId: number;
  cardId: number;
  goldenInstanceId: number | null;
  cardCode: string;
  cardName: string;
  rarity: GachaRarity;
  imageUrl: string | null;
  tradeType: "BUY_NOW" | "NEGOTIATED";
  askingPrice: number;
  tradePrice: number;
  feeRateBps: number;
  feePoint: number;
  sellerReceivedPoint: number;
  completedAt: string;
}

export interface MarketWallet {
  paidPoint: number;
  freePoint: number;
  escrowedPaidPoint: number;
  paidPointGuide: string;
  freePointGuide: string;
}

export interface MarketSellableCard {
  cardId: number;
  cardName: string;
  rarity: GachaRarity;
  imageUrl: string | null;
  ownedCount: number;
  sellableCount: number;
  goldenInstances: { id: number; originRank: number | null; listed: boolean }[];
}

export const marketIdempotencyKey = () => crypto.randomUUID();

export function getMarketListings(
  assetType?: MarketAssetType,
  page = 0,
  signal?: AbortSignal,
) {
  const params = new URLSearchParams({
    page: String(page),
    size: "20",
    sort: "createdAt,desc",
  });
  if (assetType) params.set("assetType", assetType);
  return request<MarketPage<MarketListing>>(
    `/api/v1/card/market/listings?${params}`,
    { signal },
  );
}

export function getMarketWallet(accessToken: string, signal?: AbortSignal) {
  return request<MarketWallet>("/api/v1/card/market/me/wallet", {
    accessToken,
    signal,
  });
}

export function getMarketSellableCards(
  accessToken: string,
  signal?: AbortSignal,
) {
  return request<MarketSellableCard[]>(
    "/api/v1/card/market/me/sellable-cards",
    { accessToken, signal },
  );
}

export function getMyMarketListings(accessToken: string, signal?: AbortSignal) {
  return request<MarketListing[]>("/api/v1/card/market/me/listings", {
    accessToken,
    signal,
  });
}

export function getMyMarketNegotiations(
  direction: "sent" | "received",
  accessToken: string,
  signal?: AbortSignal,
) {
  return request<MarketNegotiation[]>(
    `/api/v1/card/market/me/negotiations/${direction}`,
    {
      accessToken,
      signal,
    },
  );
}

export function getMyMarketTrades(accessToken: string, signal?: AbortSignal) {
  return request<MarketTrade[]>("/api/v1/card/market/me/trades", {
    accessToken,
    signal,
  });
}

export function createMarketListing(
  cardId: number,
  goldenInstanceId: number | null,
  askingPrice: number,
  accessToken: string,
) {
  return request<MarketListing>("/api/v1/card/market/listings", {
    method: "POST",
    accessToken,
    headers: { "Idempotency-Key": marketIdempotencyKey() },
    body: JSON.stringify({ cardId, goldenInstanceId, askingPrice }),
  });
}

export function cancelMarketListing(listingId: number, accessToken: string) {
  return request<MarketListing>(`/api/v1/card/market/listings/${listingId}`, {
    method: "DELETE",
    accessToken,
    headers: { "Idempotency-Key": marketIdempotencyKey() },
  });
}

export function buyMarketListing(listingId: number, accessToken: string) {
  return request<MarketTrade>(
    `/api/v1/card/market/listings/${listingId}/purchases`,
    {
      method: "POST",
      accessToken,
      headers: { "Idempotency-Key": marketIdempotencyKey() },
    },
  );
}

export function createMarketNegotiation(
  listingId: number,
  price: number,
  messageCode: MarketMessageCode | null,
  accessToken: string,
) {
  return request<MarketNegotiation>(
    `/api/v1/card/market/listings/${listingId}/negotiations`,
    {
      method: "POST",
      accessToken,
      headers: { "Idempotency-Key": marketIdempotencyKey() },
      body: JSON.stringify({ price, messageCode }),
    },
  );
}

export function proposeMarketPrice(
  negotiationId: number,
  price: number,
  messageCode: MarketMessageCode | null,
  accessToken: string,
) {
  return request<MarketNegotiation>(
    `/api/v1/card/market/negotiations/${negotiationId}/proposals`,
    {
      method: "POST",
      accessToken,
      headers: { "Idempotency-Key": marketIdempotencyKey() },
      body: JSON.stringify({ price, messageCode }),
    },
  );
}

export function acceptMarketNegotiation(
  negotiationId: number,
  accessToken: string,
) {
  return request<MarketTrade>(
    `/api/v1/card/market/negotiations/${negotiationId}/acceptances`,
    {
      method: "POST",
      accessToken,
      headers: { "Idempotency-Key": marketIdempotencyKey() },
    },
  );
}

export function rejectMarketNegotiation(
  negotiationId: number,
  accessToken: string,
) {
  return request<MarketNegotiation>(
    `/api/v1/card/market/negotiations/${negotiationId}/rejections`,
    {
      method: "POST",
      accessToken,
      headers: { "Idempotency-Key": marketIdempotencyKey() },
    },
  );
}

export function cancelMarketNegotiation(
  negotiationId: number,
  accessToken: string,
) {
  return request<MarketNegotiation>(
    `/api/v1/card/market/negotiations/${negotiationId}`,
    {
      method: "DELETE",
      accessToken,
      headers: { "Idempotency-Key": marketIdempotencyKey() },
    },
  );
}
