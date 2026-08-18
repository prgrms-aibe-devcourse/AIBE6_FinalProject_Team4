"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { runIdempotentMutation } from "@/features/commerce/idempotent-mutation";
import { isAbortError } from "@/features/commerce/presentation";
import { getProduct, ProductDetail } from "@/features/shop/api";
import { ApiError } from "@/lib/api";
import { purchaseGachaPacks } from "@/lib/gacha-api";
import { addCartItem, getCart } from "@/lib/order-api";
import { useStore } from "@/lib/store";
import { useUI } from "@/lib/ui";

export function useProductDetail({
  id,
  requestedReturnTo,
}: {
  id: string;
  requestedReturnTo?: string | string[];
}) {
  const router = useRouter();
  const {
    state,
    hydrated,
    refreshWallet,
    walletLoaded,
    walletLoading,
    refreshCartCount,
  } = useStore();
  const { showToast, askConfirm } = useUI();
  const productId = Number(id);
  const returnValue = Array.isArray(requestedReturnTo)
    ? requestedReturnTo[0]
    : requestedReturnTo;
  const returnTo = returnValue?.startsWith("/shop") ? returnValue : "/shop";
  const [product, setProduct] = useState<ProductDetail | null>(null);
  const [qty, setQty] = useState(1);
  const [loading, setLoading] = useState(true);
  const [purchasing, setPurchasing] = useState(false);
  const [error, setError] = useState("");
  const [adding, setAdding] = useState(false);
  const [alreadyInCart, setAlreadyInCart] = useState(false);
  const [checkingCart, setCheckingCart] = useState(true);

  useEffect(() => {
    if (!Number.isInteger(productId) || productId < 1) {
      setError("잘못된 상품 주소예요.");
      setLoading(false);
      return;
    }
    const controller = new AbortController();
    setLoading(true);
    setError("");
    getProduct(productId, undefined, controller.signal)
      .then(setProduct)
      .catch((requestError) => {
        if (isAbortError(requestError)) return;
        setProduct(null);
        setError(
          requestError instanceof ApiError
            ? requestError.message
            : "상품을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.",
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [productId]);

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
        if (!cancelled) {
          setAlreadyInCart(
            cart.items.some((item) => item.productId === productId),
          );
        }
      })
      .catch(() => undefined)
      .finally(() => {
        if (!cancelled) setCheckingCart(false);
      });
    return () => {
      cancelled = true;
    };
  }, [hydrated, productId, state.accessToken]);

  const addToCart = async (): Promise<boolean> => {
    if (!product || !hydrated || !state.accessToken) {
      showToast("장바구니와 구매 기능은 로그인 후 이용할 수 있어요.", "err");
      return false;
    }
    if (checkingCart) {
      showToast("장바구니 확인 중이에요. 잠시 후 다시 시도해 주세요.", "err");
      return false;
    }
    if (alreadyInCart) {
      showToast("이미 장바구니에 있는 상품이에요.", "err");
      return false;
    }
    setAdding(true);
    try {
      await addCartItem(product.id, qty, state.accessToken);
      await refreshCartCount();
      setAlreadyInCart(true);
      showToast("장바구니에 담았어요 🛒");
      return true;
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError
          ? requestError.message
          : "장바구니에 담지 못했어요.",
        "err",
      );
      return false;
    } finally {
      setAdding(false);
    }
  };

  const isGachaPack = product?.category === "GACHA_PACK";
  const maxQuantity = product?.stock ?? 0;
  const gachaTotalPoint = product?.pointPrice ?? 0;

  const purchaseGachaPack = () => {
    if (!product || !hydrated || !state.accessToken) {
      showToast("가챠 팩 구매는 로그인 후 이용할 수 있어요.", "err");
      return;
    }
    if (!walletLoaded) {
      showToast(
        walletLoading
          ? "포인트 잔액을 확인하고 있어요."
          : "포인트 잔액을 확인하지 못했어요. 잠시 후 다시 시도해 주세요.",
        "err",
      );
      return;
    }
    if (state.wallet.free + state.wallet.paid < gachaTotalPoint) {
      showToast("사용 가능한 포인트가 부족해요.", "err");
      return;
    }

    askConfirm({
      icon: "casino",
      title: "가챠 팩 1개를 구매할까요?",
      body: `총 ${gachaTotalPoint.toLocaleString()}P를 사용하고 구매 즉시 개봉합니다.`,
      ok: "구매하고 개봉하기",
      onOk: async () => {
        setPurchasing(true);
        try {
          const signature = `${product.id}:1:${gachaTotalPoint}`;
          const response = await runIdempotentMutation(
            "gacha-pack-purchase",
            signature,
            (idempotencyKey) =>
              purchaseGachaPacks(
                product.id,
                1,
                gachaTotalPoint,
                state.accessToken!,
                idempotencyKey,
              ),
          );
          await refreshWallet().catch(() => undefined);
          showToast(`${response.quantity}팩 구매가 완료됐어요!`);
          router.push(`/gacha/open/${response.drawIds[0]}`);
        } catch (purchaseError) {
          if (
            purchaseError instanceof ApiError &&
            purchaseError.code === "GACHA_PRODUCT_PRICE_CHANGED"
          ) {
            try {
              setProduct(await getProduct(product.id));
            } catch {
              // 다음 진입에서도 서버가 현재 가격을 다시 검증한다.
            }
          }
          showToast(
            purchaseError instanceof ApiError
              ? purchaseError.message
              : "가챠 팩을 구매하지 못했어요. 잠시 후 다시 시도해 주세요.",
            "err",
          );
        } finally {
          setPurchasing(false);
        }
      },
    });
  };

  return {
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
  };
}
