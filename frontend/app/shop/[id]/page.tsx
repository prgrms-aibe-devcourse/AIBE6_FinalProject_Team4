'use client';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { ApiError } from '@/lib/api';
import { withTopicParticle } from '@/lib/korean';
import { getProduct, ProductDetail as ProductDetailData } from '@/lib/product-api';
import { useStore } from '@/lib/store';
import { useUI } from '@/lib/ui';
import PointPrice from '@/components/PointPrice';

export default function ProductDetail({ params }: { params: { id: string } }) {
  const router = useRouter();
  const { state, hydrated, set } = useStore();
  const { showToast } = useUI();
  const productId = Number(params.id);
  const [product, setProduct] = useState<ProductDetailData | null>(null);
  const [qty, setQty] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!Number.isInteger(productId) || productId < 1) {
      setError('잘못된 상품 주소예요.');
      setLoading(false);
      return;
    }

    const controller = new AbortController();
    setLoading(true);
    setError('');

    getProduct(productId, undefined, controller.signal)
      .then(setProduct)
      .catch((requestError) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return;
        setProduct(null);
        setError(
          requestError instanceof ApiError
            ? requestError.message
            : '상품을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.',
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [productId]);

  if (loading) {
    return (
      <div className="container">
        <div className="rounded-[22px] bg-white py-14 text-center text-sub">
          상품을 불러오고 있어요 🌱
        </div>
      </div>
    );
  }

  if (error || !product) {
    return (
      <div className="container">
        <div className="rounded-[22px] bg-white px-5 py-14 text-center text-sub">
          <p>{error || '상품을 찾을 수 없어요.'}</p>
          <Link
            href="/shop"
            className="mt-4 inline-block rounded-xl bg-brand px-5 py-2.5 font-bold text-white hover:text-white"
          >
            상점으로 돌아가기
          </Link>
        </div>
      </div>
    );
  }

  const addToCart = () => {
    if (!hydrated || !state.accessToken) {
      showToast('장바구니와 구매 기능은 로그인 후 이용할 수 있어요.', 'err');
      return false;
    }
    if (qty > product.stock) {
      showToast(`지금은 최대 ${product.stock}개까지 담을 수 있어요.`, 'err');
      return false;
    }
    set((storeState) => ({ cartCount: storeState.cartCount + 1 }));
    showToast('장바구니에 담았어요 🛒');
    return true;
  };

  return (
    <div className="container">
      <Link href="/shop" className="text-sm font-semibold text-sub">
        ← 상점
      </Link>
      <div className="mt-4 grid items-start gap-7 [grid-template-columns:repeat(auto-fit,minmax(280px,1fr))]">
        <div
          className={`flex h-[320px] items-center justify-center overflow-hidden rounded-[22px] bg-brand-soft bg-cover bg-center text-[130px] ${
            product.soldOut ? 'opacity-65 grayscale' : ''
          }`}
          style={
            product.imageUrl
              ? { backgroundImage: `url("${product.imageUrl}")` }
              : undefined
          }
        >
          {!product.imageUrl && '🌱'}
        </div>
        <div>
          <div className="mb-2.5 inline-block rounded-full bg-brand-soft px-[11px] py-1 text-xs font-extrabold text-brand-dark">
            {product.category === 'KIT' ? '키트' : '모종'}
          </div>
          <h1 className="mb-2 text-[26px] font-extrabold">{product.name}</h1>
          <PointPrice value={product.pointPrice} size="lg" className="mb-3.5" />
          <p className="mb-[18px] text-[14.5px] leading-[1.7] text-[#6d7a68]">
            {product.description || '상품 설명을 준비하고 있어요.'}
          </p>
          <div className={`mb-[18px] text-[13px] font-bold ${product.soldOut ? 'text-danger' : 'text-brand'}`}>
            {product.soldOut
              ? '품절 · 곧 다시 채워둘게요'
              : `재고 ${product.stock}개 남았어요`}
          </div>

          {product.category === 'SEEDLING' && product.plantGuide && (
            <div className="mb-5 rounded-2xl bg-[#F6F9EF] px-[18px] py-4">
              <div className="mb-1.5 font-extrabold">
                {withTopicParticle(product.plantGuide.name)} 이렇게 키워요 🌿
              </div>
              <p className="text-[13.5px] leading-[1.65] text-[#6d7a68]">
                {product.plantGuide.careGuide || '식물 가이드를 준비하고 있어요.'}
              </p>
            </div>
          )}

          {product.soldOut ? (
            <button
              type="button"
              disabled
              className="w-full cursor-not-allowed rounded-[13px] bg-line p-[15px] font-extrabold text-[#a9b3a0]"
            >
              아쉽지만 지금은 준비된 수량이 모두 나갔어요
            </button>
          ) : (
            <>
              <div className="mb-[18px] flex items-center gap-3.5">
                <span className="font-bold text-[#6d7a68]">수량</span>
                <div className="flex items-center overflow-hidden rounded-[11px] border-[1.5px] border-line">
                  <button
                    type="button"
                    onClick={() => setQty(Math.max(1, qty - 1))}
                    className="flex h-10 w-10 cursor-pointer items-center justify-center text-xl text-[#6d7a68]"
                  >
                    −
                  </button>
                  <div className="w-[46px] text-center text-base font-extrabold">{qty}</div>
                  <button
                    type="button"
                    onClick={() => setQty(Math.min(product.stock, qty + 1))}
                    className="flex h-10 w-10 cursor-pointer items-center justify-center text-xl text-[#6d7a68]"
                  >
                    +
                  </button>
                </div>
              </div>
              <div className="flex flex-wrap gap-2.5">
                <button
                  type="button"
                  onClick={addToCart}
                  className="min-w-[130px] flex-1 cursor-pointer rounded-[13px] bg-brand-soft p-[15px] font-extrabold text-brand-dark"
                >
                  장바구니 담기
                </button>
                <button
                  type="button"
                  onClick={() => {
                    if (addToCart()) router.push('/checkout');
                  }}
                  className="min-w-[130px] flex-1 cursor-pointer rounded-[13px] bg-brand p-[15px] font-extrabold text-white"
                >
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
