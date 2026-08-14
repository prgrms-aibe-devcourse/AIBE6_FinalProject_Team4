"use client";

import { Suspense, useCallback, useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { MarketTradeModal } from "@/features/card-market/CardMarketComponents";
import CardMarketHeader, {
  MarketTab,
} from "@/features/card-market/CardMarketHeader";
import {
  MarketListingsSection,
  MarketNegotiationsSection,
  MarketSellSection,
  MarketSort,
  MarketTradesSection,
} from "@/features/card-market/CardMarketSections";
import { parseOneBasedPage } from "@/features/commerce/list-query";
import {
  commerceErrorMessage,
  formatPoint,
  isAbortError,
} from "@/features/commerce/presentation";
import {
  MarketAssetType,
  MarketListing,
  MarketNegotiation,
  MarketSellableCard,
  MarketTrade,
  MarketWallet,
  acceptMarketNegotiation,
  buyMarketListing,
  cancelMarketListing,
  cancelMarketNegotiation,
  createMarketListing,
  createMarketNegotiation,
  getMarketListings,
  getMarketSellableCards,
  getMarketWallet,
  getMyMarketListings,
  getMyMarketNegotiations,
  getMyMarketTrades,
  proposeMarketPrice,
  rejectMarketNegotiation,
} from "@/lib/card-market-api";
import { useStore } from "@/lib/store";
import { useUI } from "@/lib/ui";

export default function CardMarketPage() {
  return (
    <Suspense fallback={<CardMarketPageFallback />}>
      <CardMarketPageContent />
    </Suspense>
  );
}

function CardMarketPageFallback() {
  return (
    <main className="min-h-screen bg-[#f4f6f1] px-4 py-24 text-center font-bold text-[#7a8476]">
      거래소를 불러오는 중...
    </main>
  );
}

function CardMarketPageContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { askConfirm } = useUI();
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

  const submitListing = (card: MarketSellableCard) => {
    if (!token) return;
    const priceValue = Number(sellPrices[card.cardId]);
    const sellerReceived = Math.floor(priceValue * 0.8);
    const assetGuide =
      card.rarity === "GOLDEN_RARE"
        ? `선택한 개체 #${selectedGolden[card.cardId]}가 판매 등록됩니다.`
        : "판매 중에는 해당 카드 1장이 보유 수량에서 분리되며, 판매 취소나 기간 만료 시 돌아옵니다.";
    const execute = () =>
      void runAction(
        () =>
          createMarketListing(
            card.cardId,
            card.rarity === "GOLDEN_RARE"
              ? (selectedGolden[card.cardId] ?? null)
              : null,
            priceValue,
            token,
          ),
        "판매글을 등록했어요.",
      );
    askConfirm({
      icon: "sell",
      title: "판매 등록을 확인해 주세요",
      body: `판매 카드: ${card.cardName}. 등록 가격은 ${formatPoint(priceValue)}이며, 거래 완료 시 수수료 20%를 제외한 ${formatPoint(sellerReceived)}를 받습니다. ${assetGuide}`,
      ok: "판매 등록",
      onOk: () => {
        if (card.rarity !== "GOLDEN_RARE") {
          execute();
          return;
        }
        window.setTimeout(
          () =>
            askConfirm({
              icon: "warning",
              title: "귀중한 골든 카드를 정말 판매할까요?",
              body: `${card.cardName} · 개체 #${selectedGolden[card.cardId]}를 판매합니다. 등록 중에는 다른 거래에 사용할 수 없고, 판매가 완료되면 소유권이 구매자에게 즉시 이전되어 되돌릴 수 없습니다.`,
              ok: "위험을 확인하고 등록",
              danger: true,
              onOk: execute,
            }),
          0,
        );
      },
    });
  };

  const activeListings = useMemo(
    () => myListings.filter((listing) => listing.status === "OPEN"),
    [myListings],
  );
  const negotiations = tab === "sent" ? sent : received;

  return (
    <main className="min-h-screen bg-[#f4f6f1] px-4 pb-24 pt-12 text-[#263023] md:px-8 md:pt-14">
      <div className="mx-auto max-w-[1160px]">
        <CardMarketHeader
          tab={tab}
          authenticated={Boolean(token)}
          wallet={wallet}
          onTab={selectPrivateTab}
        />

        {notice ? (
          <div className="mb-5 rounded-2xl bg-[#edf5e8] px-5 py-4 font-bold text-[#476541]">
            {notice}
          </div>
        ) : null}
        {error ? (
          <div className="mb-5 rounded-2xl bg-[#fff0eb] px-5 py-4 font-bold text-[#a64d35]">
            {error}
          </div>
        ) : null}
        {(tab === "market" ? marketLoading : privateLoading) ? (
          <div className="rounded-3xl bg-white p-16 text-center font-bold text-[#7a8476]">
            거래소를 불러오는 중...
          </div>
        ) : null}

        {!marketLoading && tab === "market" ? (
          <MarketListingsSection
            listings={listings}
            userId={userId}
            assetType={assetType}
            sort={sort}
            keyword={keyword}
            keywordInput={keywordInput}
            page={marketPage}
            totalPages={marketTotalPages}
            onKeywordInput={setKeywordInput}
            onQuery={changeMarketQuery}
            onSelect={selectListing}
          />
        ) : null}

        {!privateLoading && tab === "sell" && token ? (
          <MarketSellSection
            sellable={sellable}
            activeListings={activeListings}
            prices={sellPrices}
            selectedGolden={selectedGolden}
            busy={actionLoading}
            page={privatePage}
            totalPages={privateTotalPages}
            onPrice={(cardId, value) =>
              setSellPrices((current) => ({ ...current, [cardId]: value }))
            }
            onGolden={(cardId, value) =>
              setSelectedGolden((current) => ({ ...current, [cardId]: value }))
            }
            onSubmit={submitListing}
            onCancel={(listing) =>
              void runAction(
                () => cancelMarketListing(listing.id, token),
                "판매를 취소했어요.",
              )
            }
            onPage={changePrivatePage}
          />
        ) : null}

        {!privateLoading && (tab === "sent" || tab === "received") && token ? (
          <MarketNegotiationsSection
            mode={tab}
            negotiations={negotiations}
            userId={userId!}
            prices={counterPrices}
            busy={actionLoading}
            page={privatePage}
            totalPages={privateTotalPages}
            onPrice={(negotiationId, value) =>
              setCounterPrices((current) => ({
                ...current,
                [negotiationId]: value,
              }))
            }
            onAccept={(negotiation) =>
              askConfirm({
                icon: "handshake",
                title: "이 가격으로 거래를 완료할까요?",
                body: `${negotiation.cardName}을 ${formatPoint(negotiation.currentPrice)}에 거래합니다. 거래가 완료되면 카드와 포인트 이동을 취소할 수 없습니다.`,
                ok: "제안 수락",
                onOk: () =>
                  void runAction(
                    () => acceptMarketNegotiation(negotiation.id, token),
                    "가격 제안을 수락해 거래를 완료했어요.",
                  ),
              })
            }
            onReject={(negotiation) =>
              void runAction(
                () => rejectMarketNegotiation(negotiation.id, token),
                "가격 제안을 거절했어요.",
              )
            }
            onCancel={(negotiation) =>
              void runAction(
                () => cancelMarketNegotiation(negotiation.id, token),
                "가격 제안을 취소했어요.",
              )
            }
            onPropose={(negotiation) =>
              void runAction(
                () =>
                  proposeMarketPrice(
                    negotiation.id,
                    Number(counterPrices[negotiation.id]),
                    null,
                    token,
                  ),
                "새로운 가격을 제안했어요.",
              )
            }
            onPage={changePrivatePage}
          />
        ) : null}

        {!privateLoading && tab === "trades" && token ? (
          <MarketTradesSection
            trades={trades}
            userId={userId!}
            page={privatePage}
            totalPages={privateTotalPages}
            onPage={changePrivatePage}
          />
        ) : null}
      </div>

      {selectedListing && token ? (
        <MarketTradeModal
          listing={selectedListing}
          offerPrice={offerPrice}
          busy={actionLoading}
          onOfferPrice={setOfferPrice}
          onClose={() => setSelectedListing(null)}
          onOffer={(price) =>
            void runAction(
              () =>
                createMarketNegotiation(
                  selectedListing.id,
                  price,
                  "PRICE_ADJUST_REQUEST",
                  token,
                ),
              "판매자에게 가격을 제안했어요.",
            )
          }
          onBuy={() =>
            askConfirm({
              icon: "shopping_bag",
              title: "이 카드를 바로 구매할까요?",
              body: `${selectedListing.cardName}을 ${formatPoint(selectedListing.askingPrice)}에 구매합니다. 거래 가능 포인트가 사용되며 완료 후 취소하거나 환불할 수 없습니다.`,
              ok: "구매 확정",
              onOk: () =>
                void runAction(
                  () => buyMarketListing(selectedListing.id, token),
                  "카드 구매를 완료했어요.",
                ),
            })
          }
        />
      ) : null}
    </main>
  );
}
