'use client';
import { ReactNode, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useStore } from '@/lib/store';
import { useUI } from '@/lib/ui';

interface RequireAuthProps {
  children: ReactNode;
  /**
   * UX-only gate — the value being checked (state.user.role) lives in localStorage and can be
   * edited by the user, so this must never be treated as real authorization. The backend must
   * independently verify the role from the signed JWT on every protected endpoint.
   */
  role?: string;
}

// Gates a route (and everything nested under it) behind login, and optionally a role.
export default function RequireAuth({ children, role }: RequireAuthProps) {
  const router = useRouter();
  const { state, hydrated, authExpired } = useStore();
  const { showToast } = useUI();
  const forbidden = !!role && state.user?.role !== role;

  useEffect(() => {
    if (!hydrated) return;
    if (!state.authed) {
      if (authExpired) {
        showToast('로그인 시간이 만료되었어요. 다시 로그인해 주세요.', 'err');
      }
      router.replace(`/auth?view=login${authExpired ? '&reason=expired' : ''}`);
    } else if (forbidden) {
      showToast('접근 권한이 없어요.', 'err');
      router.replace('/');
    }
  }, [authExpired, hydrated, state.authed, forbidden, router, showToast]);

  if (!hydrated || !state.authed || forbidden) return null;
  return <>{children}</>;
}
