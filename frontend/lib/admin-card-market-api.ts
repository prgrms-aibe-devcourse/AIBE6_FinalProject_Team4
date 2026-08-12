import { request } from "@/lib/api";

export interface AdminCardMarketRevenueItem {
  tradeId: number;
  listingId: number;
  cardName: string;
  tradeType: "BUY_NOW" | "NEGOTIATED";
  sellerUserId: number;
  sellerNickname: string;
  buyerUserId: number;
  buyerNickname: string;
  tradePoint: number;
  feePoint: number;
  sellerReceivedPoint: number;
  completedAt: string;
}

export interface AdminCardMarketRevenue {
  totalTradeCount: number;
  totalTradePoint: number;
  totalFeePoint: number;
  totalSellerReceivedPoint: number;
  content: AdminCardMarketRevenueItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export function getAdminCardMarketRevenue(
  accessToken: string,
  params: { page?: number; size?: number; signal?: AbortSignal } = {},
) {
  const query = new URLSearchParams({
    page: String(params.page ?? 0),
    size: String(params.size ?? 20),
  });
  return request<AdminCardMarketRevenue>(
    `/api/v1/admin/card/market/revenue?${query}`,
    { accessToken, signal: params.signal },
  );
}
