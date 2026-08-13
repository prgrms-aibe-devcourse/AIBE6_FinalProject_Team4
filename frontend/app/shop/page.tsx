'use client';
import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import FilterBar from '@/components/FilterBar';
import PointPrice from '@/components/PointPrice';
import { ApiError } from '@/lib/api';
import {
  getProducts,
  ProductCategory,
  ProductListItem,
  ProductSort,
} from '@/lib/product-api';

const TABS = [
  { key: 'all', label: '전체' },
  { key: 'KIT', label: '키트' },
  { key: 'SEEDLING', label: '모종' },
  { key: 'GACHA_PACK', label: '가챠' },
];

const SORTS = [
  { key: 'new', label: '최신순' },
  { key: 'low', label: '가격 낮은순' },
  { key: 'high', label: '가격 높은순' },
];

const CAT_LABEL: Record<ProductCategory, string> = {
  KIT: '키트',
  SEEDLING: '모종',
  GACHA_PACK: '가챠 팩',
};
const SORT_QUERY: Record<string, ProductSort> = {
  new: 'LATEST',
  low: 'PRICE_ASC',
  high: 'PRICE_DESC',
};

type ShopSearchParams = {
  category?: string | string[];
  sort?: string | string[];
  page?: string | string[];
};

const queryValue = (value?: string | string[]) =>
  Array.isArray(value) ? value[0] : value;

const initialCategory = (params?: ShopSearchParams) => {
  const value = queryValue(params?.category);
  return TABS.some((tab) => tab.key === value) ? value! : 'all';
};

const initialSort = (params?: ShopSearchParams) => {
  const value = queryValue(params?.sort);
  return SORTS.some((sort) => sort.key === value) ? value! : 'new';
};

const initialPage = (params?: ShopSearchParams) => {
  const value = Number(queryValue(params?.page));
  return Number.isInteger(value) && value > 0 ? value - 1 : 0;
};

export default function Shop({ searchParams }: { searchParams?: ShopSearchParams }) {
  const router = useRouter();
  const urlCategory = initialCategory(searchParams);
  const urlSort = initialSort(searchParams);
  const urlPage = initialPage(searchParams);
  const [cat, setCat] = useState(urlCategory);
  const [sort, setSort] = useState(urlSort);
  const [page, setPage] = useState(urlPage);
  const [products, setProducts] = useState<ProductListItem[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    setCat(urlCategory);
    setSort(urlSort);
    setPage(urlPage);
  }, [urlCategory, urlPage, urlSort]);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');

    getProducts({
      category: cat === 'all' ? undefined : (cat as ProductCategory),
      sort: SORT_QUERY[sort],
      page,
      signal: controller.signal,
    })
      .then((response) => {
        setProducts(response.content);
        setTotalPages(response.totalPages);
      })
      .catch((requestError) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return;
        setProducts([]);
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
  }, [cat, page, sort]);

  const navigate = useCallback(
    (nextCategory: string, nextSort: string, nextPage: number) => {
      const params = new URLSearchParams();
      if (nextCategory !== 'all') params.set('category', nextCategory);
      if (nextSort !== 'new') params.set('sort', nextSort);
      params.set('page', String(nextPage + 1));
      router.replace(`/shop?${params}`, { scroll: false });
    },
    [router],
  );

  useEffect(() => {
    if (totalPages > 0 && page >= totalPages) {
      setPage(totalPages - 1);
      navigate(cat, sort, totalPages - 1);
    }
  }, [cat, navigate, page, sort, totalPages]);

  const changeCategory = (nextCategory: string) => {
    setCat(nextCategory);
    setPage(0);
    navigate(nextCategory, sort, 0);
  };

  const changeSort = (nextSort: string) => {
    setSort(nextSort);
    setPage(0);
    navigate(cat, nextSort, 0);
  };

  const changePage = (nextPage: number) => {
    setPage(nextPage);
    navigate(cat, sort, nextPage);
  };

  const returnTo = `/shop?${new URLSearchParams({
    ...(cat === 'all' ? {} : { category: cat }),
    ...(sort === 'new' ? {} : { sort }),
    page: String(page + 1),
  })}`;

  return (
    <div className="container animate-upIn">
      <h1 className="mb-4 text-2xl font-extrabold">상점</h1>

      <FilterBar
        tabs={TABS}
        activeTab={cat}
        onTab={changeCategory}
        sorts={SORTS}
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
                    {CAT_LABEL[product.category]}
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
