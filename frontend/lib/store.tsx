'use client';
// 키워볼래 — shared cross-page store (React Context + localStorage persistence)
// Mirrors the prototype store.js: single wallet, journal/plant/card counters.
//
// accessToken/user/authed are persisted to localStorage along with everything
// else, so a page reload keeps the session without any silent-refresh round
// trip. lib/api.ts still keeps its own in-memory copy of the access token
// (kept in sync via setAccessToken below) purely so it can attach the
// Authorization header without importing the store.
import { createContext, useContext, useEffect, useRef, useState, useCallback, ReactNode } from 'react';
import { ApiError, AUTH_EXPIRED_EVENT, isAccessTokenExpired, reissue, logout as apiLogout, setAccessToken, setUnauthorizedHandler } from '@/lib/api';
import { getWallet } from '@/features/point/api';
import { getCart } from '@/lib/order-api';
import { getMyPlants } from '@/lib/plant-api';
import { getMyCards } from '@/features/coupon/api';
import {
  getNotifications,
  getUnreadNotificationCount,
  markAllNotificationsRead,
  markNotificationRead,
  NotificationData,
  NotificationType,
} from '@/lib/notification-api';

const KEY = 'kwb_store_v1';

// 새로고침 시 만료된 토큰을 재발급받는 동안 서버가 느리거나 응답이 없으면 hydrated가
// 영원히 false로 남아 화면이 계속 로딩 상태처럼 보인다 — 이 시간을 넘기면 재발급 실패와
// 동일하게 처리해 hydration이 항상 끝나도록 한다.
const HYDRATION_REISSUE_TIMEOUT_MS = 5000;

export interface Wallet {
  free: number;
  paid: number;
}

export type { NotificationType, NotificationData };

// Trimmed to just what the UI actually needs (identity, nav display name, role
// gating) — deliberately not the full UserResponse, so phone/status/etc. never
// end up sitting in localStorage.
export interface CurrentUser {
  id: number;
  email: string;
  nickname: string;
  role: string;
  level: number;
}

export interface StoreState {
  authed: boolean;
  accessToken: string | null;
  user: CurrentUser | null;
  wallet: Wallet;
  growingCount: number;
  plantCount: number;
  harvestedCount: number;
  failedCount: number;
  readyCards: number;
  cartCount: number;
  // 벨 드롭다운 미리보기용 최근 알림 몇 건. 전체 목록·페이지네이션은 /notifications
  // 페이지가 이 store를 거치지 않고 직접 조회한다(다른 목록형 페이지와 동일한 패턴).
  notifications: NotificationData[];
  unreadNotificationCount: number;
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
  reset: () => void;
  balance: number;
  unreadCount: number;
  markNotifRead: (id: number) => Promise<void>;
  markAllNotifsRead: () => Promise<void>;
  login: (accessToken: string, user: CurrentUser) => void;
  logout: () => void;
  refreshWallet: () => Promise<void>;
  refreshCartCount: () => Promise<void>;
  refreshPlantStats: () => Promise<void>;
  refreshReadyCards: () => Promise<void>;
  refreshNotifications: () => Promise<void>;
  refreshUnreadCount: () => Promise<void>;
}

const EMPTY_WALLET: Wallet = { free: 0, paid: 0 };

const DEFAULTS: StoreState = {
  authed: false,
  accessToken: null,
  user: null,
  wallet: EMPTY_WALLET,
  growingCount: 3,
  plantCount: 5,
  harvestedCount: 1,
  failedCount: 1,
  readyCards: 0,
  cartCount: 0,
  notifications: [],
  unreadNotificationCount: 0,
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
  const cartCountRequestId = useRef(0);
  const notificationsRequestId = useRef(0);
  const plantStatsRequestId = useRef(0);
  const readyCardsRequestId = useRef(0);

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
      notifications: [],
      unreadNotificationCount: 0,
    }));
  }, []);

  useEffect(() => {
    (async () => {
      let restored: StoreState = DEFAULTS;
      try {
        const raw = localStorage.getItem(KEY);
        if (raw) {
          restored = {
            ...DEFAULTS,
            ...JSON.parse(raw),
            wallet: EMPTY_WALLET,
            readyCards: 0,
            notifications: [],
            unreadNotificationCount: 0,
          };
        }
      } catch (e) {}

      // A permitAll endpoint (e.g. board post detail) silently treats an expired/garbage
      // bearer token as "no token" instead of erroring, so waiting for a 401 to trigger
      // the usual silent-refresh flow never happens there. Refresh proactively here,
      // before hydrated flips true and any page's fetch effects fire with a stale token.
      //
      // This round trip has no timeout of its own, so a slow/unresponsive server would leave
      // hydrated stuck at false indefinitely — the navbar stays a gray bar and auth-gated pages
      // render blank, which looks like "the app won't load" rather than an auth problem. Race it
      // against a hard deadline and treat a timeout the same as a failed refresh (logged out) so
      // hydration always completes.
      if (restored.accessToken && isAccessTokenExpired(restored.accessToken)) {
        let timeoutId: ReturnType<typeof setTimeout> | undefined;
        try {
          const res = await Promise.race([
            reissue(),
            new Promise<never>((_, reject) => {
              timeoutId = setTimeout(() => reject(new Error('reissue timed out')), HYDRATION_REISSUE_TIMEOUT_MS);
            }),
          ]);
          restored = {
            ...restored,
            authed: true,
            accessToken: res.accessToken,
            user: {
              id: res.user.id,
              email: res.user.email,
              nickname: res.user.nickname,
              role: res.user.role,
              level: res.user.level,
            },
          };
        } catch {
          restored = { ...restored, authed: false, accessToken: null, user: null };
        } finally {
          clearTimeout(timeoutId);
        }
      }

      setState(restored);
      setHydrated(true);
    })();
  }, []);

  // Keep lib/api.ts's in-memory copy of the token in sync with whatever was
  // just restored from (or later written to) localStorage.
  useEffect(() => {
    setAccessToken(state.accessToken);
  }, [state.accessToken]);

  // Registered once so any plain `request()` call in lib/api.ts that gets a 401 can
  // trigger the same silent-refresh flow and retry with the new token.
  useEffect(() => {
    setUnauthorizedHandler(async () => {
      try {
        const res = await reissue();
        const user: CurrentUser = {
          id: res.user.id,
          email: res.user.email,
          nickname: res.user.nickname,
          role: res.user.role,
          level: res.user.level,
        };
        setState((s) => ({ ...s, authed: true, accessToken: res.accessToken, user }));
        return res.accessToken;
      } catch {
        // Refresh itself failed — the session is actually over, same as a hard
        // AUTH_EXPIRED_EVENT below, so route through the same cleanup path.
        clearAuthentication(true);
        return null;
      }
    });
    return () => setUnauthorizedHandler(null);
  }, [clearAuthentication]);

  useEffect(() => {
    if (!hydrated) return;
    try {
      localStorage.setItem(
        KEY,
        JSON.stringify({ ...state, wallet: EMPTY_WALLET, readyCards: 0, notifications: [], unreadNotificationCount: 0 }),
      );
    } catch (e) {}
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

  // 네비바 장바구니 배지는 실제 장바구니 항목 수를 반영한다. 담기/수량변경/삭제/주문 완료 등
  // 카트를 바꾸는 동작 뒤에 호출해서 동기화한다 — 클라이언트에서 임의로 +1/-1 하지 않는다.
  const refreshCartCount = useCallback(async () => {
    const requestId = ++cartCountRequestId.current;
    if (!state.authed || !state.accessToken) {
      setState((s) => ({ ...s, cartCount: 0 }));
      return;
    }
    try {
      const cart = await getCart(state.accessToken);
      if (requestId !== cartCountRequestId.current) return;
      setState((s) => ({ ...s, cartCount: cart.items.length }));
    } catch {
      // 배지 갱신 실패는 조용히 무시한다 — 다음 갱신 시점에 다시 시도된다.
    }
  }, [state.accessToken, state.authed]);

  useEffect(() => {
    if (!hydrated) return;
    void refreshCartCount();
  }, [hydrated, refreshCartCount]);

  // 대시보드의 "키우는 식물" 개수 등은 등록/삭제 화면이 낙관적으로 +1/-1 해주지만, 로그인 직후나
  // 새로고침 시점엔 그 낙관적 갱신을 거치지 않아 하드코딩 기본값이 그대로 남는다 — 여기서 실제
  // 서버 값으로 채운다. 전체 목록을 받을 필요 없이 totalElements만 필요하므로 size=1로 조회한다.
  const refreshPlantStats = useCallback(async () => {
    const requestId = ++plantStatsRequestId.current;
    if (!state.authed || !state.accessToken) {
      setState((s) => ({ ...s, growingCount: 0, plantCount: 0, harvestedCount: 0, failedCount: 0 }));
      return;
    }
    try {
      // 수확완료 개수는 "전체 - 재배중 - 실패"로 역산하지 않는다 — 네 요청이 서로 다른 시점의
      // 스냅샷이라 그 사이 상태가 바뀌면 역산값이 실제 수확완료 개수와 어긋날 수 있다. 상태별로
      // 직접 조회해 항상 정확한 값을 쓴다.
      const [growingPage, harvestedPage, failedPage, allPage] = await Promise.all([
        getMyPlants({ accessToken: state.accessToken, status: 'GROWING', size: 1 }),
        getMyPlants({ accessToken: state.accessToken, status: 'HARVESTED', size: 1 }),
        getMyPlants({ accessToken: state.accessToken, status: 'FAILED', size: 1 }),
        getMyPlants({ accessToken: state.accessToken, size: 1 }),
      ]);
      if (requestId !== plantStatsRequestId.current) return;
      setState((s) => ({
        ...s,
        growingCount: growingPage.totalElements,
        harvestedCount: harvestedPage.totalElements,
        failedCount: failedPage.totalElements,
        plantCount: allPage.totalElements,
      }));
    } catch {
      // 조용히 무시한다 — 다음 갱신 시점(재로그인/새로고침)에 다시 시도된다.
    }
  }, [state.accessToken, state.authed]);

  useEffect(() => {
    if (!hydrated) return;
    void refreshPlantStats();
  }, [hydrated, refreshPlantStats]);

  // 메인페이지 "교환 가능한 쿠폰이 N종 있어요" 배너용. 로그인 직후/새로고침 시점엔
  // 카드 구매·가챠 등을 거치지 않아 하드코딩 기본값이 그대로 남는다 — 여기서 실제
  // 서버 값으로 채운다. /cards 페이지의 "교환 가능 🎉" 배지와 동일한 기준(보유 수량
  // 충족 + 교환 상품 재고 있음)을 써서, 배너를 보고 들어갔다가 품절만 보는 걸 막는다.
  const refreshReadyCards = useCallback(async () => {
    const requestId = ++readyCardsRequestId.current;
    if (!state.authed || !state.accessToken) {
      setState((s) => ({ ...s, readyCards: 0 }));
      return;
    }
    try {
      const cards = await getMyCards(state.accessToken);
      if (requestId !== readyCardsRequestId.current) return;
      const readyCards = cards.filter(
        (card) =>
          card.ownedCount !== null &&
          card.ownedCount >= card.requiredCountForExchange &&
          card.exchangeProductStock > 0,
      ).length;
      setState((s) => ({ ...s, readyCards }));
    } catch {
      // 조용히 무시한다 — 다음 갱신 시점(재로그인/새로고침)에 다시 시도된다.
    }
  }, [state.accessToken, state.authed]);

  useEffect(() => {
    if (!hydrated) return;
    void refreshReadyCards();
  }, [hydrated, refreshReadyCards]);

  // 벨 배지·미리보기용. 읽음/전체읽음/삭제 뒤에도 이걸 다시 호출해 서버 상태로 재동기화한다 —
  // 장바구니 배지와 같은 이유로 클라이언트에서 카운트를 임의로 -1 하지 않는다.
  const refreshNotifications = useCallback(async () => {
    const requestId = ++notificationsRequestId.current;
    if (!state.authed || !state.accessToken) {
      setState((s) => ({ ...s, notifications: [], unreadNotificationCount: 0 }));
      return;
    }
    try {
      // accessToken을 명시적으로 넘기지 않는다 — lib/api.ts의 store-synced 토큰을 쓰게
      // 해야 access token이 만료됐을 때 조용히 재발급받아 재시도한다. 명시적으로 넘기면
      // 그 경로를 건너뛰고 바로 401 → 로그아웃으로 빠져, 30초마다 도는 이 폴링이 유효한
      // refresh token이 있어도 세션을 끊어버릴 수 있다.
      const [page, unread] = await Promise.all([
        getNotifications(undefined, undefined, 0, 5),
        getUnreadNotificationCount(undefined),
      ]);
      if (requestId !== notificationsRequestId.current) return;
      setState((s) => ({ ...s, notifications: page.content, unreadNotificationCount: unread.unreadCount }));
    } catch {
      // 배지 갱신 실패는 조용히 무시한다 — 다음 갱신 시점에 다시 시도된다.
    }
  }, [state.accessToken, state.authed]);

  // 벨 드롭다운 목록까지는 필요 없고 배지 숫자만 즉시 갱신하고 싶을 때 쓴다(예: 다른 페이지에서
  // 어떤 작업 완료로 새 알림이 생겼을 때) — refreshNotifications()는 목록 조회까지 같이 하므로
  // 배지만 필요한 호출부에서 쓰면 불필요한 API 호출이 생긴다.
  const refreshUnreadCount = useCallback(async () => {
    const requestId = ++notificationsRequestId.current;
    if (!state.authed || !state.accessToken) {
      setState((s) => ({ ...s, unreadNotificationCount: 0 }));
      return;
    }
    try {
      const unread = await getUnreadNotificationCount(undefined);
      if (requestId !== notificationsRequestId.current) return;
      setState((s) => ({ ...s, unreadNotificationCount: unread.unreadCount }));
    } catch {
      // 배지 갱신 실패는 조용히 무시한다 — 다음 갱신 시점에 다시 시도된다.
    }
  }, [state.accessToken, state.authed]);

  useEffect(() => {
    if (!hydrated) return;
    void refreshNotifications();
  }, [hydrated, refreshNotifications]);

  // 로그인 상태에서만 30초마다 배지를 다시 물어본다 — 실시간 전달(SSE/폴링)은
  // 아직 결정되지 않았으므로(ALERT-08) 가장 단순한 폴링으로 우선 채워둔다.
  useEffect(() => {
    if (!hydrated || !state.authed) return;
    const timer = setInterval(() => void refreshNotifications(), 30_000);
    return () => clearInterval(timer);
  }, [hydrated, state.authed, refreshNotifications]);

  const set = useCallback((patch: StorePatch) => {
    setState((s) => ({ ...s, ...(typeof patch === 'function' ? patch(s) : patch) }));
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
    // Re-pick fields explicitly: callers may pass a full UserResponse (structurally
    // compatible), but only id/email/nickname/role/level should ever reach localStorage.
    const trimmed: CurrentUser = {
      id: user.id,
      email: user.email,
      nickname: user.nickname,
      role: user.role,
      level: user.level,
    };
    walletRequestId.current += 1;
    setWalletLoading(false);
    setWalletLoaded(false);
    setWalletError(null);
    setAuthExpired(false);
    setState((s) => ({
      ...s,
      authed: true,
      accessToken,
      user: trimmed,
      wallet: EMPTY_WALLET,
      notifications: [],
      unreadNotificationCount: 0,
    }));
  }, []);

  const logout = useCallback(() => {
    apiLogout().catch(() => {}); // best-effort: revoke the refresh token server-side too
    clearAuthentication(false);
  }, [clearAuthentication]);

  const markNotifRead = useCallback(async (id: number) => {
    if (!state.accessToken) return;
    try {
      await markNotificationRead(id, state.accessToken);
    } finally {
      await refreshNotifications();
    }
  }, [state.accessToken, refreshNotifications]);

  const markAllNotifsRead = useCallback(async () => {
    if (!state.accessToken) return;
    try {
      await markAllNotificationsRead(state.accessToken);
    } finally {
      await refreshNotifications();
    }
  }, [state.accessToken, refreshNotifications]);

  const balance = state.wallet.free + state.wallet.paid;
  const unreadCount = state.unreadNotificationCount;

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
        reset,
        balance,
        unreadCount,
        markNotifRead,
        markAllNotifsRead,
        login,
        logout,
        refreshWallet,
        refreshCartCount,
        refreshPlantStats,
        refreshReadyCards,
        refreshNotifications,
        refreshUnreadCount,
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
