import { API_BASE_URL, ApiError, request } from "@/lib/api";

export interface AdminCardMarketFilters {
  from?: string;
  to?: string;
  userId?: string;
  cardId?: string;
  tradeType?: "BUY_NOW" | "NEGOTIATED" | "";
  keyword?: string;
}

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
  params: {
    page?: number;
    size?: number;
    signal?: AbortSignal;
    filters?: AdminCardMarketFilters;
  } = {},
) {
  const query = new URLSearchParams({
    page: String(params.page ?? 0),
    size: String(params.size ?? 20),
  });
  appendFilters(query, params.filters);
  return request<AdminCardMarketRevenue>(
    `/api/v1/admin/card/market/revenue?${query}`,
    { accessToken, signal: params.signal },
  );
}

export async function downloadAdminCardMarketRevenueCsv(
  accessToken: string,
  filters: AdminCardMarketFilters,
) {
  const query = new URLSearchParams();
  appendFilters(query, filters);
  const response = await fetch(
    `${API_BASE_URL}/api/v1/admin/card/market/revenue.csv?${query}`,
    {
      headers: { Authorization: `Bearer ${accessToken}` },
      credentials: "include",
    },
  );
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new ApiError(
      body?.code ?? "UNKNOWN_ERROR",
      body?.message ?? "CSV 파일을 만들지 못했어요.",
      response.status,
    );
  }
  return response.blob();
}

function appendFilters(
  query: URLSearchParams,
  filters?: AdminCardMarketFilters,
) {
  if (!filters) return;
  Object.entries(filters).forEach(([key, value]) => {
    if (value != null && String(value).trim())
      query.set(key, String(value).trim());
  });
}
