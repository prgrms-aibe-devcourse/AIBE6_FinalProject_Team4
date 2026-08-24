'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import PointPrice from '@/components/PointPrice';
import { useProductDetail } from '@/features/shop/use-product-detail';
import { useStore } from '@/lib/store';
import PlantCareGuidePanel from '@/features/plant/PlantCareGuidePanel';

export default function ProductDetail({
  params,
  searchParams,
}: {
  params: { id: string };
  searchParams?: { returnTo?: string | string[] };
}) {
  const router = useRouter();
  const { state } = useStore();
  const {
    product,
    qty,
    setQty,
    loading,
    error,
    purchasing,
    adding,
    alreadyInCart,
    checkingCart,
    isGachaPack,
    maxQuantity,
    gachaTotalPoint,
    returnTo,
    addToCart,
    purchaseGachaPack,
  } = useProductDetail({ id: params.id, requestedReturnTo: searchParams?.returnTo });

  if (loading) {
    return (
      <div className="container">
        <div className="rounded-[22px] bg-white py-14 text-center text-sub">
          상품을 불러오고 있어요
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
            href={returnTo}
            className="mt-4 inline-block rounded-xl bg-brand px-5 py-2.5 font-bold text-white hover:text-white"
          >
            상점으로 돌아가기
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="container">
      <Link href={returnTo} className="text-sm font-semibold text-sub">
        ← 상점
      </Link>
      <div className="mt-4 grid items-start gap-7 [grid-template-columns:repeat(auto-fit,minmax(280px,1fr))]">
        <div
          className={`flex h-[320px] items-center justify-center overflow-hidden rounded-[22px] bg-brand-soft bg-center text-[130px] ${
            isGachaPack ? 'bg-contain bg-no-repeat' : 'bg-cover'
          } ${
            product.soldOut ? 'opacity-65 grayscale' : ''
          }`}
          style={
            product.imageUrl
              ? { backgroundImage: `url("${product.imageUrl}")` }
              : undefined
          }
        >
          {!product.imageUrl && <span className="material-symbols-outlined">potted_plant</span>}
        </div>
        <div>
          <div className="mb-2.5 inline-block rounded-full bg-brand-soft px-[11px] py-1 text-xs font-extrabold text-brand-dark">
            {product.category === 'KIT'
              ? '키트'
              : product.category === 'SEEDLING'
                ? '모종'
                : '가챠 팩'}
          </div>
          <h1 className="mb-2 text-[26px] font-extrabold">{product.name}</h1>
          <PointPrice value={product.pointPrice} size="lg" className="mb-3.5" />
          <p className="mb-[18px] text-[14.5px] leading-[1.7] text-[#6d7a68]">
            {product.description || '상품 설명을 준비하고 있어요.'}
          </p>
          {isGachaPack && (
            <p className="mb-[18px] rounded-[14px] border border-[#ddd4f3] bg-[#f7f4ff] px-4 py-3 text-[13px] font-semibold leading-6 text-[#5f527d]">
              보너스 포인트가 먼저 차감됩니다. 잔액 부족 시 충전포인트로 결제됩니다.
            </p>
          )}
          <div className={`mb-[18px] text-[13px] font-bold ${product.soldOut ? 'text-danger' : 'text-brand'}`}>
            {product.soldOut
              ? '품절 · 곧 다시 채워둘게요'
              : isGachaPack
                ? '팩은 한 번에 1개씩 구매할 수 있어요'
                : `재고 ${product.stock}개 남았어요`}
          </div>

          {isGachaPack && (
            <div className="mb-5 rounded-2xl border border-[#e5d899] bg-[#fff9df] px-4 py-3 text-sm leading-6 text-[#725a0b]">
              카드팩은 무상 포인트를 먼저 사용하고, 부족한 금액만 유상 포인트에서 자동으로 차감해요.
            </div>
          )}

          {product.category === 'SEEDLING' && product.plantGuide && (
            <div className="mb-5">
              <PlantCareGuidePanel
                speciesName={product.plantGuide.speciesName}
                accessToken={state.accessToken}
                variant="inline"
              />
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
              {!isGachaPack && (
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
                      onClick={() => setQty(Math.min(maxQuantity, qty + 1))}
                      className="flex h-10 w-10 cursor-pointer items-center justify-center text-xl text-[#6d7a68]"
                    >
                      +
                    </button>
                  </div>
                </div>
              )}
              {isGachaPack ? (
                <button
                  type="button"
                  disabled={purchasing}
                  onClick={purchaseGachaPack}
                  className="w-full cursor-pointer rounded-[13px] bg-[#6750a4] p-[15px] font-extrabold text-white disabled:cursor-wait disabled:opacity-60"
                >
                  {purchasing
                    ? '팩을 준비하고 있어요...'
                    : `${gachaTotalPoint.toLocaleString()}P로 1팩 구매하고 개봉하기`}
                </button>
              ) : (
                <>
                  {alreadyInCart && (
                    <div className="mb-3.5 rounded-[11px] bg-brand-soft px-[13px] py-[11px] text-[13px] font-semibold text-brand-dark">
                      장바구니에 상품이 담겨있습니다.{' '}
                      <Link href="/cart" className="font-extrabold underline">장바구니 보기</Link>
                    </div>
                  )}
                  <div className="flex flex-wrap gap-2.5">
                    <button
                      type="button"
                      disabled={adding || checkingCart || alreadyInCart}
                      onClick={addToCart}
                      className="min-w-[130px] flex-1 cursor-pointer rounded-[13px] bg-brand-soft p-[15px] font-extrabold text-brand-dark disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      {alreadyInCart ? '이미 담겨있어요' : '장바구니 담기'}
                    </button>
                    <button
                      type="button"
                      disabled={adding || checkingCart || alreadyInCart}
                      onClick={async () => {
                        if (await addToCart()) router.push('/cart');
                      }}
                      className="min-w-[130px] flex-1 cursor-pointer rounded-[13px] bg-brand p-[15px] font-extrabold text-white disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      바로 구매
                    </button>
                  </div>
                </>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
