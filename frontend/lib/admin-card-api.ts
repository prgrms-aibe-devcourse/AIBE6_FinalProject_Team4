import { request } from "@/lib/api";

export type AdminCardStatus = "ON_SALE" | "HIDDEN";

export interface AdminCard {
  id: number;
  name: string;
  pointPrice: number;
  exchangeProductId: number;
  exchangeProductName: string;
  requiredCountForExchange: number;
  description: string | null;
  imageKey: string | null;
  imageUrl: string | null;
  status: AdminCardStatus;
  createdAt: string;
  updatedAt: string;
}

export interface AdminCardInput {
  name: string;
  pointPrice: number;
  exchangeProductId: number;
  requiredCountForExchange: number;
  description: string | null;
  imageUrl: string | null;
  status: AdminCardStatus;
}

export interface AdminExchangeProductOption {
  id: number;
  name: string;
  stock: number;
}

export function getAdminCards(
  accessToken: string,
  signal?: AbortSignal,
): Promise<AdminCard[]> {
  return request<AdminCard[]>("/api/v1/admin/card", { accessToken, signal });
}

export function getAdminExchangeProductOptions(
  accessToken: string,
  signal?: AbortSignal,
): Promise<AdminExchangeProductOption[]> {
  return request<AdminExchangeProductOption[]>(
    "/api/v1/admin/card/exchange-products",
    { accessToken, signal },
  );
}

export function createAdminCard(
  input: AdminCardInput,
  accessToken: string,
): Promise<AdminCard> {
  return request<AdminCard>("/api/v1/admin/card", {
    method: "POST",
    accessToken,
    body: JSON.stringify(input),
  });
}

export function updateAdminCard(
  cardId: number,
  input: AdminCardInput,
  accessToken: string,
): Promise<AdminCard> {
  return request<AdminCard>(`/api/v1/admin/card/${cardId}`, {
    method: "PUT",
    accessToken,
    body: JSON.stringify(input),
  });
}

export function changeAdminCardStatus(
  cardId: number,
  status: AdminCardStatus,
  accessToken: string,
): Promise<AdminCard> {
  return request<AdminCard>(`/api/v1/admin/card/${cardId}/status`, {
    method: "PATCH",
    accessToken,
    body: JSON.stringify({ status }),
  });
}

export function hideAdminCard(
  cardId: number,
  accessToken: string,
): Promise<AdminCard> {
  return request<AdminCard>(`/api/v1/admin/card/${cardId}`, {
    method: "DELETE",
    accessToken,
  });
}

export function uploadAdminCardImage(
  cardId: number,
  file: File,
  accessToken: string,
): Promise<AdminCard> {
  const body = new FormData();
  body.append("file", file);
  return request<AdminCard>(`/api/v1/admin/card/${cardId}/image`, {
    method: "POST",
    accessToken,
    body,
  });
}
