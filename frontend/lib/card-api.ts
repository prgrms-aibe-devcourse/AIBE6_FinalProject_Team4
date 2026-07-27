import { request } from '@/lib/api';

export type CardStatus = 'ON_SALE' | 'HIDDEN';

export interface CardData {
  id: number;
  name: string;
  pointPrice: number;
  exchangeProductId: number;
  exchangeProductName: string;
  exchangeProductDescription: string | null;
  exchangeProductImageUrl: string | null;
  exchangeProductStock: number;
  requiredCountForExchange: number;
  description: string | null;
  imageUrl: string | null;
  status: CardStatus;
  createdAt: string;
  updatedAt: string;
  ownedCount: number | null;
}

export function getCards(
  accessToken?: string | null,
  signal?: AbortSignal,
): Promise<CardData[]> {
  return request<CardData[]>('/api/v1/cards', {
    accessToken,
    signal,
  });
}

export function getCard(
  cardId: number,
  accessToken?: string | null,
  signal?: AbortSignal,
): Promise<CardData> {
  return request<CardData>(`/api/v1/cards/${cardId}`, {
    accessToken,
    signal,
  });
}
