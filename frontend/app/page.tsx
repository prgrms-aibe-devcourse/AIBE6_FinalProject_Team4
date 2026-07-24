'use client';
import Link from 'next/link';
import { useStore, fmt } from '@/lib/store';
import { PLANTS } from '@/lib/data';

const CONFETTI = [
  { left: '8%', dur: '1.4s', delay: '0s', emoji: '🌿' }, { left: '26%', dur: '1.7s', delay: '.2s', emoji: '✨' },
  { left: '46%', dur: '1.3s', delay: '.1s', emoji: '🍉' }, { left: '64%', dur: '1.8s', delay: '.35s', emoji: '🌱' },
  { left: '82%', dur: '1.5s', delay: '.15s', emoji: '✨' }, { left: '92%', dur: '1.6s', delay: '.28s', emoji: '💚' },
];

const FEATURES = [
  { emoji: '🌱', title: '매일 기록하기', desc: '식물의 성장을 사진과 함께 일지로 남겨요' },
  { emoji: '☀️', title: '포인트 쌓기', desc: '기록할 때마다 포인트가 차곡차곡 쌓여요' },
  { emoji: '🎴', title: '카드 모으기', desc: '쌓인 포인트로 특별한 카드를 모아보세요' },
  { emoji: '🍉', title: '진짜 열매 받기', desc: '모은 카드를 진짜 과일·채소로 교환해요' },
];

export default function Home() {
  const { state, balance } = useStore();
  const plants = PLANTS.filter((p) => !p.archived).slice(0, 4);

  if (!state.authed) {
    return (
      <div className="container animate-upIn">
        <div className="grid items-center gap-11 py-12 md:py-16 [grid-template-columns:repeat(auto-fit,minmax(300px,1fr))]">
          <div className="text-center md:text-left">
            <h1 className="mb-4 text-[34px] font-extrabold leading-[1.3] md:text-[42px] md:leading-[1.25]">
              식물을 키우고,<br />기록하고,<br /><span className="text-brand">진짜 열매</span>를 받아보세요
            </h1>
            <p className="mb-7 text-base leading-[1.6] text-sub md:text-[17px]">
              매일의 성장을 기록하면 포인트가 쌓이고,<br />카드를 모으면 진짜 과일·채소로 바꿔드려요.
            </p>
            <div className="flex flex-wrap justify-center gap-3 md:justify-start">
              <Link href="/auth?view=signup" className="rounded-[14px] bg-brand px-7 py-[15px] text-base font-bold text-white shadow-[0_6px_18px_rgba(124,179,66,.35)] transition-colors duration-150 hover:bg-brand-dark hover:text-white">
                회원가입하고 시작하기
              </Link>
              <Link href="/auth?view=login" className="rounded-[14px] border-[1.5px] border-[#cfe0b6] bg-white px-7 py-[15px] text-base font-bold text-brand-dark transition-colors duration-150 hover:bg-brand-soft">
                로그인
              </Link>
            </div>
          </div>
          <div className="flex justify-center">
            <div className="relative flex h-[300px] w-[280px] items-center justify-center rounded-[28px] bg-gradient-to-b from-[#F3F7E9] to-[#E4EFCF] shadow-[0_20px_50px_rgba(124,179,66,.18)]">
              <div className="animate-floaty text-[130px]">🪴</div>
              <div className="absolute right-[30px] top-[26px] text-[40px]">☀️</div>
              <div className="absolute bottom-7 left-[26px] text-[30px]">🌱</div>
            </div>
          </div>
        </div>

        <div className="grid gap-4 border-t border-line pb-8 pt-10 [grid-template-columns:repeat(auto-fit,minmax(200px,1fr))]">
          {FEATURES.map((f) => (
            <div key={f.title} className="rounded-[18px] bg-white p-5 text-center shadow-card">
              <div className="text-[34px]">{f.emoji}</div>
              <div className="mt-2 font-extrabold">{f.title}</div>
              <div className="mt-1 text-[13px] text-sub">{f.desc}</div>
            </div>
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="container animate-upIn">
      <h1 className="mb-1 text-[27px] font-extrabold">안녕하세요, 초록님! 오늘도 푸릇한 하루예요 ☀️</h1>
      <p className="mb-6 text-sub">작은 기록이 모여 큰 수확이 돼요.</p>

      {state.readyCards > 0 && (
        <div className="relative mb-6 flex flex-wrap items-center gap-4 overflow-hidden rounded-[20px] bg-gradient-to-br from-[#FFE9A6] to-[#FFD54F] px-6 py-[22px] shadow-[0_8px_24px_rgba(255,213,79,.3)]">
          <div className="text-[46px]">🍉</div>
          <div className="min-w-[200px] flex-1">
            <div className="text-lg font-extrabold text-[#6b5500]">교환 가능한 카드가 {state.readyCards}종 있어요!</div>
            <div className="text-[14.5px] text-gold-text">진짜 열매로 바꿔볼까요?</div>
          </div>
          <Link href="/my/cards" className="rounded-xl bg-ink px-5 py-3 font-bold text-white transition-colors duration-150 hover:bg-[#2a332a] hover:text-white">교환하러 가기 🎉</Link>
          {CONFETTI.map((c, i) => (
            <span
              key={i}
              className="absolute -top-2.5 animate-confettiFall text-base"
              style={{ left: c.left, animationDuration: c.dur, animationDelay: c.delay, animationIterationCount: 'infinite' }}
            >
              {c.emoji}
            </span>
          ))}
        </div>
      )}

      <div className="mb-[30px] grid gap-4 [grid-template-columns:repeat(auto-fit,minmax(240px,1fr))]">
        <div className="rounded-[18px] bg-white p-[22px] shadow-card">
          <div className="text-[13px] font-bold text-sub">내 포인트</div>
          <div className="mb-0.5 mt-2 text-[31px] font-extrabold">{fmt(balance)}<span className="text-base text-faint">P</span></div>
          <Link href="/my/points/charge" className="mt-2.5 inline-block rounded-[10px] bg-gold-soft px-4 py-[9px] font-bold text-gold-text transition-colors duration-150 hover:bg-gold hover:text-gold-text">충전하기</Link>
        </div>

        <div className="rounded-[18px] bg-white p-[22px] shadow-card">
          <div className="text-[13px] font-bold text-sub">오늘의 일지</div>
          {state.wroteToday ? (
            <>
              <div className="my-3.5 text-[19px] font-extrabold text-brand">오늘 기록 완료 ✓</div>
              <Link href="/journals" className="inline-block rounded-[10px] bg-brand-soft px-4 py-[9px] font-bold text-brand-dark transition-colors duration-150 hover:bg-brand hover:text-white">일지 보기</Link>
            </>
          ) : (
            <>
              <div className="my-3 text-[15px] font-bold text-[#6d7a68]">오늘의 기록을 남기면<br />포인트를 드려요!</div>
              <Link href="/journals/new" className="inline-block rounded-[10px] bg-brand px-4 py-[9px] font-bold text-white transition-colors duration-150 hover:bg-brand-dark hover:text-white">오늘의 일지 쓰기</Link>
            </>
          )}
        </div>

        <div className="rounded-[18px] bg-white p-[22px] shadow-card">
          <div className="text-[13px] font-bold text-sub">키우는 식물</div>
          <div className="mb-0.5 mt-2 text-[31px] font-extrabold">{state.growingCount}<span className="text-base text-faint">개</span></div>
          <Link href="/plants" className="mt-2.5 inline-block rounded-[10px] bg-brand-soft px-4 py-[9px] font-bold text-brand-dark transition-colors duration-150 hover:bg-brand hover:text-white">내 식물 보기</Link>
        </div>
      </div>

      <div className="mb-3.5 flex items-center justify-between">
        <h2 className="text-xl font-extrabold">내 식물 미리보기</h2>
        <Link href="/plants" className="text-sm font-bold text-brand-dark">전체보기 →</Link>
      </div>
      <div className="grid gap-4 [grid-template-columns:repeat(auto-fill,minmax(200px,1fr))]">
        {plants.map((p) => (
          <Link key={p.id} href={`/plants/${p.id}`} className="block overflow-hidden rounded-[18px] bg-white text-ink shadow-card hover:text-ink">
            <div className="flex h-[120px] items-center justify-center text-[60px]" style={{ background: p.grad }}>{p.emoji}</div>
            <div className="p-3.5">
              <div className="font-extrabold">{p.nickname}</div>
              <div className="mt-0.5 text-[13px] text-sub">{p.species} · D+{p.dplus}</div>
            </div>
          </Link>
        ))}
      </div>
    </div>
  );
}
