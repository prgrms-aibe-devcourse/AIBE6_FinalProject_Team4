'use client';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';
import { useStore, fmt, NotificationType } from '@/lib/store';
import { useUI } from '@/lib/ui';
import Skeleton from './Skeleton';

const NOTIF_ICON: Record<NotificationType, string> = {
  DELIVERY: '📦',
  POINT: '☀️',
  JOURNAL_REMINDER: '🌱',
  INQUIRY: '💬',
  NOTICE: '📢',
};

const NAV = [
  { key: 'home', label: '홈', href: '/' },
  { key: 'plants', label: '내 식물', href: '/plants' },
  { key: 'journal', label: '일지', href: '/journals' },
  { key: 'shop', label: '상점', href: '/shop' },
  { key: 'cards', label: '카드', href: '/cards' },
  { key: 'exchange', label: '교환', href: '/my/exchanges' },
];

const BOTTOM = [
  { key: 'home', label: '홈', icon: 'home', href: '/' },
  { key: 'plants', label: '식물', icon: 'potted_plant', href: '/plants' },
  { key: 'journal', label: '일지', icon: 'menu_book', href: '/journals' },
  { key: 'shop', label: '상점', icon: 'storefront', href: '/shop' },
  { key: 'account', label: 'MY', icon: 'person', href: '/my' },
];

function activeKey(pathname: string) {
  if (pathname === '/') return 'home';
  if (pathname.startsWith('/plants')) return 'plants';
  if (pathname.startsWith('/journals')) return 'journal';
  if (pathname.startsWith('/cards')) return 'cards';
  if (pathname.startsWith('/shop')) return 'shop';
  if (pathname.startsWith('/my/exchanges') || pathname.startsWith('/exchange')) return 'exchange';
  if (pathname.startsWith('/my')) return 'account';
  return '';
}

export default function Navbar() {
  const pathname = usePathname() || '/';
  const router = useRouter();
  const { balance, state, hydrated, reset, logout, unreadCount, markNotifRead, markAllNotifsRead } = useStore();
  const { showToast } = useUI();
  const active = activeKey(pathname);
  const cartCount = state.cartCount;
  const isAdmin = state.user?.role === 'ADMIN';

  const [bellOpen, setBellOpen] = useState(false);
  const bellRef = useRef<HTMLDivElement>(null);
  const [profileOpen, setProfileOpen] = useState(false);
  const profileRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!bellOpen && !profileOpen) return;
    const onClick = (e: MouseEvent) => {
      if (bellRef.current && !bellRef.current.contains(e.target as Node)) setBellOpen(false);
      if (profileRef.current && !profileRef.current.contains(e.target as Node)) setProfileOpen(false);
    };
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, [bellOpen, profileOpen]);

  const doReset = () => { reset(); router.refresh(); };

  const openNotif = (n: (typeof state.notifications)[number]) => {
    markNotifRead(n.id);
    if (n.broken) showToast('연결된 내용을 찾을 수 없어요.', 'err');
    setBellOpen(false);
  };

  const doLogout = () => {
    setProfileOpen(false);
    logout();
    showToast('로그아웃했어요.');
    router.push('/');
  };

  if (pathname.startsWith('/auth')) return null;

  return (
    <div className="font-sans">
      <div className="sticky top-0 z-40 border-b border-line bg-paper/90 backdrop-blur-md">
        <div className="mx-auto flex h-[62px] max-w-[1160px] items-center gap-2 px-4 md:gap-4 md:px-5">
          <Link href="/" className="whitespace-nowrap text-[17px] font-extrabold text-brand-dark md:text-[19px]">키워볼래 🌱</Link>
          <div className="ml-3 hidden gap-0.5 overflow-auto md:flex">
            {NAV.map((n) => (
              <Link
                key={n.key}
                href={n.href}
                className={`whitespace-nowrap rounded-[10px] px-3 py-2 text-[15px] font-bold transition-colors duration-150 ${
                  active === n.key
                    ? 'bg-brand text-white hover:bg-brand-dark hover:text-white'
                    : 'text-[#5b6a54] hover:bg-brand-soft hover:text-brand-dark'
                }`}
              >
                {n.label}
              </Link>
            ))}
          </div>
          <div className="flex-1" />
          {!hydrated ? (
            <>
              <Skeleton className="h-10 w-10" />
              <Skeleton className="h-10 w-10" />
              <Skeleton className="h-[34px] w-24 rounded-full" />
              <Skeleton className="h-10 w-10" />
              <Skeleton className="h-[34px] w-[34px] rounded-full" />
            </>
          ) : state.authed ? (
            <>
              <button type="button" onClick={doReset} title="데모 데이터 초기화" className="hidden cursor-pointer rounded-[9px] px-2 py-1 text-faint transition-colors duration-150 hover:bg-brand-soft hover:text-brand-dark md:block">
                <span className="material-symbols-outlined text-[19px]">refresh</span>
              </button>
              {isAdmin && (
                <Link href="/admin" className="hidden whitespace-nowrap rounded-[10px] bg-ink px-3.5 py-2 text-[15px] font-bold text-white transition-colors duration-150 hover:bg-[#2a332a] hover:text-white md:block">
                  관리자 콘솔
                </Link>
              )}
              <Link
                href="/cart"
                title="장바구니"
                className="relative grid h-10 w-10 place-items-center rounded-xl border border-line bg-white text-[#5b6a54] transition-colors duration-150 hover:border-brand hover:bg-brand-soft hover:text-brand-dark"
              >
                <span className="material-symbols-outlined text-[21px]">shopping_cart</span>
                {cartCount > 0 && (
                  <span className="absolute right-[3px] top-[3px] grid h-4 min-w-4 place-items-center rounded-full bg-brand px-[3px] text-[10px] font-bold text-white">
                    {cartCount}
                  </span>
                )}
              </Link>
              <Link href="/my/points" className="flex items-center gap-1.5 whitespace-nowrap rounded-full bg-gold-soft px-[13px] py-[7px] font-extrabold text-gold-text transition-colors duration-150 hover:bg-gold hover:text-gold-text">
                <span className="inline-flex h-[18px] w-[18px] items-center justify-center rounded-full bg-gold text-[11px] text-gold-text">P</span>
                {fmt(balance)}
              </Link>
              <div className="relative" ref={bellRef}>
                <button
                  type="button"
                  onClick={() => setBellOpen((v) => !v)}
                  className="relative grid h-10 w-10 place-items-center rounded-xl border border-line bg-white text-[#5b6a54] transition-colors duration-150 hover:border-brand hover:bg-brand-soft hover:text-brand-dark"
                >
                  <span className="material-symbols-outlined text-[21px]">notifications</span>
                  {unreadCount > 0 && (
                    <span className="absolute right-[3px] top-[3px] flex h-4 min-w-4 items-center justify-center rounded-full bg-[#e5533b] px-[3px] text-[10px] font-extrabold text-white">
                      {unreadCount}
                    </span>
                  )}
                </button>
                {bellOpen && (
                  <div className="absolute right-0 top-[48px] w-80 overflow-hidden rounded-2xl border border-line bg-white shadow-[0_14px_40px_-12px_rgba(85,139,47,.35)]">
                    <div className="flex items-center justify-between border-b border-[#F2ECDD] px-4 py-3.5">
                      <b className="text-[15px] font-bold">알림</b>
                      <button type="button" onClick={markAllNotifsRead} className="cursor-pointer text-[13px] font-bold text-brand hover:text-brand-dark">
                        모두 읽음
                      </button>
                    </div>
                    {state.notifications.slice(0, 4).map((n) => (
                      <button
                        key={n.id}
                        type="button"
                        onClick={() => openNotif(n)}
                        className={`flex w-full cursor-pointer items-start gap-[11px] border-b border-[#F7F2E7] px-4 py-3 text-left transition-colors duration-150 hover:bg-brand-soft ${n.unread ? 'bg-[#FFFBEB]' : 'bg-white'}`}
                      >
                        <span className="text-[18px]">{NOTIF_ICON[n.type]}</span>
                        <div className="min-w-0">
                          <div className="text-[14px] font-bold text-[#3E4A3D]">{n.title}</div>
                          <div className="overflow-hidden text-ellipsis whitespace-nowrap text-[12.5px] text-faint">{n.content}</div>
                        </div>
                      </button>
                    ))}
                    <Link
                      href="/notifications"
                      onClick={() => setBellOpen(false)}
                      className="block px-3 py-3 text-center text-[14px] font-bold text-brand transition-colors duration-150 hover:bg-brand-soft hover:text-brand-dark"
                    >
                      전체 보기
                    </Link>
                  </div>
                )}
              </div>
              <div className="relative" ref={profileRef}>
                <button
                  type="button"
                  onClick={() => setProfileOpen((v) => !v)}
                  className="flex h-[34px] w-[34px] cursor-pointer items-center justify-center rounded-full bg-gradient-to-br from-[#AED581] to-[#7CB342] font-extrabold text-white ring-0 ring-brand-dark/40 transition-shadow duration-150 hover:ring-4"
                >
                  초
                </button>
                {profileOpen && (
                  <div className="absolute right-0 top-[44px] w-56 overflow-hidden rounded-2xl border border-line bg-white shadow-[0_14px_40px_-12px_rgba(85,139,47,.35)]">
                    <div className="border-b border-[#F2ECDD] px-4 py-3.5">
                      <div className="text-[15px] font-bold">초록님</div>
                      <div className="mt-0.5 text-[12.5px] text-faint">Lv.3 새싹 정원사</div>
                    </div>
                    <Link href="/my" onClick={() => setProfileOpen(false)} className="block px-4 py-2.5 text-[14px] font-semibold text-ink transition-colors duration-150 hover:bg-brand-soft hover:text-ink">
                      마이페이지
                    </Link>
                    <Link href="/my/orders" onClick={() => setProfileOpen(false)} className="block px-4 py-2.5 text-[14px] font-semibold text-ink transition-colors duration-150 hover:bg-brand-soft hover:text-ink">
                      주문 내역
                    </Link>
                    <Link href="/my/points" onClick={() => setProfileOpen(false)} className="block px-4 py-2.5 text-[14px] font-semibold text-ink transition-colors duration-150 hover:bg-brand-soft hover:text-ink">
                      포인트 내역
                    </Link>
                    <Link href="/my/inquiries" onClick={() => setProfileOpen(false)} className="block px-4 py-2.5 text-[14px] font-semibold text-ink transition-colors duration-150 hover:bg-brand-soft hover:text-ink">
                      1:1 문의
                    </Link>
                    <button
                      type="button"
                      onClick={doLogout}
                      className="block w-full cursor-pointer border-t border-[#F2ECDD] px-4 py-2.5 text-left text-[14px] font-semibold text-danger transition-colors duration-150 hover:bg-danger-soft"
                    >
                      로그아웃
                    </button>
                  </div>
                )}
              </div>
            </>
          ) : (
            <>
              <Link href="/auth?view=login" className="whitespace-nowrap rounded-[10px] px-3.5 py-2 text-[15px] font-bold text-[#5b6a54] transition-colors duration-150 hover:bg-brand-soft hover:text-brand-dark">로그인</Link>
              <Link href="/auth?view=signup" className="whitespace-nowrap rounded-[10px] bg-brand px-3.5 py-2 text-[15px] font-bold text-white transition-colors duration-150 hover:bg-brand-dark hover:text-white">회원가입</Link>
            </>
          )}
        </div>
      </div>

      <div className="fixed bottom-0 left-0 right-0 z-[45] h-[66px] border-t border-line bg-paper/95 backdrop-blur-md md:hidden">
        <div className="flex h-[66px] w-full">
          {BOTTOM.map((b) => {
            const href = b.key === 'account' && !state.authed ? '/auth' : b.href;
            return (
              <Link
                key={b.key}
                href={href}
                className={`flex flex-1 flex-col items-center justify-center gap-[3px] ${
                  active === b.key ? 'text-brand hover:text-brand' : 'text-[#9aa691] hover:text-[#9aa691]'
                }`}
              >
                <span className="material-symbols-outlined text-2xl">{b.icon}</span>
                <span className="text-[11px] font-bold">{b.label}</span>
              </Link>
            );
          })}
        </div>
      </div>
    </div>
  );
}
