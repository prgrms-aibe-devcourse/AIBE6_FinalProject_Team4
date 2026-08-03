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

export interface AdminPointAdjustmentInput {
  userId: number;
  currencyType: PointCurrencyType;
  amount: number;
}

export interface AdminPointAdjustmentData extends AdminPointAdjustmentInput {
  transactionId: number;
  balanceAfter: number;
  paidPoint: number;
  freePoint: number;
  balance: number;
}

export type AdminPointAdjustmentDirection = 'GRANT' | 'DEDUCT';

export interface AdminPointAdjustmentHistoryData {
  transactionId: number;
  targetUserId: number;
  targetEmail: string;
  targetNickname: string;
  currencyType: PointCurrencyType;
  amount: number;
  balanceAfter: number;
  adminUserId: number | null;
  createdAt: string;
}

interface AdminPointAdjustmentHistoryParams {
  accessToken: string;
  userId?: number;
  currencyType?: PointCurrencyType;
  direction?: AdminPointAdjustmentDirection;
  from?: string;
  to?: string;
  page: number;
  size?: number;
  signal?: AbortSignal;
}

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

export interface PointActivity {
  id: number;
  type: PointTransactionType;
  refType: PointReferenceType | null;
  refId: number | null;
  amount: number;
  paidAmount: number;
  freeAmount: number;
  paidBalanceAfter: number | null;
  freeBalanceAfter: number | null;
  createdAt: string;
}

export type PointActivityPage = Omit<PointTransactionPage, 'content'> & {
  content: PointActivity[];
};

export type AdminPointAdjustmentHistoryPage = Omit<PointTransactionPage, 'content'> & {
  content: AdminPointAdjustmentHistoryData[];
};

interface PointTransactionParams {
  accessToken: string;
  type?: PointTransactionType;
  from?: string;
  to?: string;
  page: number;
  size?: number;
  signal?: AbortSignal;
}

interface PointActivityParams {
  accessToken: string;
  type?: PointTransactionType;
  refType?: PointReferenceType;
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

export function getPointActivities({
  accessToken,
  type,
  refType,
  from,
  to,
  page,
  size = 20,
  signal,
}: PointActivityParams): Promise<PointActivityPage> {
  const query = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  if (type) query.set('type', type);
  if (refType) query.set('refType', refType);
  if (from) query.set('from', from);
  if (to) query.set('to', to);

  return request<PointActivityPage>(`/api/v1/points/activities?${query.toString()}`, {
    accessToken,
    signal,
  });
}

export function adjustPointByAdmin(
  accessToken: string,
  payload: AdminPointAdjustmentInput,
  idempotencyKey: string,
): Promise<AdminPointAdjustmentData> {
  return request<AdminPointAdjustmentData>('/api/v1/admin/point/adjust', {
    method: 'POST',
    accessToken,
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify(payload),
  });
}

export function getWalletByAdmin(
  accessToken: string,
  userId: number,
): Promise<WalletData> {
  return request<WalletData>(`/api/v1/admin/point/user/${userId}/wallet`, {
    accessToken,
  });
}

export function getAdminPointAdjustments({
  accessToken,
  userId,
  currencyType,
  direction,
  from,
  to,
  page,
  size = 20,
  signal,
}: AdminPointAdjustmentHistoryParams): Promise<AdminPointAdjustmentHistoryPage> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (userId) query.set('userId', String(userId));
  if (currencyType) query.set('currencyType', currencyType);
  if (direction) query.set('direction', direction);
  if (from) query.set('from', from);
  if (to) query.set('to', to);

  return request<AdminPointAdjustmentHistoryPage>(
    `/api/v1/admin/point/adjustments?${query.toString()}`,
    { accessToken, signal },
  );
}
