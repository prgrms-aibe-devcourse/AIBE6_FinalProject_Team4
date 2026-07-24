import { request } from '@/lib/api';

export type ProductCategory = 'KIT' | 'SEEDLING';
export type ProductSort = 'LATEST' | 'PRICE_ASC' | 'PRICE_DESC';

export interface ProductListItem {
  id: number;
  name: string;
  category: ProductCategory;
  pointPrice: number;
  stock: number;
  soldOut: boolean;
  imageUrl: string | null;
}

export interface ProductPage {
  content: ProductListItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface PlantGuide {
  plantSpeciesId: number;
  name: string;
  category: string | null;
  careGuide: string | null;
}

export interface ProductDetail {
  id: number;
  name: string;
  category: ProductCategory;
  pointPrice: number;
  stock: number;
  soldOut: boolean;
  description: string | null;
  imageUrl: string | null;
  plantGuide: PlantGuide | null;
  createdAt: string;
  updatedAt: string;
}

interface ProductListParams {
  accessToken: string;
  category?: ProductCategory;
  sort: ProductSort;
  page: number;
  size?: number;
  signal?: AbortSignal;
}

export function getProducts({
  accessToken,
  category,
  sort,
  page,
  size = 20,
  signal,
}: ProductListParams): Promise<ProductPage> {
  const query = new URLSearchParams({
    sort,
    page: String(page),
    size: String(size),
  });
  if (category) query.set('category', category);

  return request<ProductPage>(`/api/v1/product?${query.toString()}`, {
    accessToken,
    signal,
  });
}

export function getProduct(
  productId: number,
  accessToken: string,
  signal?: AbortSignal,
): Promise<ProductDetail> {
  return request<ProductDetail>(`/api/v1/product/${productId}`, {
    accessToken,
    signal,
  });
}
