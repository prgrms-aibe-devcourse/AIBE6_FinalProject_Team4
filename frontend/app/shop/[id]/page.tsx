'use client';
import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useStore } from '@/lib/store';
import { useUI } from '@/lib/ui';
import PointPrice from '@/components/PointPrice';
import { PRODUCTS } from '@/lib/data';

export default function ProductDetail({ params }: { params: { id: string } }) {
  const router = useRouter();
  const { set } = useStore();
  const { showToast } = useUI();
  const id = Number(params.id);
  const p = PRODUCTS.find((x) => x.id === id) || PRODUCTS[0];
  const [qty, setQty] = useState(1);
  const sold = p.stock <= 0;

  const addToCart = () => {
    if (qty > p.stock) return showToast(`지금은 최대 ${p.stock}개까지 담을 수 있어요.`, 'err');
    set((s) => ({ cartCount: s.cartCount + 1 }));
    showToast('장바구니에 담았어요 🛒');
  };

  return (
    <div className="container">
      <Link href="/shop" className="text-sm font-semibold text-sub">← 상점</Link>
      <div className="mt-4 grid items-start gap-7 [grid-template-columns:repeat(auto-fit,minmax(280px,1fr))]">
        <div
          className={`flex h-[320px] items-center justify-center overflow-hidden rounded-[22px] text-[130px] ${sold ? 'opacity-65 grayscale' : ''}`}
          style={{ background: p.grad }}
        >
          {p.emoji}
        </div>
        <div>
          <div className="mb-2.5 inline-block rounded-full bg-brand-soft px-[11px] py-1 text-xs font-extrabold text-brand-dark">
            {p.cat === 'KIT' ? '키트' : '모종'}
          </div>
          <h1 className="mb-2 text-[26px] font-extrabold">{p.name}</h1>
          <PointPrice value={p.price} size="lg" className="mb-3.5" />
          <p className="mb-[18px] text-[14.5px] leading-[1.7] text-[#6d7a68]">{p.description}</p>
          <div className={`mb-[18px] text-[13px] font-bold ${sold ? 'text-danger' : 'text-brand'}`}>
            {sold ? '품절 · 곧 다시 채워둘게요' : `재고 ${p.stock}개 남았어요`}
          </div>

          {p.cat === 'SEEDLING' && (
            <div className="mb-5 rounded-2xl bg-[#F6F9EF] px-[18px] py-4">
              <div className="mb-1.5 font-extrabold">이 아이는 이런 식물이에요 🌿</div>
              <p className="text-[13.5px] leading-[1.65] text-[#6d7a68]">{p.speciesGuide}</p>
            </div>
          )}

          {sold ? (
            <button type="button" disabled className="w-full cursor-not-allowed rounded-[13px] bg-line p-[15px] font-extrabold text-[#a9b3a0]">
              아쉽지만 지금은 준비된 수량이 모두 나갔어요
            </button>
          ) : (
            <>
              <div className="mb-[18px] flex items-center gap-3.5">
                <span className="font-bold text-[#6d7a68]">수량</span>
                <div className="flex items-center overflow-hidden rounded-[11px] border-[1.5px] border-line">
                  <button type="button" onClick={() => setQty(Math.max(1, qty - 1))} className="flex h-10 w-10 cursor-pointer items-center justify-center text-xl text-[#6d7a68]">−</button>
                  <div className="w-[46px] text-center text-base font-extrabold">{qty}</div>
                  <button type="button" onClick={() => setQty(Math.min(p.stock, qty + 1))} className="flex h-10 w-10 cursor-pointer items-center justify-center text-xl text-[#6d7a68]">+</button>
                </div>
              </div>
              <div className="flex flex-wrap gap-2.5">
                <button type="button" onClick={addToCart} className="min-w-[130px] flex-1 cursor-pointer rounded-[13px] bg-brand-soft p-[15px] font-extrabold text-brand-dark">
                  장바구니 담기
                </button>
                <button type="button" onClick={() => { addToCart(); router.push('/checkout'); }} className="min-w-[130px] flex-1 cursor-pointer rounded-[13px] bg-brand p-[15px] font-extrabold text-white">
                  바로 구매
                </button>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
