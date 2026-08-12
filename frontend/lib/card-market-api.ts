import { ApiError, request } from "@/lib/api";
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

const MARKET_IDEMPOTENCY_PREFIX = "kwb:card-market:idempotency:";
const pendingIdempotencyKeys = new Map<string, string>();

function getPendingIdempotencyKey(operation: string) {
  const cached = pendingIdempotencyKeys.get(operation);
  if (cached) return cached;

  const storageKey = MARKET_IDEMPOTENCY_PREFIX + operation;
  const stored =
    typeof window === "undefined"
      ? null
      : window.sessionStorage.getItem(storageKey);
  const key = stored ?? crypto.randomUUID();
  pendingIdempotencyKeys.set(operation, key);
  if (typeof window !== "undefined") {
    window.sessionStorage.setItem(storageKey, key);
  }
  return key;
}

function clearPendingIdempotencyKey(operation: string) {
  pendingIdempotencyKeys.delete(operation);
  if (typeof window !== "undefined") {
    window.sessionStorage.removeItem(MARKET_IDEMPOTENCY_PREFIX + operation);
  }
}

async function marketMutation<T>(
  operation: string,
  execute: (idempotencyKey: string) => Promise<T>,
) {
  const key = getPendingIdempotencyKey(operation);
  try {
    const response = await execute(key);
    clearPendingIdempotencyKey(operation);
    return response;
  } catch (error) {
    // 응답 유실·서버 오류·처리 중 응답은 결과가 불명확하므로 같은 키로 재시도한다.
    if (
      error instanceof ApiError &&
      error.status < 500 &&
      error.code !== "COMMON_IDEMPOTENCY_IN_PROGRESS"
    ) {
      clearPendingIdempotencyKey(operation);
    }
    throw error;
  }
}

export function getMarketListings(
  options: {
    assetType?: MarketAssetType;
    keyword?: string;
    sort?: "createdAt,desc" | "askingPrice,asc" | "askingPrice,desc";
    page?: number;
    signal?: AbortSignal;
  } = {},
) {
  const params = new URLSearchParams({
    page: String(options.page ?? 0),
    size: "20",
    sort: options.sort ?? "createdAt,desc",
  });
  if (options.assetType) params.set("assetType", options.assetType);
  if (options.keyword?.trim()) params.set("keyword", options.keyword.trim());
  return request<MarketPage<MarketListing>>(
    `/api/v1/card/market/listings?${params}`,
    { signal: options.signal },
  );
}

export function getMarketListing(listingId: number, signal?: AbortSignal) {
  return request<MarketListing>(`/api/v1/card/market/listings/${listingId}`, {
    signal,
  });
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

export function getMyMarketListings(
  accessToken: string,
  page = 0,
  signal?: AbortSignal,
  status?: MarketListingStatus,
) {
  const params = new URLSearchParams({ page: String(page), size: "20" });
  if (status) params.set("status", status);
  return request<MarketPage<MarketListing>>(
    `/api/v1/card/market/me/listings?${params}`,
    { accessToken, signal },
  );
}

export function getMyMarketNegotiations(
  direction: "sent" | "received",
  accessToken: string,
  page = 0,
  signal?: AbortSignal,
) {
  return request<MarketPage<MarketNegotiation>>(
    `/api/v1/card/market/me/negotiations/${direction}?page=${page}&size=20`,
    {
      accessToken,
      signal,
    },
  );
}

export function getMyMarketNegotiation(
  negotiationId: number,
  accessToken: string,
  signal?: AbortSignal,
) {
  return request<MarketNegotiation>(
    `/api/v1/card/market/me/negotiations/${negotiationId}`,
    { accessToken, signal },
  );
}

export function getMyMarketTrades(
  accessToken: string,
  page = 0,
  signal?: AbortSignal,
) {
  return request<MarketPage<MarketTrade>>(
    `/api/v1/card/market/me/trades?page=${page}&size=20`,
    { accessToken, signal },
  );
}

export function createMarketListing(
  cardId: number,
  goldenInstanceId: number | null,
  askingPrice: number,
  accessToken: string,
) {
  const operation = `listing:create:${cardId}:${goldenInstanceId ?? "none"}:${askingPrice}`;
  return marketMutation(operation, (idempotencyKey) =>
    request<MarketListing>("/api/v1/card/market/listings", {
      method: "POST",
      accessToken,
      headers: { "Idempotency-Key": idempotencyKey },
      body: JSON.stringify({ cardId, goldenInstanceId, askingPrice }),
    }),
  );
}

export function cancelMarketListing(listingId: number, accessToken: string) {
  return marketMutation(`listing:cancel:${listingId}`, (idempotencyKey) =>
    request<MarketListing>(`/api/v1/card/market/listings/${listingId}`, {
      method: "DELETE",
      accessToken,
      headers: { "Idempotency-Key": idempotencyKey },
    }),
  );
}

export function buyMarketListing(listingId: number, accessToken: string) {
  return marketMutation(`listing:buy:${listingId}`, (idempotencyKey) =>
    request<MarketTrade>(
      `/api/v1/card/market/listings/${listingId}/purchases`,
      {
        method: "POST",
        accessToken,
        headers: { "Idempotency-Key": idempotencyKey },
      },
    ),
  );
}

export function createMarketNegotiation(
  listingId: number,
  price: number,
  messageCode: MarketMessageCode | null,
  accessToken: string,
) {
  const operation = `negotiation:create:${listingId}:${price}:${messageCode ?? "none"}`;
  return marketMutation(operation, (idempotencyKey) =>
    request<MarketNegotiation>(
      `/api/v1/card/market/listings/${listingId}/negotiations`,
      {
        method: "POST",
        accessToken,
        headers: { "Idempotency-Key": idempotencyKey },
        body: JSON.stringify({ price, messageCode }),
      },
    ),
  );
}

export function proposeMarketPrice(
  negotiationId: number,
  price: number,
  messageCode: MarketMessageCode | null,
  accessToken: string,
) {
  const operation = `negotiation:propose:${negotiationId}:${price}:${messageCode ?? "none"}`;
  return marketMutation(operation, (idempotencyKey) =>
    request<MarketNegotiation>(
      `/api/v1/card/market/negotiations/${negotiationId}/proposals`,
      {
        method: "POST",
        accessToken,
        headers: { "Idempotency-Key": idempotencyKey },
        body: JSON.stringify({ price, messageCode }),
      },
    ),
  );
}

export function acceptMarketNegotiation(
  negotiationId: number,
  accessToken: string,
) {
  return marketMutation(
    `negotiation:accept:${negotiationId}`,
    (idempotencyKey) =>
      request<MarketTrade>(
        `/api/v1/card/market/negotiations/${negotiationId}/acceptances`,
        {
          method: "POST",
          accessToken,
          headers: { "Idempotency-Key": idempotencyKey },
        },
      ),
  );
}

export function rejectMarketNegotiation(
  negotiationId: number,
  accessToken: string,
) {
  return marketMutation(
    `negotiation:reject:${negotiationId}`,
    (idempotencyKey) =>
      request<MarketNegotiation>(
        `/api/v1/card/market/negotiations/${negotiationId}/rejections`,
        {
          method: "POST",
          accessToken,
          headers: { "Idempotency-Key": idempotencyKey },
        },
      ),
  );
}

export function cancelMarketNegotiation(
  negotiationId: number,
  accessToken: string,
) {
  return marketMutation(
    `negotiation:cancel:${negotiationId}`,
    (idempotencyKey) =>
      request<MarketNegotiation>(
        `/api/v1/card/market/negotiations/${negotiationId}`,
        {
          method: "DELETE",
          accessToken,
          headers: { "Idempotency-Key": idempotencyKey },
        },
      ),
  );
}
