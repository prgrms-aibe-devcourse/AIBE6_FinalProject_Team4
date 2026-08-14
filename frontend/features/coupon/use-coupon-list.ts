"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import {
  firstSearchParam,
  parseOneBasedPage,
} from "@/features/commerce/list-query";
import {
  commerceErrorMessage,
  isAbortError,
} from "@/features/commerce/presentation";
import { CardData, getCards } from "@/features/coupon/api";
import { useStore } from "@/lib/store";

export const COUPON_TABS = [
  { key: "all", label: "전체" },
  { key: "collecting", label: "수집중" },
  { key: "ready", label: "교환가능" },
];

export const COUPON_SORTS = [
  { key: "new", label: "최신순" },
  { key: "low", label: "가격 낮은순" },
  { key: "high", label: "가격 높은순" },
  { key: "progress", label: "진행률순" },
];

const PAGE_SIZE = 12;
export const couponProgress = (card: CardData) =>
  (card.ownedCount ?? 0) / card.requiredCountForExchange;

export type CouponSearchParams = {
  tab?: string | string[];
  sort?: string | string[];
  page?: string | string[];
};

export function useCouponList(searchParams?: CouponSearchParams) {
  const router = useRouter();
  const { state, hydrated } = useStore();
  const requestedFilter = firstSearchParam(searchParams?.tab);
  const requestedSort = firstSearchParam(searchParams?.sort);
  const urlFilter = COUPON_TABS.some((tab) => tab.key === requestedFilter)
    ? requestedFilter!
    : "all";
  const urlSort = COUPON_SORTS.some((item) => item.key === requestedSort)
    ? requestedSort!
    : "new";
  const urlPage = parseOneBasedPage(searchParams?.page);
  const [filter, setFilter] = useState(urlFilter);
  const [sort, setSort] = useState(urlSort);
  const [page, setPage] = useState(urlPage);
  const [cards, setCards] = useState<CardData[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const personalized = cards.some((card) => card.ownedCount !== null);

  useEffect(() => {
    setFilter(urlFilter);
    setSort(urlSort);
    setPage(urlPage);
  }, [urlFilter, urlPage, urlSort]);

  useEffect(() => {
    if (!hydrated) return;
    const controller = new AbortController();
    setLoading(true);
    setError("");
    getCards(state.accessToken, controller.signal)
      .then(setCards)
      .catch((requestError) => {
        if (isAbortError(requestError)) return;
        setCards([]);
        setError(
          commerceErrorMessage(
            requestError,
            "쿠폰을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.",
          ),
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [hydrated, state.accessToken]);

  useEffect(() => {
    if (!hydrated || state.accessToken) return;
    setFilter("all");
    if (sort === "progress") setSort("new");
  }, [hydrated, state.accessToken, sort]);

  const list = useMemo(() => {
    const filtered = cards.filter((card) => {
      if (!personalized) return true;
      if (filter === "ready") {
        return (card.ownedCount ?? 0) >= card.requiredCountForExchange;
      }
      if (filter === "collecting") {
        const ownedCount = card.ownedCount ?? 0;
        return ownedCount > 0 && ownedCount < card.requiredCountForExchange;
      }
      return true;
    });
    if (sort === "low")
      return [...filtered].sort((a, b) => a.pointPrice - b.pointPrice);
    if (sort === "high")
      return [...filtered].sort((a, b) => b.pointPrice - a.pointPrice);
    if (sort === "progress")
      return [...filtered].sort(
        (a, b) => couponProgress(b) - couponProgress(a),
      );
    return [...filtered].sort(
      (a, b) =>
        new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
    );
  }, [cards, filter, personalized, sort]);

  const totalPages = Math.ceil(list.length / PAGE_SIZE);
  const visibleCards = list.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);
  const navigate = useCallback(
    (nextFilter: string, nextSort: string, nextPage: number) => {
      const params = new URLSearchParams();
      if (nextFilter !== "all") params.set("tab", nextFilter);
      if (nextSort !== "new") params.set("sort", nextSort);
      params.set("page", String(nextPage + 1));
      router.replace(`/cards?${params}`, { scroll: false });
    },
    [router],
  );

  const changeFilter = (nextFilter: string) => {
    setFilter(nextFilter);
    setPage(0);
    navigate(nextFilter, sort, 0);
  };
  const changeSort = (nextSort: string) => {
    setSort(nextSort);
    setPage(0);
    navigate(filter, nextSort, 0);
  };
  const changePage = (nextPage: number) => {
    setPage(nextPage);
    navigate(filter, sort, nextPage);
  };

  useEffect(() => {
    if (totalPages > 0 && page >= totalPages) {
      setPage(totalPages - 1);
      navigate(filter, sort, totalPages - 1);
    }
  }, [filter, navigate, page, sort, totalPages]);

  const returnTo = `/cards?${new URLSearchParams({
    ...(filter === "all" ? {} : { tab: filter }),
    ...(sort === "new" ? {} : { sort }),
    page: String(page + 1),
  })}`;

  return {
    filter,
    sort,
    page,
    visibleCards,
    totalPages,
    personalized,
    loading,
    error,
    returnTo,
    changeFilter,
    changeSort,
    changePage,
  };
}
