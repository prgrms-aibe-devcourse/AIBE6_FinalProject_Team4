'use client';
import Link from 'next/link';
import { usePathname } from 'next/navigation';

const COLS = [
  { title: '서비스', links: [['홈', '/'], ['내 식물', '/plants'], ['성장 일지', '/journals'], ['상점', '/shop'], ['카드', '/cards']] },
  { title: '내 정보', links: [['마이페이지', '/my'], ['주문 내역', '/my/orders'], ['포인트', '/my/points'], ['내 카드', '/my/cards']] },
  { title: '고객지원', links: [['1:1 문의', '/my/inquiries'], ['알림 설정', '/my/settings/notifications']] },
];

export default function Footer() {
  const pathname = usePathname() || '/';
  if (pathname.startsWith('/auth')) return null;

  return (
    <footer className="mt-10 hidden border-t border-line bg-[#F6F8EF] font-sans md:block">
      <div className="mx-auto grid max-w-[1160px] grid-cols-[1.4fr_1fr_1fr_1fr] items-start gap-8 px-5 pb-7 pt-10">
        <div>
          <div className="text-[19px] font-extrabold text-brand-dark">키워볼래 🌱</div>
          <p className="mt-3 max-w-[260px] text-[13.5px] leading-[1.7] text-sub">
            식물을 키우고, 기록하고, 진짜 열매를 받아보세요. 작은 기록이 모여 큰 수확이 돼요.
          </p>
          <div className="mt-3.5 inline-block rounded-full bg-gold-soft px-3 py-1.5 text-xs font-bold text-gold-text">
            테스트 모드 · 실결제 없음
          </div>
        </div>
        {COLS.map((c) => (
          <div key={c.title}>
            <div className="mb-3 text-sm font-extrabold">{c.title}</div>
            <div className="flex flex-col gap-[9px]">
              {c.links.map(([label, href]) => (
                <Link key={label} href={href} className="text-[13.5px] text-sub">{label}</Link>
              ))}
            </div>
          </div>
        ))}
      </div>
      <div className="border-t border-[#e6ead9] px-5 py-4 text-center text-xs text-[#a9b3a0]">
        © 2026 키워볼래. 실결제가 발생하지 않는 테스트 모드로 운영됩니다.
      </div>
    </footer>
  );
}
