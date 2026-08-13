import { request } from "@/lib/api";
import { ProductCategory } from "@/lib/product-api";

export type AdminProductStatus = "ACTIVE" | "HIDDEN";

export interface AdminProduct {
  id: number;
  name: string;
  category: ProductCategory;
  pointPrice: number;
  stock: number;
  unlimitedStock: boolean;
  soldOut: boolean;
  plantId: number | null;
  description: string | null;
  imageKey: string | null;
  imageUrl: string | null;
  status: AdminProductStatus;
  createdAt: string;
  updatedAt: string;
}

export interface AdminProductInput {
  name: string;
  category: ProductCategory;
  pointPrice: number;
  stock: number;
  plantId: number | null;
  description: string | null;
  imageUrl: string | null;
}

export function getAdminProducts(
  accessToken: string,
  signal?: AbortSignal,
): Promise<AdminProduct[]> {
  return request<AdminProduct[]>("/api/v1/admin/product", {
    accessToken,
    signal,
  });
}

export function createAdminProduct(
  input: AdminProductInput,
  accessToken: string,
): Promise<AdminProduct> {
  return request<AdminProduct>("/api/v1/admin/product", {
    method: "POST",
    accessToken,
    body: JSON.stringify(input),
  });
}

export function updateAdminProduct(
  productId: number,
  input: AdminProductInput,
  accessToken: string,
): Promise<AdminProduct> {
  return request<AdminProduct>(`/api/v1/admin/product/${productId}`, {
    method: "PUT",
    accessToken,
    body: JSON.stringify(input),
  });
}

export function adjustAdminProductStock(
  productId: number,
  delta: number,
  accessToken: string,
): Promise<AdminProduct> {
  return request<AdminProduct>(`/api/v1/admin/product/${productId}/stock`, {
    method: "PATCH",
    accessToken,
    body: JSON.stringify({ delta }),
  });
}

export function changeAdminProductStatus(
  productId: number,
  status: AdminProductStatus,
  accessToken: string,
): Promise<AdminProduct> {
  return request<AdminProduct>(`/api/v1/admin/product/${productId}/status`, {
    method: "PATCH",
    accessToken,
    body: JSON.stringify({ status }),
  });
}

export function hideAdminProduct(
  productId: number,
  accessToken: string,
): Promise<AdminProduct> {
  return request<AdminProduct>(`/api/v1/admin/product/${productId}`, {
    method: "DELETE",
    accessToken,
  });
}

export function uploadAdminProductImage(
  productId: number,
  file: File,
  accessToken: string,
): Promise<AdminProduct> {
  const body = new FormData();
  body.append("file", file);
  return request<AdminProduct>(`/api/v1/admin/product/${productId}/image`, {
    method: "POST",
    accessToken,
    body,
  });
}
