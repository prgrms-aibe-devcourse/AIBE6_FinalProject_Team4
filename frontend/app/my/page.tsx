'use client';
import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useUI } from '@/lib/ui';
import { useStore } from '@/lib/store';
import { ADDRESSES } from '@/lib/data';

const LINKS = [
  { icon: 'receipt_long', label: '주문 내역', href: '/my/orders' },
  { icon: 'redeem', label: '교환 내역', href: '/my/exchanges' },
  { icon: 'paid', label: '포인트 내역', href: '/my/points' },
  { icon: 'style', label: '내 카드', href: '/my/cards' },
  { icon: 'menu_book', label: '내 일지', href: '/journals' },
  { icon: 'mail', label: '1:1 문의', href: '/my/inquiries' },
];

export default function MyPage() {
  const { showToast } = useUI();
  const { logout } = useStore();
  const router = useRouter();
  const [addresses, setAddresses] = useState(ADDRESSES);

  const setDefault = (id: number) => {
    setAddresses(addresses.map((a) => ({ ...a, isDefault: a.id === id })));
    showToast('기본 배송지를 변경했어요.');
  };

  const doLogout = () => {
    logout();
    showToast('로그아웃했어요.');
    router.push('/');
  };

  return (
    <div className="container max-w-[960px]">
      <div className="mb-6 flex flex-wrap items-center gap-[18px] rounded-[20px] bg-white p-6 shadow-card">
        <div className="flex h-[72px] w-[72px] items-center justify-center rounded-full bg-gradient-to-br from-[#AED581] to-[#7CB342] text-3xl font-extrabold text-white">초</div>
        <div className="min-w-[180px] flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-xl font-extrabold">초록</span>
            <span className="rounded-full bg-gold-soft px-[11px] py-1 text-xs font-extrabold text-gold-text">Lv.3 새싹 정원사</span>
          </div>
          <div className="mt-1 text-sm text-sub">hello@example.com</div>
        </div>
        <button
          type="button"
          onClick={() => showToast('프로필 수정은 데모에서 준비 중이에요 🌿')}
          className="cursor-pointer rounded-[11px] bg-brand-soft px-[18px] py-[11px] font-bold text-brand-dark"
        >
          프로필 수정
        </button>
      </div>

      <h2 className="mb-3.5 text-lg font-extrabold">바로가기</h2>
      <div className="mb-7 grid gap-3.5 [grid-template-columns:repeat(auto-fill,minmax(150px,1fr))]">
        {LINKS.map((l) => (
          <Link key={l.label} href={l.href} className="rounded-2xl bg-white px-[18px] py-5 text-ink shadow-card hover:text-ink">
            <div><span className="material-symbols-outlined text-[28px] text-brand">{l.icon}</span></div>
            <div className="mt-2 font-extrabold">{l.label}</div>
          </Link>
        ))}
      </div>

      <h2 className="mb-3.5 text-lg font-extrabold">배송지 관리</h2>
      <div className="flex flex-col gap-3">
        {addresses.map((a) => (
          <div key={a.id} className="flex flex-wrap items-center gap-3 rounded-[14px] bg-white px-[18px] py-4 shadow-card">
            <div className="min-w-[180px] flex-1">
              <div className="flex items-center gap-2 font-bold">
                {a.name}
                {a.isDefault && <span className="rounded-full bg-brand-soft px-2 py-0.5 text-[11px] text-brand-dark">기본</span>}
              </div>
              <div className="mt-1 text-[13.5px] text-sub">{a.phone} · {a.addr}</div>
            </div>
            {!a.isDefault && (
              <button type="button" onClick={() => setDefault(a.id)} className="cursor-pointer rounded-[10px] border-[1.5px] border-[#cfe0b6] bg-white px-3.5 py-2 text-[13px] font-bold text-brand-dark">
                기본으로
              </button>
            )}
            <button type="button" className="cursor-pointer rounded-[10px] border-[1.5px] border-line bg-white px-3 py-2 text-[13px] font-bold text-sub">수정</button>
          </div>
        ))}
        <button type="button" className="cursor-pointer rounded-[14px] border-[1.5px] border-dashed border-[#cfe0b6] bg-white p-3.5 text-center font-bold text-brand-dark">
          + 새 배송지 추가
        </button>
      </div>

      <button
        type="button"
        onClick={doLogout}
        className="mt-7 w-full cursor-pointer rounded-[14px] border-[1.5px] border-line bg-white p-3.5 text-center font-bold text-danger"
      >
        로그아웃
      </button>
    </div>
  );
}
