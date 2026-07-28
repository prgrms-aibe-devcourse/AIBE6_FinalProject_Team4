'use client';
// 키워볼래 — shared cross-page store (React Context + localStorage persistence)
// Mirrors the prototype store.js: single wallet, journal/plant/card counters.
import { createContext, useContext, useEffect, useRef, useState, useCallback, ReactNode } from 'react';
import { ApiError, AUTH_EXPIRED_EVENT } from '@/lib/api';
import { getWallet } from '@/features/point/api';

const KEY = 'kwb_store_v1';

export interface Wallet {
  free: number;
  paid: number;
}

export type NotificationType = 'DELIVERY' | 'POINT' | 'JOURNAL_REMINDER' | 'INQUIRY' | 'NOTICE';

export interface NotificationItem {
  id: number;
  type: NotificationType;
  title: string;
  content: string;
  date: string;
  unread: boolean;
  broken?: boolean;
}

export interface CurrentUser {
  id: number;
  email: string;
  nickname: string;
  name: string;
  role: string;
}

export interface StoreState {
  authed: boolean;
  accessToken: string | null;
  user: CurrentUser | null;
  wallet: Wallet;
  rewardedToday: boolean;
  wroteToday: boolean;
  growingCount: number;
  plantCount: number;
  readyCards: number;
  cartCount: number;
  lastReward: number;
  notifications: NotificationItem[];
}

export type StorePatch = Partial<StoreState> | ((s: StoreState) => Partial<StoreState>);

export interface StoreContextValue {
  state: StoreState;
  hydrated: boolean;
  authExpired: boolean;
  walletLoading: boolean;
  walletLoaded: boolean;
  walletError: string | null;
  set: (patch: StorePatch) => void;
  spend: (amount: number) => void;
  creditFree: (amount: number) => void;
  creditPaid: (amount: number) => void;
  reset: () => void;
  balance: number;
  unreadCount: number;
  markNotifRead: (id: number) => void;
  markAllNotifsRead: () => void;
  login: (accessToken: string, user: CurrentUser) => void;
  logout: () => void;
  refreshWallet: () => Promise<void>;
}

const EMPTY_WALLET: Wallet = { free: 0, paid: 0 };

const DEFAULTS: StoreState = {
  authed: false,
  accessToken: null,
  user: null,
  wallet: EMPTY_WALLET,
  rewardedToday: false,
  wroteToday: false,
  growingCount: 3,
  plantCount: 5,
  readyCards: 2,
  cartCount: 4,
  lastReward: 30,
  notifications: [
    { id: 1, type: 'DELIVERY', title: '주문하신 상품이 배송을 시작했어요 📦', content: 'ORD-20260709-0022 · 방울토마토 모종', date: '오늘', unread: true },
    { id: 2, type: 'POINT', title: '일지 보상 30P가 지급됐어요 ☀️', content: '토실이의 오늘 기록', date: '오늘', unread: true },
    { id: 3, type: 'JOURNAL_REMINDER', title: '오늘 쌈싸리의 모습을 남겨볼까요? 🌱', content: '아직 오늘의 일지를 쓰지 않으셨어요', date: '오늘', unread: true },
    { id: 4, type: 'INQUIRY', title: '문의하신 내용에 답변이 도착했어요 💬', content: '배송 관련 문의', date: '어제', unread: false, broken: false },
    { id: 5, type: 'NOTICE', title: '새로운 카드가 상점에 입고됐어요 📢', content: '감자 카드를 만나보세요', date: '어제', unread: false, broken: true },
  ],
};

const StoreCtx = createContext<StoreContextValue | null>(null);

export function StoreProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<StoreState>(DEFAULTS);
  const [hydrated, setHydrated] = useState(false);
  const [authExpired, setAuthExpired] = useState(false);
  const [walletLoading, setWalletLoading] = useState(false);
  const [walletLoaded, setWalletLoaded] = useState(false);
  const [walletError, setWalletError] = useState<string | null>(null);
  const walletRequestId = useRef(0);

  const clearAuthentication = useCallback((expired: boolean) => {
    walletRequestId.current += 1;
    setWalletLoading(false);
    setWalletLoaded(false);
    setWalletError(null);
    setAuthExpired(expired);
    setState((s) => ({
      ...s,
      authed: false,
      accessToken: null,
      user: null,
      wallet: EMPTY_WALLET,
    }));
  }, []);

  useEffect(() => {
    try {
      const raw = localStorage.getItem(KEY);
      if (raw) setState({ ...DEFAULTS, ...JSON.parse(raw), wallet: EMPTY_WALLET });
    } catch (e) {}
    setHydrated(true);
  }, []);

  useEffect(() => {
    if (!hydrated) return;
    try { localStorage.setItem(KEY, JSON.stringify({ ...state, wallet: EMPTY_WALLET })); } catch (e) {}
  }, [state, hydrated]);

  useEffect(() => {
    const handleAuthExpired = () => clearAuthentication(true);
    window.addEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired);
    return () => window.removeEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired);
  }, [clearAuthentication]);

  const refreshWallet = useCallback(async () => {
    const requestId = ++walletRequestId.current;
    if (!state.authed || !state.accessToken) {
      setState((s) => ({ ...s, wallet: EMPTY_WALLET }));
      setWalletLoading(false);
      setWalletLoaded(false);
      setWalletError(null);
      return;
    }

    setWalletLoading(true);
    setWalletError(null);
    try {
      const wallet = await getWallet(state.accessToken);
      if (requestId !== walletRequestId.current) return;
      setState((s) => ({
        ...s,
        wallet: { paid: wallet.paidPoint, free: wallet.freePoint },
      }));
      setWalletLoaded(true);
    } catch (error) {
      if (requestId !== walletRequestId.current) return;
      setWalletLoaded(false);
      setWalletError(
        error instanceof ApiError
          ? error.message
          : '포인트 잔액을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.',
      );
    } finally {
      if (requestId === walletRequestId.current) setWalletLoading(false);
    }
  }, [state.accessToken, state.authed]);

  useEffect(() => {
    if (!hydrated) return;
    void refreshWallet();
  }, [hydrated, refreshWallet]);

  const set = useCallback((patch: StorePatch) => {
    setState((s) => ({ ...s, ...(typeof patch === 'function' ? patch(s) : patch) }));
  }, []);

  // 무상 포인트 먼저 차감
  const spend = useCallback((amount: number) => {
    setState((s) => {
      let { free, paid } = s.wallet;
      const uf = Math.min(free, amount);
      free -= uf; paid -= (amount - uf);
      return { ...s, wallet: { free: Math.max(0, free), paid: Math.max(0, paid) } };
    });
  }, []);

  const creditFree = useCallback((amount: number) => {
    setState((s) => ({ ...s, wallet: { free: s.wallet.free + amount, paid: s.wallet.paid } }));
  }, []);

  const creditPaid = useCallback((amount: number) => {
    setState((s) => ({ ...s, wallet: { free: s.wallet.free, paid: s.wallet.paid + amount } }));
  }, []);

  const reset = useCallback(() => {
    walletRequestId.current += 1;
    setWalletLoading(false);
    setWalletLoaded(false);
    setWalletError(null);
    setAuthExpired(false);
    setState(DEFAULTS);
  }, []);

  const login = useCallback((accessToken: string, user: CurrentUser) => {
    walletRequestId.current += 1;
    setWalletLoading(false);
    setWalletLoaded(false);
    setWalletError(null);
    setAuthExpired(false);
    setState((s) => ({ ...s, authed: true, accessToken, user, wallet: EMPTY_WALLET }));
  }, []);
  const logout = useCallback(() => clearAuthentication(false), [clearAuthentication]);

  const markNotifRead = useCallback((id: number) => {
    setState((s) => ({ ...s, notifications: s.notifications.map((n) => (n.id === id ? { ...n, unread: false } : n)) }));
  }, []);

  const markAllNotifsRead = useCallback(() => {
    setState((s) => ({ ...s, notifications: s.notifications.map((n) => ({ ...n, unread: false })) }));
  }, []);

  const balance = state.wallet.free + state.wallet.paid;
  const unreadCount = state.notifications.filter((n) => n.unread).length;

  return (
    <StoreCtx.Provider
      value={{
        state,
        hydrated,
        authExpired,
        walletLoading,
        walletLoaded,
        walletError,
        set,
        spend,
        creditFree,
        creditPaid,
        reset,
        balance,
        unreadCount,
        markNotifRead,
        markAllNotifsRead,
        login,
        logout,
        refreshWallet,
      }}
    >
      {children}
    </StoreCtx.Provider>
  );
}

export function useStore(): StoreContextValue {
  const ctx = useContext(StoreCtx);
  if (!ctx) throw new Error('useStore must be used within <StoreProvider>');
  return ctx;
}

export const fmt = (n: number | string) => Number(n).toLocaleString('en-US');
