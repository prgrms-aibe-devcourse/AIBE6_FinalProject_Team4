"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
  commerceErrorMessage,
  isAbortError,
} from "@/features/commerce/presentation";
import {
  firstSearchParam,
  parseOneBasedPage,
} from "@/features/commerce/list-query";
import {
  DisplayCard,
  GachaCardModal,
  lockedCard,
  RARITY_LABEL,
  RARITY_ORDER,
} from "@/features/gacha/GachaCardPresentation";
import GachaCatalogSection from "@/features/gacha/GachaCatalogSection";
import GachaHistorySection, {
  GachaRatesSection,
} from "@/features/gacha/GachaHistorySection";
import GachaMineSection from "@/features/gacha/GachaMineSection";
import GachaWorkshop from "@/features/gacha/GachaWorkshop";
import { useGachaCosmetics } from "@/features/gacha/use-gacha-cosmetics";
import {
  GACHA_COLLECTION_CHANGED_EVENT,
  GachaCard,
  GachaCollectionCard,
  GachaDrawPage,
  GachaRateData,
  GachaRarity,
  getGachaCatalog,
  getGachaDraws,
  getGachaRates,
  getMyGachaCollection,
} from "@/lib/gacha-api";
import { useStore } from "@/lib/store";
import { useSpotlightTour } from "@/lib/onboarding/useSpotlightTour";
import SpotlightTour, { TourStep } from "@/components/onboarding/SpotlightTour";

const GACHA_TOUR_STEPS: TourStep[] = [
  {
    targetId: "gacha-catalog-tab",
    title: "전체 도감",
    description:
      "카드팩은 상점에서 포인트로 구매하고, 여기서는 모은 카드를 도감으로 확인할 수 있어요.",
  },
  {
    targetId: "gacha-mine-tab",
    title: "내 카드 갤러리",
    description: "지금까지 모은 카드만 모아서 볼 수 있어요.",
  },
  {
    targetId: "gacha-history-tab",
    title: "개봉 내역",
    description: "그동안 열어본 카드팩 기록을 확인할 수 있어요.",
  },
];

type Tab = "catalog" | "mine" | "workshop" | "history";
type GachaSearchParams = {
  tab?: string | string[];
  section?: string | string[];
  page?: string | string[];
};

function initialTab(searchParams?: GachaSearchParams): Tab {
  const value = firstSearchParam(searchParams?.tab);
  return value === "mine" || value === "workshop" || value === "history"
    ? value
    : "catalog";
}

const EMPTY_HISTORY: GachaDrawPage = {
  content: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
};

export default function GachaPage({
  searchParams,
}: {
  searchParams?: GachaSearchParams;
}) {
  const { state, hydrated } = useStore();
  const tour = useSpotlightTour("gacha", GACHA_TOUR_STEPS.length);
  const router = useRouter();
  const urlTab = initialTab(searchParams);
  const urlHistoryPage = parseOneBasedPage(searchParams?.page);
  const [tab, setTab] = useState<Tab>(urlTab);
  const initialWorkshopSection =
    firstSearchParam(searchParams?.section) === "cosmetics"
      ? "cosmetics"
      : "menu";
  const [catalog, setCatalog] = useState<GachaCard[]>([]);
  const [collection, setCollection] = useState<GachaCollectionCard[]>([]);
  const [history, setHistory] = useState<GachaDrawPage | null>(null);
  const [historyPage, setHistoryPage] = useState(urlHistoryPage);
  const [rates, setRates] = useState<GachaRateData | null>(null);
  const [expandedRarities, setExpandedRarities] = useState<Set<GachaRarity>>(
    () => new Set(),
  );
  const [mineRarity, setMineRarity] = useState<GachaRarity | "ALL">("ALL");
  const [selectedCard, setSelectedCard] = useState<DisplayCard | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const {
    data: cosmeticData,
    title: equippedTitle,
    border: equippedBorder,
  } = useGachaCosmetics();

  const refreshCollection = useCallback(async () => {
    if (!state.accessToken) return;
    setCollection(await getMyGachaCollection(state.accessToken));
  }, [state.accessToken]);

  const refreshOwnedState = useCallback(async () => {
    if (!state.accessToken) return;
    const [nextCollection, nextHistory] = await Promise.all([
      getMyGachaCollection(state.accessToken),
      getGachaDraws(state.accessToken, undefined, historyPage),
    ]);
    setCollection(nextCollection);
    setHistory(nextHistory);
  }, [historyPage, state.accessToken]);

  useEffect(() => {
    setTab(urlTab);
    setHistoryPage(urlHistoryPage);
  }, [urlHistoryPage, urlTab]);

  useEffect(() => {
    if (!hydrated) return;
    const controller = new AbortController();
    let active = true;
    setLoading(true);
    setError("");

    const privateRequests = state.accessToken
      ? [
          getMyGachaCollection(state.accessToken, controller.signal),
          getGachaDraws(
            state.accessToken,
            undefined,
            historyPage,
            controller.signal,
          ),
        ]
      : [Promise.resolve([]), Promise.resolve(EMPTY_HISTORY)];

    Promise.all([
      getGachaCatalog(undefined, controller.signal),
      getGachaRates(controller.signal),
      ...privateRequests,
    ])
      .then(([catalogData, rateData, collectionData, historyData]) => {
        if (!active) return;
        setCatalog(catalogData as GachaCard[]);
        setRates(rateData as GachaRateData);
        setCollection(collectionData as GachaCollectionCard[]);
        setHistory(historyData as GachaDrawPage);
      })
      .catch((cause: unknown) => {
        if (!active || isAbortError(cause)) return;
        setError(
          commerceErrorMessage(cause, "가챠 정보를 불러오지 못했습니다."),
        );
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
      controller.abort();
    };
  }, [historyPage, hydrated, state.accessToken]);

  useEffect(() => {
    if (!hydrated || !state.accessToken) return;
    const refreshWhenVisible = () => {
      if (document.visibilityState === "visible") {
        void refreshOwnedState().catch(() => undefined);
      }
    };
    window.addEventListener("pageshow", refreshWhenVisible);
    window.addEventListener("focus", refreshWhenVisible);
    document.addEventListener("visibilitychange", refreshWhenVisible);
    return () => {
      window.removeEventListener("pageshow", refreshWhenVisible);
      window.removeEventListener("focus", refreshWhenVisible);
      document.removeEventListener("visibilitychange", refreshWhenVisible);
    };
  }, [hydrated, refreshOwnedState, state.accessToken]);

  useEffect(() => {
    if (!state.accessToken) return;
    const refreshAfterTestGrant = () => {
      void refreshCollection().catch(() => undefined);
    };
    window.addEventListener(
      GACHA_COLLECTION_CHANGED_EVENT,
      refreshAfterTestGrant,
    );
    return () =>
      window.removeEventListener(
        GACHA_COLLECTION_CHANGED_EVENT,
        refreshAfterTestGrant,
      );
  }, [refreshCollection, state.accessToken]);

  const hasProcessingDraw =
    history?.content.some((draw) =>
      ["PENDING", "PROCESSING", "RETRYABLE_FAILED"].includes(draw.status),
    ) ?? false;

  useEffect(() => {
    if (!hasProcessingDraw) return;
    const timer = window.setInterval(
      () => void refreshOwnedState().catch(() => undefined),
      1500,
    );
    return () => window.clearInterval(timer);
  }, [hasProcessingDraw, refreshOwnedState]);

  useEffect(() => {
    if (!selectedCard) return;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") setSelectedCard(null);
    };
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [selectedCard]);

  const collectionById = useMemo(
    () => new Map(collection.map((card) => [card.id, card])),
    [collection],
  );
  const catalogCards = useMemo(
    () =>
      catalog.map((card) => {
        const collected = collectionById.get(card.id);
        return collected ? { ...card, ...collected } : lockedCard(card);
      }),
    [catalog, collectionById],
  );
  const ownedCards = useMemo(
    () =>
      collection.filter(
        (card) =>
          card.ownedCount > 0 &&
          (mineRarity === "ALL" || card.rarity === mineRarity),
      ),
    [collection, mineRarity],
  );
  const ownedUniqueCount = collection.filter(
    (card) => card.ownedCount > 0,
  ).length;
  const ownedTotalCount = collection.reduce(
    (sum, card) => sum + Math.max(card.ownedCount, 0),
    0,
  );
  const dismantleableTotal = collection.reduce(
    (sum, card) => sum + Math.max(card.dismantleableCount, 0),
    0,
  );
  const highestOwnedRarity =
    [...RARITY_ORDER]
      .reverse()
      .find((rarity) =>
        collection.some(
          (card) => card.ownedCount > 0 && card.rarity === rarity,
        ),
      ) ?? null;
  const unviewed =
    history?.content.filter(
      (draw) => draw.status === "COMPLETED" && !draw.resultViewedAt,
    ) ?? [];

  const selectTab = (next: Tab) => {
    if (next !== "catalog" && !state.accessToken) return;
    setTab(next);
    setMineRarity("ALL");
    const params = new URLSearchParams();
    if (next !== "catalog") params.set("tab", next);
    if (next === "history" && historyPage > 0) {
      params.set("page", String(historyPage + 1));
    }
    router.replace(params.size ? `/gacha?${params}` : "/gacha", {
      scroll: false,
    });
  };

  const changeHistoryPage = (nextPage: number) => {
    setHistoryPage(nextPage);
    router.replace(`/gacha?tab=history&page=${nextPage + 1}`, {
      scroll: false,
    });
  };

  const toggleRarity = (rarity: GachaRarity) => {
    setExpandedRarities((current) => {
      const next = new Set(current);
      if (next.has(rarity)) next.delete(rarity);
      else next.add(rarity);
      return next;
    });
  };

  return (
    <div className="container pb-20">
      <section className="relative mb-7 overflow-hidden rounded-[28px] bg-gradient-to-br from-[#253822] via-[#45643b] to-[#a28a31] px-6 py-8 text-white shadow-card md:px-10">
        <div className="relative z-10 max-w-[620px]">
          <p className="mb-2 text-sm font-bold text-[#dce9c8]">
            SEASON 01 · 봄의 수호자
          </p>
          <div className="flex items-center gap-2">
            <h1 className="text-[30px] font-black tracking-[-0.04em] md:text-[38px]">
              오늘의 기록이 카드가 돼요
            </h1>
            <button
              type="button"
              title="온보딩 투어 다시 보기"
              aria-label="온보딩 투어 다시 보기"
              onClick={tour.start}
              className="flex h-5 w-5 flex-none cursor-pointer items-center justify-center rounded-full bg-white text-[11px] font-bold text-[#45643b] hover:bg-[#f2cf59]"
            >
              ?
            </button>
          </div>
          <p className="mt-3 max-w-[560px] text-sm leading-6 text-white/80">
            획득한 카드만 원본 일러스트가 해금됩니다. 도감을 채우고 나만의 카드
            갤러리를 완성해 보세요.
          </p>
        </div>
        <div className="absolute -bottom-16 -right-8 h-52 w-52 rounded-full bg-[#f2cf59]/25 blur-2xl" />
      </section>

      {unviewed.length > 0 ? (
        <Link
          href={`/gacha/open/${unviewed[0].drawId}`}
          className="mb-6 flex items-center justify-between rounded-2xl border border-[#e7cf72] bg-[#fff9df] px-5 py-4 text-[#6d5200] shadow-sm"
        >
          <span className="font-extrabold">
            아직 확인하지 않은 카드팩이 있어요.
          </span>
          <span className="text-sm font-bold">지금 열기 →</span>
        </Link>
      ) : null}

      <nav
        aria-label="가챠 메뉴"
        className="mb-7 grid grid-cols-3 gap-2 rounded-2xl bg-[#e9ede4] p-1.5"
      >
        {(
          [
            ["catalog", "전체 도감", "collections_bookmark"],
            ["mine", "내 카드 갤러리", "auto_awesome"],
            ["history", "개봉 내역", "history"],
          ] as const
        ).map(([key, label, icon]) => (
          <button
            key={key}
            type="button"
            data-tour-id={`gacha-${key}-tab`}
            disabled={key !== "catalog" && !state.accessToken}
            onClick={() => selectTab(key)}
            className={`flex min-h-14 items-center justify-center gap-2 rounded-xl px-2 text-xs font-extrabold transition sm:text-sm ${
              tab === key || (tab === "workshop" && key === "mine")
                ? "bg-white text-ink shadow-sm"
                : "text-sub disabled:cursor-not-allowed disabled:opacity-35"
            }`}
          >
            <span
              aria-hidden="true"
              className="material-symbols-outlined text-[19px]"
            >
              {icon}
            </span>
            {label}
          </button>
        ))}
      </nav>
      {tour.open && (
        <SpotlightTour
          steps={GACHA_TOUR_STEPS}
          stepIndex={tour.stepIndex}
          onNext={tour.next}
          onSkip={tour.skip}
        />
      )}

      {loading ? (
        <div className="rounded-2xl bg-white p-12 text-center text-sub">
          도감을 불러오는 중...
        </div>
      ) : null}
      {error ? (
        <div className="rounded-2xl bg-[#fff4ef] p-5 font-semibold text-danger">
          {error}
        </div>
      ) : null}

      {!loading && !error && tab === "catalog" ? (
        <GachaCatalogSection
          cards={catalogCards}
          unlockedCount={collection.filter((card) => card.unlocked).length}
          totalCount={catalog.length}
          expandedRarities={expandedRarities}
          onToggleRarity={toggleRarity}
          onSelectCard={setSelectedCard}
        />
      ) : null}

      {!loading && !error && tab === "mine" ? (
        <GachaMineSection
          cards={ownedCards}
          rarity={mineRarity}
          ownedUniqueCount={ownedUniqueCount}
          ownedTotalCount={ownedTotalCount}
          highestOwnedRarity={highestOwnedRarity}
          dismantleableTotal={dismantleableTotal}
          shardBalance={cosmeticData?.shards.balance ?? 0}
          nickname={state.user?.nickname}
          title={equippedTitle}
          border={equippedBorder}
          onRarity={setMineRarity}
          onSelectCard={setSelectedCard}
          onOpenWorkshop={() => selectTab("workshop")}
        />
      ) : null}

      {!loading && !error && tab === "workshop" && state.accessToken ? (
        <GachaWorkshop
          accessToken={state.accessToken}
          collection={collection}
          onCollectionRefresh={refreshCollection}
          onBack={() => selectTab("mine")}
          initialSection={initialWorkshopSection}
        />
      ) : null}

      {!loading && !error && tab === "history" ? (
        <GachaHistorySection
          history={history}
          page={historyPage}
          onPage={changeHistoryPage}
        />
      ) : null}

      {rates ? <GachaRatesSection rates={rates} /> : null}
      <GachaCardModal
        card={selectedCard}
        onClose={() => setSelectedCard(null)}
      />
    </div>
  );
}
