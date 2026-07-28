import { request } from '@/lib/api';

export interface WalletData {
  userId: number;
  balance: number;
  paidPoint: number;
  freePoint: number;
  updatedAt: string;
}

export function getWallet(accessToken: string): Promise<WalletData> {
  return request<WalletData>('/api/v1/points/wallet', {
    accessToken,
  });
}
