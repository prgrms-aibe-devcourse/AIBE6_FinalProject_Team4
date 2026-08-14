'use client';
import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { ApiError } from '@/lib/api';
import { withTopicParticle } from '@/lib/korean';
import { purchaseGachaPacks } from '@/lib/gacha-api';
import { getProduct, ProductDetail as ProductDetailData } from '@/lib/product-api';
import { addCartItem, getCart } from '@/lib/order-api';
import { useStore } from '@/lib/store';
import { useUI } from '@/lib/ui';
import PointPrice from '@/components/PointPrice';

export default function ProductDetail({
  params,
  searchParams,
}: {
  params: { id: string };
  searchParams?: { returnTo?: string | string[] };
}) {
  const router = useRouter();
  const { state, hydrated, refreshWallet, walletLoaded, walletLoading, refreshCartCount } = useStore();
  const { showToast, askConfirm } = useUI();
  const productId = Number(params.id);
  const requestedReturnTo = Array.isArray(searchParams?.returnTo)
    ? searchParams?.returnTo[0]
    : searchParams?.returnTo;
  const returnTo = requestedReturnTo?.startsWith('/shop')
    ? requestedReturnTo
    : '/shop';
  const [product, setProduct] = useState<ProductDetailData | null>(null);
  const [qty, setQty] = useState(1);
  const [loading, setLoading] = useState(true);
  const [purchasing, setPurchasing] = useState(false);
  const [error, setError] = useState('');
  const purchaseAttempt = useRef<{ signature: string; key: string } | null>(null);
  const [adding, setAdding] = useState(false);
  const [alreadyInCart, setAlreadyInCart] = useState(false);
  const [checkingCart, setCheckingCart] = useState(true);

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

  // 로그인 상태면 이 상품이 이미 장바구니에 있는지 확인해서 중복 담기를 막는다. 확인이 끝나기
  // 전까지는 버튼을 눌러도 진행되지 않게 막아서, 조회가 끝나기 전에 클릭해 중복 담기가
  // 통과해버리는 경쟁 상태를 막는다.
  useEffect(() => {
    if (!hydrated) return;
    if (!state.accessToken) {
      setAlreadyInCart(false);
      setCheckingCart(false);
      return;
    }
    let cancelled = false;
    setCheckingCart(true);
    getCart(state.accessToken)
      .then((cart) => {
        if (cancelled) return;
        setAlreadyInCart(cart.items.some((item) => item.productId === productId));
      })
      .catch(() => {
        // 조회 실패해도 담기 자체는 서버가 다시 검증하니 조용히 무시한다.
      })
      .finally(() => {
        if (!cancelled) setCheckingCart(false);
      });
    return () => { cancelled = true; };
  }, [hydrated, state.accessToken, productId]);

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
            href={returnTo}
            className="mt-4 inline-block rounded-xl bg-brand px-5 py-2.5 font-bold text-white hover:text-white"
          >
            상점으로 돌아가기
          </Link>
        </div>
      </div>
    );
  }

  // 서버가 실제 소유권·재고·1~99 범위를 다시 검증하므로 여기서는 로그인 여부와 중복 담기만 먼저 막는다.
  const addToCart = async (): Promise<boolean> => {
    if (!hydrated || !state.accessToken) {
      showToast('장바구니와 구매 기능은 로그인 후 이용할 수 있어요.', 'err');
      return false;
    }
    if (checkingCart) {
      showToast('장바구니 확인 중이에요. 잠시 후 다시 시도해 주세요.', 'err');
      return false;
    }
    if (alreadyInCart) {
      showToast('이미 장바구니에 있는 상품이에요.', 'err');
      return false;
    }
    setAdding(true);
    try {
      await addCartItem(product.id, qty, state.accessToken);
      await refreshCartCount();
      setAlreadyInCart(true);
      showToast('장바구니에 담았어요 🛒');
      return true;
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError ? requestError.message : '장바구니에 담지 못했어요.',
        'err',
      );
      return false;
    } finally {
      setAdding(false);
    }
  };

  const isGachaPack = product.category === 'GACHA_PACK';
  const maxQuantity = product.stock;
  const gachaPackQuantity = 1;
  const gachaTotalPoint = product.pointPrice;

  const purchaseGachaPack = () => {
    if (!hydrated || !state.accessToken) {
      showToast('가챠 팩 구매는 로그인 후 이용할 수 있어요.', 'err');
      return;
    }
    if (!walletLoaded) {
      showToast(
        walletLoading
          ? '포인트 잔액을 확인하고 있어요.'
          : '포인트 잔액을 확인하지 못했어요. 잠시 후 다시 시도해 주세요.',
        'err',
      );
      return;
    }
    if (state.wallet.free + state.wallet.paid < gachaTotalPoint) {
      showToast('사용 가능한 포인트가 부족해요.', 'err');
      return;
    }

    askConfirm({
      icon: 'casino',
      title: '가챠 팩 1개를 구매할까요?',
      body: `총 ${gachaTotalPoint.toLocaleString()}P를 사용하고 구매 즉시 개봉합니다.`,
      ok: '구매하고 개봉하기',
      onOk: async () => {
        setPurchasing(true);
        try {
          const signature = `${product.id}:${gachaPackQuantity}:${gachaTotalPoint}`;
          const idempotencyKey =
            purchaseAttempt.current?.signature === signature
              ? purchaseAttempt.current.key
              : crypto.randomUUID();
          purchaseAttempt.current = { signature, key: idempotencyKey };
          const response = await purchaseGachaPacks(
            product.id,
            gachaPackQuantity,
            gachaTotalPoint,
            state.accessToken!,
            idempotencyKey,
          );
          purchaseAttempt.current = null;
          await refreshWallet().catch(() => undefined);
          showToast(`${response.quantity}팩 구매가 완료됐어요!`);
          router.push(`/gacha/open/${response.drawIds[0]}`);
        } catch (purchaseError) {
          if (
            purchaseError instanceof ApiError &&
            purchaseError.code === 'GACHA_PRODUCT_PRICE_CHANGED'
          ) {
            purchaseAttempt.current = null;
            try {
              setProduct(await getProduct(product.id));
            } catch {
              // 다음 화면 진입 또는 새로고침에서도 서버가 현재 가격을 다시 검증한다.
            }
          }
          showToast(
            purchaseError instanceof ApiError
              ? purchaseError.message
              : '가챠 팩을 구매하지 못했어요. 잠시 후 다시 시도해 주세요.',
            'err',
          );
        } finally {
          setPurchasing(false);
        }
      },
    });
  };

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
          {!product.imageUrl && '🌱'}
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
                      이미 장바구니에 있는 상품이에요.{' '}
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
