'use client';

import Link from 'next/link';
import FilterBar from '@/components/FilterBar';
import PointPrice from '@/components/PointPrice';
import {
  SHOP_CATEGORY_LABEL,
  SHOP_SORTS,
  SHOP_TABS,
  type ShopSearchParams,
  useShopList,
} from '@/features/shop/use-shop-list';

export default function Shop({ searchParams }: { searchParams?: ShopSearchParams }) {
  const {
    cat,
    sort,
    page,
    products,
    totalPages,
    loading,
    error,
    returnTo,
    changeCategory,
    changeSort,
    changePage,
  } = useShopList(searchParams);

  return (
    <div className="container animate-upIn">
      <h1 className="mb-4 text-2xl font-extrabold">상점</h1>

      <FilterBar
        tabs={SHOP_TABS}
        activeTab={cat}
        onTab={changeCategory}
        sorts={SHOP_SORTS}
        activeSort={sort}
        onSort={changeSort}
      />

      {cat === 'GACHA_PACK' ? (
        <div className="mb-5 rounded-2xl border border-[#e5d899] bg-[#fff9df] px-4 py-3 text-sm leading-6 text-[#725a0b]">
          카드팩은 구매 시 무상 포인트를 먼저 사용하고, 부족한 금액만 유상 포인트에서 자동으로 차감됩니다.
        </div>
      ) : null}

      {loading ? (
        <div className="rounded-[22px] bg-white py-14 text-center text-[15px] text-sub">
          상품을 불러오고 있어요 🌱
        </div>
      ) : error ? (
        <div className="rounded-[22px] bg-white px-5 py-14 text-center text-[15px] text-sub">
          <p>{error}</p>
        </div>
      ) : products.length === 0 ? (
        <div className="rounded-[22px] bg-white py-14 text-center text-[15px] text-sub">
          찾으시는 상품이 아직 없어요. 곧 새 식구들이 들어올 예정이에요!
        </div>
      ) : (
        <>
          <div className="grid gap-4 [grid-template-columns:repeat(auto-fill,minmax(210px,1fr))]">
            {products.map((product) => (
              <Link
                key={product.id}
                href={`/shop/${product.id}?returnTo=${encodeURIComponent(returnTo)}`}
                className="block overflow-hidden rounded-[20px] bg-white text-ink shadow-card hover:text-ink"
              >
                <div
                  className={`relative grid h-[150px] place-items-center bg-brand-soft bg-center text-[46px] ${
                    product.category === 'GACHA_PACK'
                      ? 'bg-contain bg-no-repeat'
                      : 'bg-cover'
                  } ${
                    product.soldOut ? 'opacity-70 grayscale' : ''
                  }`}
                  style={
                    product.imageUrl
                      ? { backgroundImage: `url("${product.imageUrl}")` }
                      : undefined
                  }
                >
                  {!product.imageUrl && <span>🌱</span>}
                  {product.soldOut && (
                    <span className="absolute left-3 top-3 rounded-lg bg-sub px-3 py-1 text-xs font-bold text-white">
                      품절
                    </span>
                  )}
                </div>
                <div className="px-4 py-3.5">
                  <div className="mb-[7px] inline-block rounded-md bg-brand-soft px-[9px] py-[3px] text-[11px] font-bold text-brand-dark">
                    {SHOP_CATEGORY_LABEL[product.category]}
                  </div>
                  <div className="text-[15.5px] font-bold">{product.name}</div>
                  <PointPrice value={product.pointPrice} className="mt-1.5" />
                </div>
              </Link>
            ))}
          </div>

          {totalPages > 1 && (
            <div className="mt-7 flex items-center justify-center gap-3">
              <button
                type="button"
                disabled={page === 0}
                onClick={() => changePage(Math.max(0, page - 1))}
                className="rounded-xl border border-line bg-white px-4 py-2 font-bold disabled:cursor-not-allowed disabled:opacity-40"
              >
                이전
              </button>
              <span className="text-sm font-bold text-sub">
                {page + 1} / {totalPages}
              </span>
              <button
                type="button"
                disabled={page + 1 >= totalPages}
                onClick={() => changePage(page + 1)}
                className="rounded-xl border border-line bg-white px-4 py-2 font-bold disabled:cursor-not-allowed disabled:opacity-40"
              >
                다음
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
