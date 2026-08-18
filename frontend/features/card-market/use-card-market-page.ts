"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { MarketTab } from "@/features/card-market/CardMarketHeader";
import { MarketSort } from "@/features/card-market/CardMarketSections";
import { parseOneBasedPage } from "@/features/commerce/list-query";
import {
  commerceErrorMessage,
  isAbortError,
} from "@/features/commerce/presentation";
import {
  MarketAssetType,
  MarketListing,
  MarketNegotiation,
  MarketSellableCard,
  MarketTrade,
  MarketWallet,
  getMarketListings,
  getMarketSellableCards,
  getMarketWallet,
  getMyMarketListings,
  getMyMarketNegotiations,
  getMyMarketTrades,
} from "@/features/card-market/api";
import { useStore } from "@/lib/store";

export function useCardMarketPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { state, hydrated, refreshWallet } = useStore();
  const requestedView = searchParams.get("view");
  const [tab, setTab] = useState<MarketTab>(() =>
    requestedView === "sell" ||
    requestedView === "sent" ||
    requestedView === "received" ||
    requestedView === "trades"
      ? requestedView
      : "market",
  );
  const requestedRarity = searchParams.get("rarity");
  const [assetType, setAssetType] = useState<MarketAssetType | undefined>(() =>
    requestedRarity === "HYPER_RARE" || requestedRarity === "GOLDEN_RARE"
      ? requestedRarity
      : undefined,
  );
  const [marketPage, setMarketPage] = useState(() =>
    parseOneBasedPage(searchParams.get("page")),
  );
  const [marketTotalPages, setMarketTotalPages] = useState(0);
  const requestedSort = searchParams.get("sort");
  const [sort, setSort] = useState<MarketSort>(() =>
    requestedSort === "askingPrice,asc" || requestedSort === "askingPrice,desc"
      ? requestedSort
      : "createdAt,desc",
  );
  const [keyword, setKeyword] = useState(searchParams.get("keyword") ?? "");
  const [keywordInput, setKeywordInput] = useState(
    searchParams.get("keyword") ?? "",
  );
  const [privatePage, setPrivatePage] = useState(() =>
    parseOneBasedPage(searchParams.get("myPage")),
  );
  const [privateTotalPages, setPrivateTotalPages] = useState(0);
  const [listings, setListings] = useState<MarketListing[]>([]);
  const [myListings, setMyListings] = useState<MarketListing[]>([]);
  const [sent, setSent] = useState<MarketNegotiation[]>([]);
  const [received, setReceived] = useState<MarketNegotiation[]>([]);
  const [trades, setTrades] = useState<MarketTrade[]>([]);
  const [sellable, setSellable] = useState<MarketSellableCard[]>([]);
  const [wallet, setWallet] = useState<MarketWallet | null>(null);
  const [marketLoading, setMarketLoading] = useState(true);
  const [privateLoading, setPrivateLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [selectedListing, setSelectedListing] = useState<MarketListing | null>(
    null,
  );
  const [offerPrice, setOfferPrice] = useState("");
  const [counterPrices, setCounterPrices] = useState<Record<number, string>>(
    {},
  );
  const [sellPrices, setSellPrices] = useState<Record<number, string>>({});
  const [selectedGolden, setSelectedGolden] = useState<Record<number, number>>(
    {},
  );

  const token = state.accessToken;
  const userId = state.user?.id;

  const loadMarket = useCallback(
    async (signal?: AbortSignal) => {
      setMarketLoading(true);
      setError("");
      try {
        const market = await getMarketListings({
          assetType,
          keyword,
          sort,
          page: marketPage,
          signal,
        });
        setListings(market.content);
        setMarketTotalPages(market.totalPages);
      } catch (loadError) {
        if (!isAbortError(loadError)) {
          setError(
            commerceErrorMessage(
              loadError,
              "판매 목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.",
            ),
          );
        }
      } finally {
        setMarketLoading(false);
      }
    },
    [assetType, keyword, marketPage, sort],
  );

  const loadWallet = useCallback(
    async (signal?: AbortSignal) => {
      if (!token) {
        setWallet(null);
        return;
      }
      setWallet(await getMarketWallet(token, signal));
    },
    [token],
  );

  const loadPrivate = useCallback(
    async (signal?: AbortSignal) => {
      if (!token || tab === "market") return;
      setPrivateLoading(true);
      setError("");
      try {
        if (tab === "sell") {
          const [cards, mine] = await Promise.all([
            getMarketSellableCards(token, signal),
            getMyMarketListings(token, privatePage, signal, "OPEN"),
          ]);
          setSellable(cards);
          setMyListings(mine.content);
          setPrivateTotalPages(mine.totalPages);
        } else if (tab === "sent" || tab === "received") {
          const page = await getMyMarketNegotiations(
            tab,
            token,
            privatePage,
            signal,
          );
          if (tab === "sent") setSent(page.content);
          else setReceived(page.content);
          setPrivateTotalPages(page.totalPages);
        } else {
          const page = await getMyMarketTrades(token, privatePage, signal);
          setTrades(page.content);
          setPrivateTotalPages(page.totalPages);
        }
      } catch (loadError) {
        if (!isAbortError(loadError)) {
          setError(
            commerceErrorMessage(
              loadError,
              "내 거래 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.",
            ),
          );
        }
      } finally {
        setPrivateLoading(false);
      }
    },
    [privatePage, tab, token],
  );

  useEffect(() => {
    const nextAssetType = searchParams.get("rarity");
    setAssetType(
      nextAssetType === "HYPER_RARE" || nextAssetType === "GOLDEN_RARE"
        ? nextAssetType
        : undefined,
    );
    setMarketPage(parseOneBasedPage(searchParams.get("page")));
    const nextSort = searchParams.get("sort");
    setSort(
      nextSort === "askingPrice,asc" || nextSort === "askingPrice,desc"
        ? nextSort
        : "createdAt,desc",
    );
    const nextKeyword = searchParams.get("keyword") ?? "";
    setKeyword(nextKeyword);
    setKeywordInput(nextKeyword);
    const nextView = searchParams.get("view");
    setTab(
      nextView === "sell" ||
        nextView === "sent" ||
        nextView === "received" ||
        nextView === "trades"
        ? nextView
        : "market",
    );
    setPrivatePage(parseOneBasedPage(searchParams.get("myPage")));
  }, [searchParams]);

  useEffect(() => {
    if (!hydrated) return;
    const controller = new AbortController();
    void loadMarket(controller.signal);
    return () => controller.abort();
  }, [hydrated, loadMarket]);

  useEffect(() => {
    if (!hydrated) return;
    const controller = new AbortController();
    void loadWallet(controller.signal).catch((loadError) => {
      if (!isAbortError(loadError)) {
        setError(
          commerceErrorMessage(
            loadError,
            "포인트 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.",
          ),
        );
      }
    });
    return () => controller.abort();
  }, [hydrated, loadWallet]);

  useEffect(() => {
    if (!hydrated) return;
    const controller = new AbortController();
    void loadPrivate(controller.signal);
    return () => controller.abort();
  }, [hydrated, loadPrivate]);

  const runAction = async (action: () => Promise<unknown>, success: string) => {
    setActionLoading(true);
    setError("");
    setNotice("");
    try {
      await action();
      setSelectedListing(null);
      setOfferPrice("");
      setNotice(success);
      await Promise.all([
        loadMarket(),
        loadWallet(),
        loadPrivate(),
        refreshWallet(),
      ]);
    } catch (actionError) {
      setError(
        commerceErrorMessage(
          actionError,
          "처리하지 못했어요. 잠시 후 다시 시도해 주세요.",
        ),
      );
    } finally {
      setActionLoading(false);
    }
  };

  const selectPrivateTab = (next: MarketTab) => {
    if (next !== "market" && !token) {
      setNotice("로그인하면 카드 판매와 가격 협상을 이용할 수 있어요.");
      return;
    }
    const params = new URLSearchParams(searchParams.toString());
    if (next === "market") params.delete("view");
    else params.set("view", next);
    params.delete("myPage");
    setTab(next);
    setPrivatePage(0);
    router.replace(params.size ? `/card-market?${params}` : "/card-market", {
      scroll: false,
    });
  };

  const changeMarketQuery = (
    nextAssetType: MarketAssetType | undefined,
    nextPage: number,
    nextSort: MarketSort = sort,
    nextKeyword: string = keyword,
  ) => {
    const params = new URLSearchParams(searchParams.toString());
    if (nextAssetType) params.set("rarity", nextAssetType);
    else params.delete("rarity");
    if (nextPage > 0) params.set("page", String(nextPage + 1));
    else params.delete("page");
    if (nextSort !== "createdAt,desc") params.set("sort", nextSort);
    else params.delete("sort");
    if (nextKeyword.trim()) params.set("keyword", nextKeyword.trim());
    else params.delete("keyword");
    setAssetType(nextAssetType);
    setMarketPage(nextPage);
    setSort(nextSort);
    setKeyword(nextKeyword.trim());
    const query = params.toString();
    router.replace(query ? `/card-market?${query}` : "/card-market", {
      scroll: false,
    });
  };

  const changePrivatePage = (nextPage: number) => {
    const params = new URLSearchParams(searchParams.toString());
    if (nextPage > 0) params.set("myPage", String(nextPage + 1));
    else params.delete("myPage");
    setPrivatePage(nextPage);
    router.replace(`/card-market?${params}`, { scroll: false });
  };

  const selectListing = (listing: MarketListing) => {
    if (!token) {
      setNotice("로그인 후 구매할 수 있어요.");
      return;
    }
    setSelectedListing(listing);
    setOfferPrice("");
  };

  const activeListings = useMemo(
    () => myListings.filter((listing) => listing.status === "OPEN"),
    [myListings],
  );

  return {
    token,
    userId,
    tab,
    assetType,
    marketPage,
    marketTotalPages,
    sort,
    keyword,
    keywordInput,
    privatePage,
    privateTotalPages,
    listings,
    sent,
    received,
    trades,
    sellable,
    wallet,
    marketLoading,
    privateLoading,
    actionLoading,
    error,
    notice,
    selectedListing,
    offerPrice,
    counterPrices,
    sellPrices,
    selectedGolden,
    activeListings,
    setKeywordInput,
    setSelectedListing,
    setOfferPrice,
    setCounterPrices,
    setSellPrices,
    setSelectedGolden,
    runAction,
    selectPrivateTab,
    changeMarketQuery,
    changePrivatePage,
    selectListing,
  };
}
