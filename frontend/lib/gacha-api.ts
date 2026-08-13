import { request } from "@/lib/api";

export type GachaRarity =
  "COMMON" | "RARE" | "SUPER_RARE" | "HYPER_RARE" | "GOLDEN_RARE";

export type GachaDrawStatus =
  | "PENDING"
  | "PROCESSING"
  | "COMPLETED"
  | "RETRYABLE_FAILED"
  | "MANUAL_REVIEW"
  | "REFUNDED";

export interface GachaCard {
  id: number;
  code: string;
  name: string;
  rarity: GachaRarity;
  description: string | null;
  imageUrl: string | null;
  displayOrder: number;
}

export interface GachaCollectionCard extends GachaCard {
  ownedCount: number;
  dismantleableCount: number;
  shardPerCard: number;
  owned: boolean;
  unlocked: boolean;
  goldenGachaAcquired: boolean;
}

export type GachaCosmeticType = "TITLE" | "BORDER";

export interface GachaShardWallet {
  balance: number;
  lifetimeEarned: number;
  lifetimeSpent: number;
}

export interface GachaCosmetic {
  code: string;
  name: string;
  type: GachaCosmeticType;
  price: number;
  styleKey: string;
  owned: boolean;
  equipped: boolean;
  unlockedAt: string | null;
}

export interface GachaMyCosmetics {
  shards: GachaShardWallet;
  cosmetics: GachaCosmetic[];
}

export interface GachaDismantleData {
  earnedShards: number;
  balance: number;
  lifetimeEarned: number;
  items: {
    cardId: number;
    cardName: string;
    quantity: number;
    shardPerCard: number;
    earnedShards: number;
    ownedCountAfter: number;
  }[];
}

export interface GachaRateData {
  rateVersion: number;
  drawCount: number;
  totalWeight: number;
  rarities: { rarity: GachaRarity; weight: number; percent: number }[];
  notices: string[];
}

export interface GachaDrawSummary {
  drawId: number;
  status: GachaDrawStatus;
  sourceType: "LOG_REWARD" | "PURCHASE" | "EVENT" | "ADMIN";
  drawCount: number;
  createdAt: string;
  completedAt: string | null;
  resultViewedAt: string | null;
}

export interface GachaDrawPage {
  content: GachaDrawSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface GachaDrawItem {
  sequence: number;
  cardId: number;
  code: string;
  name: string;
  imageUrl: string | null;
  rolledRarity: GachaRarity;
  finalRarity: GachaRarity;
  downgraded: boolean;
  new: boolean;
  ownedCountAfter: number;
  nextMilestone: number | null;
  goldenOriginRank: number | null;
}

export interface GachaDrawDetail {
  drawId: number;
  status: GachaDrawStatus;
  sourceType: GachaDrawSummary["sourceType"];
  rateVersion: number;
  createdAt: string;
  completedAt: string | null;
  resultViewedAt: string | null;
  items: GachaDrawItem[];
}

export interface GachaPackPurchaseData {
  purchaseId: number;
  productId: number;
  productName: string;
  quantity: number;
  unitPoint: number;
  totalPoint: number;
  usedFreePoint: number;
  usedPaidPoint: number;
  remainingBalance: number;
  drawIds: number[];
}

export interface GachaTestCardGrantData {
  cardId: number;
  cardName: string;
  rarity: "HYPER_RARE" | "GOLDEN_RARE";
  grantedQuantity: number;
  ownedCountAfter: number;
}

export const GACHA_COLLECTION_CHANGED_EVENT = "kwb:gacha-collection-changed";

export function getGachaCatalog(
  rarity?: GachaRarity,
  signal?: AbortSignal,
): Promise<GachaCard[]> {
  const query = rarity ? `?rarity=${rarity}` : "";
  return request<GachaCard[]>(`/api/v1/card/gacha/catalog${query}`, { signal });
}

export function getGachaRates(signal?: AbortSignal): Promise<GachaRateData> {
  return request<GachaRateData>("/api/v1/card/gacha/rates", { signal });
}

export function getMyGachaCollection(
  accessToken: string,
  signal?: AbortSignal,
): Promise<GachaCollectionCard[]> {
  return request<GachaCollectionCard[]>("/api/v1/card/gacha/me/collection", {
    accessToken,
    signal,
  });
}

export function getMyGachaShards(
  accessToken: string,
  signal?: AbortSignal,
): Promise<GachaShardWallet> {
  return request<GachaShardWallet>("/api/v1/card/gacha/me/shards", {
    accessToken,
    signal,
  });
}

export function dismantleGachaCards(
  items: { cardId: number; quantity: number }[],
  accessToken: string,
  idempotencyKey: string,
): Promise<GachaDismantleData> {
  return request<GachaDismantleData>("/api/v1/card/gacha/me/dismantles", {
    method: "POST",
    accessToken,
    headers: { "Idempotency-Key": idempotencyKey },
    body: JSON.stringify({ items }),
  });
}

export function getGachaCosmetics(
  signal?: AbortSignal,
): Promise<GachaCosmetic[]> {
  return request<GachaCosmetic[]>("/api/v1/card/gacha/cosmetics", { signal });
}

export function getMyGachaCosmetics(
  accessToken: string,
  signal?: AbortSignal,
): Promise<GachaMyCosmetics> {
  return request<GachaMyCosmetics>("/api/v1/card/gacha/me/cosmetics", {
    accessToken,
    signal,
  });
}

export function purchaseGachaCosmetic(
  code: string,
  accessToken: string,
  idempotencyKey: string,
): Promise<GachaMyCosmetics> {
  return request<GachaMyCosmetics>(
    `/api/v1/card/gacha/me/cosmetics/${encodeURIComponent(code)}/purchases`,
    {
      method: "POST",
      accessToken,
      headers: { "Idempotency-Key": idempotencyKey },
    },
  );
}

export function equipGachaCosmetic(
  code: string,
  accessToken: string,
): Promise<GachaMyCosmetics> {
  return request<GachaMyCosmetics>(
    `/api/v1/card/gacha/me/cosmetics/${encodeURIComponent(code)}/equipped`,
    { method: "PATCH", accessToken },
  );
}

export function unequipGachaCosmetic(
  type: GachaCosmeticType,
  accessToken: string,
): Promise<GachaMyCosmetics> {
  return request<GachaMyCosmetics>(
    `/api/v1/card/gacha/me/cosmetics/${type}/equipped`,
    { method: "DELETE", accessToken },
  );
}

export function getGachaDraws(
  accessToken: string,
  viewed?: boolean,
  page = 0,
  signal?: AbortSignal,
): Promise<GachaDrawPage> {
  const params = new URLSearchParams({ page: String(page), size: "20" });
  if (viewed !== undefined) params.set("viewed", String(viewed));
  return request<GachaDrawPage>(`/api/v1/card/gacha/draws?${params}`, {
    accessToken,
    signal,
  });
}

export function getGachaDraw(
  drawId: number,
  accessToken: string,
  signal?: AbortSignal,
): Promise<GachaDrawDetail> {
  return request<GachaDrawDetail>(`/api/v1/card/gacha/draws/${drawId}`, {
    accessToken,
    signal,
  });
}

export function markGachaDrawViewed(
  drawId: number,
  accessToken: string,
): Promise<GachaDrawDetail> {
  return request<GachaDrawDetail>(`/api/v1/card/gacha/draws/${drawId}/viewed`, {
    method: "PATCH",
    accessToken,
    keepalive: true,
  });
}

export function purchaseGachaPacks(
  productId: number,
  quantity: number,
  expectedUnitPoint: number,
  accessToken: string,
  idempotencyKey: string,
): Promise<GachaPackPurchaseData> {
  return request<GachaPackPurchaseData>("/api/v1/card/gacha/purchases", {
    method: "POST",
    accessToken,
    headers: {
      "Idempotency-Key": idempotencyKey,
    },
    body: JSON.stringify({ productId, quantity, expectedUnitPoint }),
  });
}

export function grantLocalTestGachaCard(
  rarity: "HYPER_RARE" | "GOLDEN_RARE",
  quantity: 1 | 2,
  accessToken: string,
): Promise<GachaTestCardGrantData> {
  return request<GachaTestCardGrantData>("/api/v1/card/gacha/me/test-cards", {
    method: "POST",
    accessToken,
    body: JSON.stringify({ rarity, quantity }),
  });
}
