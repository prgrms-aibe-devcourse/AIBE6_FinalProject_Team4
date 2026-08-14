"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  firstSearchParam,
  parseOneBasedPage,
} from "@/features/commerce/list-query";
import {
  commerceErrorMessage,
  isAbortError,
} from "@/features/commerce/presentation";
import {
  getProducts,
  ProductCategory,
  ProductListItem,
  ProductSort,
} from "@/features/shop/api";

export const SHOP_TABS = [
  { key: "all", label: "전체" },
  { key: "KIT", label: "키트" },
  { key: "SEEDLING", label: "모종" },
  { key: "GACHA_PACK", label: "가챠" },
];

export const SHOP_SORTS = [
  { key: "new", label: "최신순" },
  { key: "low", label: "가격 낮은순" },
  { key: "high", label: "가격 높은순" },
];

export const SHOP_CATEGORY_LABEL: Record<ProductCategory, string> = {
  KIT: "키트",
  SEEDLING: "모종",
  GACHA_PACK: "가챠 팩",
};

const SORT_QUERY: Record<string, ProductSort> = {
  new: "LATEST",
  low: "PRICE_ASC",
  high: "PRICE_DESC",
};

export type ShopSearchParams = {
  category?: string | string[];
  sort?: string | string[];
  page?: string | string[];
};

export function useShopList(searchParams?: ShopSearchParams) {
  const router = useRouter();
  const requestedCategory = firstSearchParam(searchParams?.category);
  const requestedSort = firstSearchParam(searchParams?.sort);
  const urlCategory = SHOP_TABS.some((tab) => tab.key === requestedCategory)
    ? requestedCategory!
    : "all";
  const urlSort = SHOP_SORTS.some((sort) => sort.key === requestedSort)
    ? requestedSort!
    : "new";
  const urlPage = parseOneBasedPage(searchParams?.page);
  const [cat, setCat] = useState(urlCategory);
  const [sort, setSort] = useState(urlSort);
  const [page, setPage] = useState(urlPage);
  const [products, setProducts] = useState<ProductListItem[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    setCat(urlCategory);
    setSort(urlSort);
    setPage(urlPage);
  }, [urlCategory, urlPage, urlSort]);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError("");
    getProducts({
      category: cat === "all" ? undefined : (cat as ProductCategory),
      sort: SORT_QUERY[sort],
      page,
      signal: controller.signal,
    })
      .then((response) => {
        setProducts(response.content);
        setTotalPages(response.totalPages);
      })
      .catch((requestError) => {
        if (isAbortError(requestError)) return;
        setProducts([]);
        setError(
          commerceErrorMessage(
            requestError,
            "상품을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.",
          ),
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
      if (nextCategory !== "all") params.set("category", nextCategory);
      if (nextSort !== "new") params.set("sort", nextSort);
      params.set("page", String(nextPage + 1));
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
    ...(cat === "all" ? {} : { category: cat }),
    ...(sort === "new" ? {} : { sort }),
    page: String(page + 1),
  })}`;

  return {
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
  };
}
