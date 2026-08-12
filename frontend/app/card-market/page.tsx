"use client";

import { Suspense, useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { ApiError } from "@/lib/api";
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

type Tab = "market" | "sell" | "sent" | "received" | "trades";
type MarketSort = "createdAt,desc" | "askingPrice,asc" | "askingPrice,desc";

const WON = new Intl.NumberFormat("ko-KR");
const STATUS_LABEL: Record<string, string> = {
  OPEN: "판매 중",
  SOLD: "판매 완료",
  CANCELLED: "취소",
  EXPIRED: "기간 만료",
  NEGOTIATING: "협상 중",
  ACCEPTED: "거래 완료",
  REJECTED: "거절",
  LISTING_CLOSED: "판매글 종료",
};

function point(value: number) {
  return `${WON.format(value)}P`;
}

function dateTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

function errorMessage(error: unknown) {
  return error instanceof ApiError
    ? error.message
    : "처리하지 못했어요. 잠시 후 다시 시도해 주세요.";
}

function CardImage({ src, name }: { src: string | null; name: string }) {
  return src ? (
    // 외부 S3/CDN 주소를 그대로 표시하며 카드 원본 비율을 유지한다.
    // eslint-disable-next-line @next/next/no-img-element
    <img src={src} alt={name} className="h-full w-full object-contain" />
  ) : (
    <div className="flex h-full items-center justify-center bg-[#e8ece4] text-[#788173]">
      <span className="material-symbols-outlined text-4xl">playing_cards</span>
    </div>
  );
}

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
  const [tab, setTab] = useState<Tab>(() =>
    requestedView === "sell" ||
    requestedView === "sent" ||
    requestedView === "received" ||
    requestedView === "trades"
      ? requestedView
      : "market",
  );
  const requestedAssetType = searchParams.get("rarity");
  const requestedPage = Number(searchParams.get("page"));
  const [assetType, setAssetType] = useState<MarketAssetType | undefined>(() =>
    requestedAssetType === "HYPER_RARE" || requestedAssetType === "GOLDEN_RARE"
      ? requestedAssetType
      : undefined,
  );
  const [marketPage, setMarketPage] = useState(() =>
    Number.isInteger(requestedPage) && requestedPage > 0
      ? requestedPage - 1
      : 0,
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
  const requestedPrivatePage = Number(searchParams.get("myPage"));
  const [privatePage, setPrivatePage] = useState(() =>
    Number.isInteger(requestedPrivatePage) && requestedPrivatePage > 0
      ? requestedPrivatePage - 1
      : 0,
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
        if (!(
          loadError instanceof DOMException && loadError.name === "AbortError"
        )) {
          setError(errorMessage(loadError));
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
        if (!(
          loadError instanceof DOMException && loadError.name === "AbortError"
        )) {
          setError(errorMessage(loadError));
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
    const nextPage = Number(searchParams.get("page"));
    setMarketPage(
      Number.isInteger(nextPage) && nextPage > 0 ? nextPage - 1 : 0,
    );
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
    const nextPrivatePage = Number(searchParams.get("myPage"));
    setPrivatePage(
      Number.isInteger(nextPrivatePage) && nextPrivatePage > 0
        ? nextPrivatePage - 1
        : 0,
    );
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
      if (!(
        loadError instanceof DOMException && loadError.name === "AbortError"
      )) {
        setError(errorMessage(loadError));
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
      setError(errorMessage(actionError));
    } finally {
      setActionLoading(false);
    }
  };

  const selectPrivateTab = (next: Tab) => {
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

  const activeListings = useMemo(
    () => myListings.filter((listing) => listing.status === "OPEN"),
    [myListings],
  );

  return (
    <main className="min-h-screen bg-[#f4f6f1] px-4 pb-24 pt-12 text-[#263023] md:px-8 md:pt-14">
      <div className="mx-auto max-w-[1160px]">
        <section className="relative mb-12 overflow-hidden rounded-[32px] bg-gradient-to-br from-[#1f3023] via-[#3f5b3d] to-[#9a7b26] px-7 py-11 text-white shadow-xl md:px-12 md:py-14">
          <div className="relative z-10 max-w-2xl">
            <span className="mb-6 inline-flex rounded-full border border-white/15 bg-white/10 px-4 py-2 text-[11px] font-black tracking-[0.1em] text-[#f4e7ae] backdrop-blur-sm">
              HYPER · GOLDEN ONLY
            </span>
            <p className="mb-2 text-xs font-black uppercase tracking-[0.24em] text-[#e8dda7]">
              Card market
            </p>
            <h1 className="text-3xl font-black tracking-[-0.04em] md:text-5xl">
              카드 거래소
            </h1>
            <p className="mt-4 max-w-xl text-sm leading-6 text-white/75 md:text-base">
              하이퍼와 골든 카드를 판매하거나 원하는 가격을 제안해 보세요.
              소중한 컬렉션을 안전한 규칙 안에서 거래할 수 있어요.
            </p>
          </div>
          <div className="absolute -bottom-20 -right-10 h-64 w-64 rounded-full bg-[#f4d76e]/20 blur-3xl" />
        </section>

        <section className="mb-14 grid gap-6 lg:grid-cols-[1.15fr_0.85fr]">
          <div className="rounded-[28px] border border-[#d9e2d4] bg-white p-7 shadow-[0_18px_45px_-32px_rgba(39,67,35,.45)] md:p-8">
            <div className="flex items-start gap-4">
              <span className="material-symbols-outlined grid h-12 w-12 shrink-0 place-items-center rounded-2xl bg-[#eaf1e5] text-2xl text-[#496541]">
                verified_user
              </span>
              <div>
                <p className="text-xs font-black uppercase tracking-[0.16em] text-[#83907d]">
                  Safe trading policy
                </p>
                <h2 className="mt-1 text-xl font-black md:text-2xl">
                  왜 충전한 포인트만 사용할까요?
                </h2>
                <p className="mt-3 text-sm leading-6 text-[#687264]">
                  활동 보상으로 받은 포인트가 계정 간 거래를 통해 양도되거나
                  현금처럼 바뀌는 악용을 막고, 판매자의 거래 가치를 보호하기
                  위해서예요. 카드 구매와 가격 제안에는 결제로 충전한 포인트만
                  사용할 수 있습니다.
                </p>
              </div>
            </div>
            <div className="mt-6 flex flex-wrap gap-2 border-t border-[#edf1ea] pt-5">
              {[
                "구매·가격 제안 공통",
                "판매 수수료 20%",
                "거래 완료 후 취소 불가",
              ].map((guide) => (
                <span
                  key={guide}
                  className="rounded-full bg-[#f2f5ef] px-3 py-1.5 text-xs font-bold text-[#667260]"
                >
                  {guide}
                </span>
              ))}
            </div>
          </div>

          {token && wallet ? (
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-1">
              <WalletCard
                icon="account_balance_wallet"
                label="거래 가능 포인트"
                value={point(wallet.paidPoint)}
                tone="gold"
              />
              <WalletCard
                icon="lock"
                label="가격 제안 보관 중"
                value={point(wallet.escrowedPaidPoint)}
                tone="green"
              />
            </div>
          ) : (
            <div className="flex min-h-48 flex-col justify-center rounded-[28px] border border-dashed border-[#cbd6c5] bg-white/55 p-8 text-center">
              <span className="material-symbols-outlined text-3xl text-[#75836e]">
                account_circle
              </span>
              <p className="mt-3 font-black">로그인 후 거래를 시작해 보세요.</p>
              <p className="mt-2 text-sm leading-6 text-[#758071]">
                내 거래 가능 금액과 가격 제안 현황을 확인할 수 있어요.
              </p>
            </div>
          )}
        </section>

        <nav className="mb-12 grid grid-cols-2 gap-3 rounded-[26px] border border-[#dde4d8] bg-white p-3 shadow-[0_16px_38px_-30px_rgba(39,67,35,.5)] md:grid-cols-5">
          {(
            [
              ["market", "판매 목록", "storefront"],
              ["sell", "내 판매", "sell"],
              ["sent", "보낸 제안", "outgoing_mail"],
              ["received", "받은 제안", "inbox"],
              ["trades", "거래 내역", "receipt_long"],
            ] as const
          ).map(([key, label, icon]) => (
            <button
              key={key}
              type="button"
              onClick={() => selectPrivateTab(key)}
              className={`flex min-h-14 items-center justify-center gap-2 rounded-2xl text-sm font-extrabold transition ${
                tab === key
                  ? "bg-[#344b32] text-white shadow-md"
                  : "text-[#687264] hover:bg-[#eef2eb]"
              }`}
            >
              <span className="material-symbols-outlined text-xl">{icon}</span>
              {label}
            </button>
          ))}
        </nav>

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
          <section className="rounded-[30px] border border-[#e0e6dc] bg-white/45 p-5 md:p-8">
            <div className="mb-5 flex flex-wrap items-end justify-between gap-4">
              <div>
                <p className="text-xs font-black uppercase tracking-[0.18em] text-[#8b947f]">
                  Open listings
                </p>
                <h2 className="mt-1 text-2xl font-black">판매 중인 카드</h2>
              </div>
              <div className="flex flex-wrap gap-2">
                {([undefined, "HYPER_RARE", "GOLDEN_RARE"] as const).map(
                  (type) => (
                    <button
                      key={type ?? "ALL"}
                      type="button"
                      onClick={() => changeMarketQuery(type, 0)}
                      className={`rounded-full px-4 py-2 text-xs font-black ${assetType === type ? "bg-[#344b32] text-white" : "bg-white text-[#667260]"}`}
                    >
                      {type === undefined
                        ? "전체"
                        : type === "HYPER_RARE"
                          ? "하이퍼"
                          : "골든"}
                    </button>
                  ),
                )}
              </div>
            </div>
            <form
              className="mb-6 grid gap-3 rounded-2xl border border-[#dce4d7] bg-white p-3 sm:grid-cols-[1fr_180px_auto]"
              onSubmit={(event) => {
                event.preventDefault();
                changeMarketQuery(assetType, 0, sort, keywordInput);
              }}
            >
              <label className="flex items-center gap-2 rounded-xl bg-[#f4f6f1] px-4">
                <span className="material-symbols-outlined text-xl text-[#76816f]">
                  search
                </span>
                <input
                  value={keywordInput}
                  maxLength={50}
                  onChange={(event) => setKeywordInput(event.target.value)}
                  placeholder="카드 이름 검색"
                  className="min-w-0 flex-1 bg-transparent py-3 text-sm font-bold outline-none"
                />
              </label>
              <select
                value={sort}
                onChange={(event) =>
                  changeMarketQuery(
                    assetType,
                    0,
                    event.target.value as MarketSort,
                    keyword,
                  )
                }
                className="rounded-xl border border-[#d8e0d3] bg-white px-4 py-3 text-sm font-black outline-none"
              >
                <option value="createdAt,desc">최신 등록순</option>
                <option value="askingPrice,asc">가격 낮은순</option>
                <option value="askingPrice,desc">가격 높은순</option>
              </select>
              <button
                type="submit"
                className="rounded-xl bg-[#344b32] px-6 py-3 text-sm font-black text-white"
              >
                검색
              </button>
            </form>
            {listings.length ? (
              <>
                <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
                  {listings.map((listing) => (
                    <ListingCard
                      key={listing.id}
                      listing={listing}
                      mine={listing.sellerUserId === userId}
                      onBuy={() => {
                        if (!token)
                          return setNotice("로그인 후 구매할 수 있어요.");
                        setSelectedListing(listing);
                        setOfferPrice("");
                      }}
                    />
                  ))}
                </div>
                {marketTotalPages > 1 ? (
                  <div className="mt-8 flex items-center justify-center gap-3 border-t border-[#dfe5da] pt-6">
                    <button
                      type="button"
                      disabled={marketPage <= 0}
                      onClick={() =>
                        changeMarketQuery(assetType, marketPage - 1)
                      }
                      className="rounded-xl border border-[#cad5c5] bg-white px-5 py-2.5 text-sm font-black text-[#53644e] disabled:opacity-35"
                    >
                      이전
                    </button>
                    <span className="min-w-20 text-center text-sm font-black text-[#667260]">
                      {marketPage + 1} / {marketTotalPages}
                    </span>
                    <button
                      type="button"
                      disabled={marketPage + 1 >= marketTotalPages}
                      onClick={() =>
                        changeMarketQuery(assetType, marketPage + 1)
                      }
                      className="rounded-xl border border-[#cad5c5] bg-white px-5 py-2.5 text-sm font-black text-[#53644e] disabled:opacity-35"
                    >
                      다음
                    </button>
                  </div>
                ) : null}
              </>
            ) : (
              <Empty text="현재 판매 중인 카드가 없어요." />
            )}
          </section>
        ) : null}

        {!privateLoading && tab === "sell" && token ? (
          <section className="grid gap-12 rounded-[30px] border border-[#e0e6dc] bg-white/45 p-5 md:p-8 lg:grid-cols-[1.1fr_0.9fr]">
            <div>
              <SectionTitle eyebrow="Sell a card" title="판매할 카드 선택" />
              <p className="mb-5 text-sm leading-6 text-[#737d6e]">
                하이퍼는 동일 카드를 한 장 남기고 판매할 수 있으며, 골든은
                개체를 직접 선택합니다.
              </p>
              <div className="space-y-4">
                {sellable
                  .filter((card) => card.sellableCount > 0)
                  .map((card) => (
                    <SellableCardRow
                      key={card.cardId}
                      card={card}
                      price={sellPrices[card.cardId] ?? ""}
                      goldenId={selectedGolden[card.cardId]}
                      busy={actionLoading}
                      onPrice={(value) =>
                        setSellPrices((current) => ({
                          ...current,
                          [card.cardId]: value,
                        }))
                      }
                      onGolden={(value) =>
                        setSelectedGolden((current) => ({
                          ...current,
                          [card.cardId]: value,
                        }))
                      }
                      onSubmit={() => {
                        const priceValue = Number(sellPrices[card.cardId]);
                        const sellerReceived = Math.floor(priceValue * 0.8);
                        const assetGuide =
                          card.rarity === "GOLDEN_RARE"
                            ? `선택한 개체 #${selectedGolden[card.cardId]}가 판매 등록됩니다.`
                            : "판매 중에는 해당 카드 1장이 보유 수량에서 분리되며, 판매 취소나 기간 만료 시 돌아옵니다.";
                        const submitListing = () =>
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
                          body: `판매 카드: ${card.cardName}. 등록 가격은 ${point(priceValue)}이며, 거래 완료 시 수수료 20%를 제외한 ${point(sellerReceived)}를 받습니다. ${assetGuide}`,
                          ok: "판매 등록",
                          onOk: () => {
                            if (card.rarity !== "GOLDEN_RARE") {
                              submitListing();
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
                                  onOk: submitListing,
                                }),
                              0,
                            );
                          },
                        });
                      }}
                    />
                  ))}
                {!sellable.some((card) => card.sellableCount > 0) ? (
                  <Empty text="현재 판매할 수 있는 카드가 없어요." />
                ) : null}
              </div>
            </div>
            <div>
              <SectionTitle eyebrow="My listings" title="판매 중인 카드" />
              <div className="space-y-3">
                {activeListings.map((listing) => (
                  <div
                    key={listing.id}
                    className="flex items-center gap-4 rounded-2xl border border-[#dce3d7] bg-white p-4"
                  >
                    <div className="h-24 w-16 shrink-0 overflow-hidden rounded-lg bg-[#eef1eb]">
                      <CardImage
                        src={listing.imageUrl}
                        name={listing.cardName}
                      />
                    </div>
                    <div className="min-w-0 flex-1">
                      <p className="truncate font-black">{listing.cardName}</p>
                      <p className="mt-1 text-sm font-bold text-[#8b6b18]">
                        {point(listing.askingPrice)}
                      </p>
                      <p className="mt-1 text-xs text-[#7a8476]">
                        받은 제안 {listing.activeOfferCount}개
                      </p>
                    </div>
                    <button
                      type="button"
                      disabled={actionLoading}
                      onClick={() =>
                        void runAction(
                          () => cancelMarketListing(listing.id, token),
                          "판매를 취소했어요.",
                        )
                      }
                      className="rounded-xl border border-[#d8bcb2] px-3 py-2 text-xs font-black text-[#9c4d3c]"
                    >
                      취소
                    </button>
                  </div>
                ))}
                {!activeListings.length ? (
                  <Empty text="판매 중인 카드가 없어요." />
                ) : null}
              </div>
              <PrivatePager
                page={privatePage}
                totalPages={privateTotalPages}
                onChange={changePrivatePage}
              />
            </div>
          </section>
        ) : null}

        {!privateLoading && (tab === "sent" || tab === "received") && token ? (
          <section className="rounded-[30px] border border-[#e0e6dc] bg-white/45 p-5 md:p-8">
            <SectionTitle
              eyebrow={tab === "sent" ? "Sent offers" : "Received offers"}
              title={
                tab === "sent" ? "내가 보낸 가격 제안" : "내가 받은 가격 제안"
              }
            />
            <div className="grid gap-5 lg:grid-cols-2">
              {(tab === "sent" ? sent : received).map((negotiation) => (
                <NegotiationCard
                  key={negotiation.id}
                  negotiation={negotiation}
                  userId={userId!}
                  price={counterPrices[negotiation.id] ?? ""}
                  busy={actionLoading}
                  onPrice={(value) =>
                    setCounterPrices((current) => ({
                      ...current,
                      [negotiation.id]: value,
                    }))
                  }
                  onAccept={() =>
                    askConfirm({
                      icon: "handshake",
                      title: "이 가격으로 거래를 완료할까요?",
                      body: `${negotiation.cardName}을 ${point(negotiation.currentPrice)}에 거래합니다. 거래가 완료되면 카드와 포인트 이동을 취소할 수 없습니다.`,
                      ok: "제안 수락",
                      onOk: () =>
                        void runAction(
                          () => acceptMarketNegotiation(negotiation.id, token),
                          "가격 제안을 수락해 거래를 완료했어요.",
                        ),
                    })
                  }
                  onReject={() =>
                    void runAction(
                      () => rejectMarketNegotiation(negotiation.id, token),
                      "가격 제안을 거절했어요.",
                    )
                  }
                  onCancel={() =>
                    void runAction(
                      () => cancelMarketNegotiation(negotiation.id, token),
                      "가격 제안을 취소했어요.",
                    )
                  }
                  onPropose={() =>
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
                />
              ))}
            </div>
            {!(tab === "sent" ? sent : received).length ? (
              <Empty text="가격 제안 내역이 없어요." />
            ) : null}
            <PrivatePager
              page={privatePage}
              totalPages={privateTotalPages}
              onChange={changePrivatePage}
            />
          </section>
        ) : null}

        {!privateLoading && tab === "trades" && token ? (
          <section className="rounded-[30px] border border-[#e0e6dc] bg-white/45 p-5 md:p-8">
            <SectionTitle eyebrow="Trade history" title="완료된 거래" />
            <div className="space-y-3">
              {trades.map((trade) => (
                <div
                  key={trade.id}
                  className="grid items-center gap-4 rounded-2xl border border-[#dce3d7] bg-white p-4 md:grid-cols-[72px_1fr_auto_auto]"
                >
                  <div className="h-24 w-16 overflow-hidden rounded-lg bg-[#edf0e9]">
                    <CardImage src={trade.imageUrl} name={trade.cardName} />
                  </div>
                  <div>
                    <p className="font-black">{trade.cardName}</p>
                    <p className="mt-1 text-xs text-[#788273]">
                      {trade.tradeType === "BUY_NOW"
                        ? "바로 구매"
                        : "가격 협상"}{" "}
                      · {dateTime(trade.completedAt)}
                    </p>
                  </div>
                  <div className="text-left md:text-right">
                    <p className="text-xs text-[#788273]">거래 금액</p>
                    <p className="font-black text-[#765b12]">
                      {point(trade.tradePrice)}
                    </p>
                  </div>
                  <span
                    className={`w-fit rounded-full px-3 py-1 text-xs font-black ${trade.buyerUserId === userId ? "bg-[#e7f0e2] text-[#43623c]" : "bg-[#f7ebc9] text-[#806419]"}`}
                  >
                    {trade.buyerUserId === userId ? "구매" : "판매"}
                  </span>
                </div>
              ))}
              {!trades.length ? <Empty text="완료된 거래가 없어요." /> : null}
            </div>
            <PrivatePager
              page={privatePage}
              totalPages={privateTotalPages}
              onChange={changePrivatePage}
            />
          </section>
        ) : null}
      </div>

      {selectedListing && token ? (
        <div
          className="fixed inset-0 z-50 flex items-end justify-center bg-black/55 p-4 backdrop-blur-sm md:items-center"
          onMouseDown={() => setSelectedListing(null)}
        >
          <div
            className="w-full max-w-lg rounded-[28px] bg-white p-6 shadow-2xl"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <div className="flex gap-5">
              <div className="h-40 w-28 shrink-0 overflow-hidden rounded-xl bg-[#eef1eb]">
                <CardImage
                  src={selectedListing.imageUrl}
                  name={selectedListing.cardName}
                />
              </div>
              <div className="flex-1">
                <p className="text-xs font-black text-[#9a7a25]">
                  {selectedListing.assetType === "GOLDEN_RARE"
                    ? "GOLDEN RARE"
                    : "HYPER RARE"}
                </p>
                <h2 className="mt-1 text-2xl font-black">
                  {selectedListing.cardName}
                </h2>
                <p className="mt-3 text-sm text-[#788273]">
                  판매자 {selectedListing.sellerNickname}
                </p>
                <p className="mt-2 text-2xl font-black text-[#6e5618]">
                  {point(selectedListing.askingPrice)}
                </p>
              </div>
            </div>
            <div className="mt-6 rounded-2xl bg-[#f5f1df] p-4 text-sm font-bold leading-6 text-[#6f5b25]">
              구매와 가격 제안에는 거래 가능 포인트가 사용되며, 완료된 거래는
              취소할 수 없습니다.
            </div>
            <label className="mt-5 block text-sm font-black">
              더 저렴한 가격 제안
            </label>
            <div className="mt-2 flex gap-2">
              <input
                type="number"
                min={100}
                max={selectedListing.askingPrice - 1}
                value={offerPrice}
                onChange={(event) => setOfferPrice(event.target.value)}
                placeholder="최소 100P"
                className="min-w-0 flex-1 rounded-xl border border-[#ced7c8] px-4 py-3 font-bold outline-none focus:border-[#5d7955]"
              />
              <button
                type="button"
                disabled={
                  actionLoading ||
                  Number(offerPrice) < 100 ||
                  Number(offerPrice) >= selectedListing.askingPrice
                }
                onClick={() =>
                  void runAction(
                    () =>
                      createMarketNegotiation(
                        selectedListing.id,
                        Number(offerPrice),
                        "PRICE_ADJUST_REQUEST",
                        token,
                      ),
                    "판매자에게 가격을 제안했어요.",
                  )
                }
                className="rounded-xl bg-[#e7eee2] px-4 font-black text-[#405d3a] disabled:opacity-40"
              >
                제안
              </button>
            </div>
            <button
              type="button"
              disabled={actionLoading}
              onClick={() =>
                askConfirm({
                  icon: "shopping_bag",
                  title: "이 카드를 바로 구매할까요?",
                  body: `${selectedListing.cardName}을 ${point(selectedListing.askingPrice)}에 구매합니다. 거래 가능 포인트가 사용되며 완료 후 취소하거나 환불할 수 없습니다.`,
                  ok: "구매 확정",
                  onOk: () =>
                    void runAction(
                      () => buyMarketListing(selectedListing.id, token),
                      "카드 구매를 완료했어요.",
                    ),
                })
              }
              className="mt-4 w-full rounded-2xl bg-[#344b32] py-4 font-black text-white shadow-lg disabled:opacity-40"
            >
              {point(selectedListing.askingPrice)} 바로 구매
            </button>
          </div>
        </div>
      ) : null}
    </main>
  );
}

function WalletCard({
  icon,
  label,
  value,
  tone,
}: {
  icon: string;
  label: string;
  value: string;
  tone: "gold" | "green";
}) {
  const style =
    tone === "gold"
      ? "border-[#e8d394] bg-[#fffaf0] text-[#806419]"
      : "border-[#cdddc5] bg-[#f5faf2] text-[#476341]";
  return (
    <div className={`rounded-[24px] border p-6 shadow-sm ${style}`}>
      <div className="flex items-center gap-2 text-xs font-black">
        <span className="material-symbols-outlined text-xl">{icon}</span>
        {label}
      </div>
      <p className="mt-3 text-2xl font-black">{value}</p>
    </div>
  );
}

function SectionTitle({ eyebrow, title }: { eyebrow: string; title: string }) {
  return (
    <div className="mb-5">
      <p className="text-xs font-black uppercase tracking-[0.18em] text-[#8b947f]">
        {eyebrow}
      </p>
      <h2 className="mt-1 text-2xl font-black">{title}</h2>
    </div>
  );
}

function Empty({ text }: { text: string }) {
  return (
    <div className="rounded-3xl border border-dashed border-[#cbd4c5] bg-white/60 p-12 text-center font-bold text-[#7a8476]">
      {text}
    </div>
  );
}

function PrivatePager({
  page,
  totalPages,
  onChange,
}: {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
}) {
  if (totalPages <= 1) return null;
  return (
    <div className="mt-6 flex items-center justify-center gap-3 border-t border-[#dfe5da] pt-5">
      <button
        type="button"
        disabled={page <= 0}
        onClick={() => onChange(page - 1)}
        className="rounded-xl border border-[#cad5c5] bg-white px-4 py-2 text-sm font-black disabled:opacity-35"
      >
        이전
      </button>
      <span className="text-sm font-black text-[#667260]">
        {page + 1} / {totalPages}
      </span>
      <button
        type="button"
        disabled={page + 1 >= totalPages}
        onClick={() => onChange(page + 1)}
        className="rounded-xl border border-[#cad5c5] bg-white px-4 py-2 text-sm font-black disabled:opacity-35"
      >
        다음
      </button>
    </div>
  );
}

function ListingCard({
  listing,
  mine,
  onBuy,
}: {
  listing: MarketListing;
  mine: boolean;
  onBuy: () => void;
}) {
  return (
    <article className="group overflow-hidden rounded-[24px] border border-[#d9e0d4] bg-white shadow-sm transition hover:-translate-y-1 hover:shadow-xl">
      <div className="relative aspect-[3/4] bg-[#edf0e9] p-3">
        <CardImage src={listing.imageUrl} name={listing.cardName} />
        <span
          className={`absolute left-4 top-4 rounded-full px-3 py-1 text-[11px] font-black ${listing.assetType === "GOLDEN_RARE" ? "bg-[#f5d96a] text-[#634d0d]" : "bg-[#7050a0] text-white"}`}
        >
          {listing.assetType === "GOLDEN_RARE" ? "GOLDEN" : "HYPER"}
        </span>
      </div>
      <div className="p-5">
        <h3 className="truncate text-lg font-black">{listing.cardName}</h3>
        <p className="mt-1 text-xs text-[#7a8476]">
          {listing.sellerNickname} · 제안 {listing.activeOfferCount}개
        </p>
        <div className="mt-4 flex items-end justify-between">
          <div>
            <p className="text-[11px] font-bold text-[#8a9384]">즉시 구매</p>
            <p className="text-xl font-black text-[#765b12]">
              {point(listing.askingPrice)}
            </p>
          </div>
          <button
            type="button"
            disabled={mine}
            onClick={onBuy}
            className="rounded-xl bg-[#344b32] px-4 py-2 text-xs font-black text-white disabled:bg-[#dfe4db] disabled:text-[#929a8f]"
          >
            {mine ? "내 판매" : "거래하기"}
          </button>
        </div>
        <p className="mt-4 text-[11px] text-[#929a8f]">
          {dateTime(listing.expiresAt)}까지
        </p>
        <Link
          href={`/card-market/listings/${listing.id}`}
          className="mt-3 block text-center text-xs font-black text-[#52694d] underline-offset-4 hover:underline"
        >
          매물 상세 보기
        </Link>
      </div>
    </article>
  );
}

function SellableCardRow({
  card,
  price,
  goldenId,
  busy,
  onPrice,
  onGolden,
  onSubmit,
}: {
  card: MarketSellableCard;
  price: string;
  goldenId?: number;
  busy: boolean;
  onPrice: (value: string) => void;
  onGolden: (value: number) => void;
  onSubmit: () => void;
}) {
  const validGolden = card.rarity !== "GOLDEN_RARE" || Boolean(goldenId);
  return (
    <div className="rounded-2xl border border-[#dce3d7] bg-white p-4">
      <div className="flex gap-4">
        <div className="h-28 w-20 shrink-0 overflow-hidden rounded-lg bg-[#eef1eb]">
          <CardImage src={card.imageUrl} name={card.cardName} />
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex items-center justify-between gap-3">
            <div>
              <h3 className="font-black">{card.cardName}</h3>
              <p className="mt-1 text-xs text-[#788273]">
                보유 {card.ownedCount}장 · 판매 가능 {card.sellableCount}장
              </p>
            </div>
            <span className="rounded-full bg-[#eee8f8] px-3 py-1 text-[10px] font-black text-[#684995]">
              {card.rarity === "GOLDEN_RARE" ? "GOLDEN" : "HYPER"}
            </span>
          </div>
          {card.rarity === "GOLDEN_RARE" ? (
            <select
              value={goldenId ?? ""}
              onChange={(event) => onGolden(Number(event.target.value))}
              className="mt-3 w-full rounded-xl border border-[#d3dacd] px-3 py-2 text-sm font-bold"
            >
              <option value="">판매할 골든 개체 선택</option>
              {card.goldenInstances
                .filter((item) => !item.listed)
                .map((item) => (
                  <option key={item.id} value={item.id}>
                    {card.cardName} · 개체 #{item.id}
                  </option>
                ))}
            </select>
          ) : null}
          <div className="mt-3 flex gap-2">
            <input
              type="number"
              min={100}
              max={99999999}
              value={price}
              onChange={(event) => onPrice(event.target.value)}
              placeholder="판매 가격 100P 이상"
              className="min-w-0 flex-1 rounded-xl border border-[#d3dacd] px-3 py-2 text-sm font-bold"
            />
            <button
              type="button"
              disabled={
                busy ||
                Number(price) < 100 ||
                Number(price) > 99999999 ||
                !validGolden
              }
              onClick={onSubmit}
              className="rounded-xl bg-[#344b32] px-4 text-xs font-black text-white disabled:opacity-35"
            >
              등록
            </button>
          </div>
          <p className="mt-2 text-[11px] text-[#8b947f]">
            판매 가능 가격 100P ~ 99,999,999P · 판매 완료 시 수수료 20%가
            차감됩니다.
          </p>
        </div>
      </div>
    </div>
  );
}

function NegotiationCard({
  negotiation,
  userId,
  price,
  busy,
  onPrice,
  onAccept,
  onReject,
  onCancel,
  onPropose,
}: {
  negotiation: MarketNegotiation;
  userId: number;
  price: string;
  busy: boolean;
  onPrice: (value: string) => void;
  onAccept: () => void;
  onReject: () => void;
  onCancel: () => void;
  onPropose: () => void;
}) {
  const role = negotiation.buyerUserId === userId ? "BUYER" : "SELLER";
  const myTurn =
    negotiation.status === "NEGOTIATING" && negotiation.turn === role;
  return (
    <article className="rounded-[24px] border border-[#dce3d7] bg-white p-5 shadow-sm">
      <div className="flex gap-4">
        <div className="h-28 w-20 shrink-0 overflow-hidden rounded-lg bg-[#edf0e9]">
          <CardImage src={negotiation.imageUrl} name={negotiation.cardName} />
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex items-center justify-between gap-2">
            <h3 className="truncate font-black">{negotiation.cardName}</h3>
            <span className="rounded-full bg-[#eef2eb] px-3 py-1 text-[10px] font-black text-[#667260]">
              {STATUS_LABEL[negotiation.status] ?? negotiation.status}
            </span>
          </div>
          <p className="mt-2 text-sm text-[#747e70]">
            판매가 <strong>{point(negotiation.askingPrice)}</strong>
          </p>
          <p className="mt-1 text-xl font-black text-[#765b12]">
            현재 제안 {point(negotiation.currentPrice)}
          </p>
          <p className="mt-1 text-xs text-[#879083]">
            제안 금액 {point(negotiation.escrowedPaidPoint)} 보관 중 ·{" "}
            {dateTime(negotiation.expiresAt)}까지
          </p>
        </div>
      </div>
      <div className="mt-4 flex flex-wrap gap-2">
        {negotiation.proposals.map((proposal) => (
          <span
            key={proposal.id}
            className={`rounded-full px-3 py-1.5 text-xs font-bold ${proposal.proposerType === "BUYER" ? "bg-[#e9f1e5] text-[#496541]" : "bg-[#f7ebc9] text-[#785e18]"}`}
          >
            {proposal.proposerType === "BUYER" ? "구매자" : "판매자"}{" "}
            {point(proposal.proposedPrice)}
          </span>
        ))}
      </div>
      {myTurn ? (
        <div className="mt-5 border-t border-[#edf0e9] pt-4">
          <div className="flex gap-2">
            <input
              type="number"
              min={100}
              max={99999999}
              value={price}
              onChange={(event) => onPrice(event.target.value)}
              placeholder="새 가격"
              className="min-w-0 flex-1 rounded-xl border border-[#d3dacd] px-3 py-2 text-sm font-bold"
            />
            <button
              type="button"
              disabled={busy || Number(price) < 100}
              onClick={onPropose}
              className="rounded-xl bg-[#e7eee2] px-4 text-xs font-black text-[#405d3a] disabled:opacity-35"
            >
              역제안
            </button>
          </div>
          <div className="mt-2 grid grid-cols-2 gap-2">
            <button
              type="button"
              disabled={busy}
              onClick={onAccept}
              className="rounded-xl bg-[#344b32] py-3 text-xs font-black text-white"
            >
              현재 가격 수락
            </button>
            <button
              type="button"
              disabled={busy}
              onClick={onReject}
              className="rounded-xl border border-[#d8bcb2] py-3 text-xs font-black text-[#9c4d3c]"
            >
              거절
            </button>
          </div>
        </div>
      ) : null}
      {role === "BUYER" && negotiation.status === "NEGOTIATING" ? (
        <button
          type="button"
          disabled={busy}
          onClick={onCancel}
          className="mt-3 text-xs font-bold text-[#899184] underline"
        >
          내 제안 취소
        </button>
      ) : null}
    </article>
  );
}
