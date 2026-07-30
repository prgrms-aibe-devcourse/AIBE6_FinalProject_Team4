import { request } from '@/lib/api';

export interface WalletData {
  userId: number;
  balance: number;
  paidPoint: number;
  freePoint: number;
  updatedAt: string;
}

export type PointTransactionType =
  | 'CHARGE'
  | 'JOURNAL_REWARD'
  | 'PURCHASE'
  | 'RESTORE'
  | 'REFUND'
  | 'ADMIN_ADJUST';

export type PointCurrencyType = 'FREE' | 'PAID';

export type PointReferenceType =
  | 'ORDER'
  | 'CARD_PURCHASE'
  | 'PAYMENT'
  | 'PAYMENT_REFUND'
  | 'JOURNAL_COMPLETION'
  | 'ADMIN';

export interface PointTransaction {
  id: number;
  walletId: number;
  type: PointTransactionType;
  currencyType: PointCurrencyType;
  amount: number;
  balanceAfter: number;
  refType: PointReferenceType | null;
  refId: number | null;
  createdAt: string;
}

export interface PointTransactionPage {
  content: PointTransaction[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  numberOfElements: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

interface PointTransactionParams {
  accessToken: string;
  type?: PointTransactionType;
  from?: string;
  to?: string;
  page: number;
  size?: number;
  signal?: AbortSignal;
}

export function getWallet(accessToken: string): Promise<WalletData> {
  return request<WalletData>('/api/v1/points/wallet', {
    accessToken,
  });
}

export function getPointTransactions({
  accessToken,
  type,
  from,
  to,
  page,
  size = 20,
  signal,
}: PointTransactionParams): Promise<PointTransactionPage> {
  const query = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  query.append('sort', 'createdAt,desc');
  query.append('sort', 'id,desc');
  if (type) query.set('type', type);
  if (from) query.set('from', from);
  if (to) query.set('to', to);

  return request<PointTransactionPage>(`/api/v1/points/transactions?${query.toString()}`, {
    accessToken,
    signal,
  });
}
