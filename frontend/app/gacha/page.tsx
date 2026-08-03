"use client";

import Image from "next/image";
import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { ApiError } from "@/lib/api";
import {
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

const RARITY_LABEL: Record<GachaRarity, string> = {
  COMMON: "커먼",
  RARE: "레어",
  SUPER_RARE: "슈퍼 레어",
  HYPER_RARE: "하이퍼 레어",
  GOLDEN_RARE: "골든 레어",
};

const RARITY_STYLE: Record<GachaRarity, string> = {
  COMMON: "bg-[#eef1e9] text-[#65705f]",
  RARE: "bg-[#e9f4ea] text-[#3c7a43]",
  SUPER_RARE: "bg-[#e9effc] text-[#3d5f9b]",
  HYPER_RARE: "bg-[#f0eafd] text-[#6945a6]",
  GOLDEN_RARE: "bg-[#fff3c9] text-[#8b6800]",
};

const RARITY_PANEL: Record<GachaRarity, string> = {
  COMMON: "border-[#dce2d6] bg-[#f5f7f2]",
  RARE: "border-[#cde3cf] bg-[#f1f8f1]",
  SUPER_RARE: "border-[#cbd7ef] bg-[#f2f5fc]",
  HYPER_RARE: "border-[#d8caee] bg-[#f7f3fd]",
  GOLDEN_RARE: "border-[#e7cc69] bg-gradient-to-br from-[#fff9df] to-[#f8e7a5]",
};

const RARITY_ORDER: GachaRarity[] = [
  "COMMON",
  "RARE",
  "SUPER_RARE",
  "HYPER_RARE",
  "GOLDEN_RARE",
];

type Tab = "catalog" | "mine" | "history";

type DisplayCard = GachaCard & {
  ownedCount: number;
  owned: boolean;
  unlocked: boolean;
  goldenGachaAcquired: boolean;
};

const EMPTY_HISTORY: GachaDrawPage = {
  content: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
};

function lockedCard(card: GachaCard): DisplayCard {
  return {
    ...card,
    imageUrl: null,
    ownedCount: 0,
    owned: false,
    unlocked: false,
    goldenGachaAcquired: false,
  };
}

export default function GachaPage() {
  const { state, hydrated } = useStore();
  const [tab, setTab] = useState<Tab>("catalog");
  const [catalog, setCatalog] = useState<GachaCard[]>([]);
  const [collection, setCollection] = useState<GachaCollectionCard[]>([]);
  const [history, setHistory] = useState<GachaDrawPage | null>(null);
  const [rates, setRates] = useState<GachaRateData | null>(null);
  const [expandedRarities, setExpandedRarities] = useState<Set<GachaRarity>>(
    () => new Set(),
  );
  const [mineRarity, setMineRarity] = useState<GachaRarity | "ALL">("ALL");
  const [selectedCard, setSelectedCard] = useState<DisplayCard | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!hydrated) return;
    const controller = new AbortController();
    let active = true;
    setLoading(true);
    setError("");

    const privateRequests = state.accessToken
      ? [
          getMyGachaCollection(state.accessToken, controller.signal),
          getGachaDraws(state.accessToken, undefined, 0, controller.signal),
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
        if (
          !active ||
          (cause instanceof DOMException && cause.name === "AbortError")
        ) {
          return;
        }
        setError(
          cause instanceof ApiError
            ? cause.message
            : "가챠 정보를 불러오지 못했습니다.",
        );
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
      controller.abort();
    };
  }, [hydrated, state.accessToken]);

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
          <h1 className="text-[30px] font-black tracking-[-0.04em] md:text-[38px]">
            오늘의 기록이 카드가 돼요
          </h1>
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
            disabled={key !== "catalog" && !state.accessToken}
            onClick={() => selectTab(key)}
            className={`flex min-h-14 items-center justify-center gap-2 rounded-xl px-2 text-xs font-extrabold transition sm:text-sm ${
              tab === key
                ? "bg-white text-ink shadow-sm"
                : "text-sub disabled:cursor-not-allowed disabled:opacity-35"
            }`}
          >
            <span className="material-symbols-outlined text-[19px]">
              {icon}
            </span>
            {label}
          </button>
        ))}
      </nav>

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
        <section aria-labelledby="catalog-title">
          <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
            <div>
              <p className="text-xs font-bold uppercase tracking-[0.18em] text-brand">
                Collection book
              </p>
              <h2
                id="catalog-title"
                className="mt-1 text-2xl font-black text-ink"
              >
                카드 도감
              </h2>
              <p className="mt-1 text-sm text-sub">
                등급을 눌러 펼쳐보세요. 모든 도감은 처음에는 접혀 있습니다.
              </p>
            </div>
            <div className="rounded-full bg-white px-4 py-2 text-sm font-bold text-sub shadow-sm">
              해금 {collection.filter((card) => card.unlocked).length} /{" "}
              {catalog.length}
            </div>
          </div>

          <div className="space-y-3">
            {RARITY_ORDER.map((rarity) => {
              const rarityCards = catalogCards.filter(
                (card) => card.rarity === rarity,
              );
              const unlockedCount = rarityCards.filter(
                (card) => card.unlocked,
              ).length;
              const expanded = expandedRarities.has(rarity);
              return (
                <section
                  key={rarity}
                  className={`overflow-hidden rounded-2xl border ${RARITY_PANEL[rarity]}`}
                >
                  <button
                    type="button"
                    aria-expanded={expanded}
                    aria-controls={`catalog-${rarity}`}
                    aria-label={`${RARITY_LABEL[rarity]} 도감 ${expanded ? "접기" : "펼치기"}`}
                    onClick={() => toggleRarity(rarity)}
                    className="flex w-full items-center justify-between gap-4 px-5 py-4 text-left"
                  >
                    <span className="flex items-center gap-3">
                      <span
                        className={`rounded-full px-3 py-1.5 text-xs font-black ${RARITY_STYLE[rarity]}`}
                      >
                        {RARITY_LABEL[rarity]}
                      </span>
                      <span className="text-sm font-bold text-sub">
                        {unlockedCount}/{rarityCards.length} 해금
                      </span>
                    </span>
                    <span
                      className={`material-symbols-outlined transition-transform ${expanded ? "rotate-180" : ""}`}
                    >
                      expand_more
                    </span>
                  </button>

                  {expanded ? (
                    <div
                      id={`catalog-${rarity}`}
                      className="grid grid-cols-2 gap-3 border-t border-black/5 p-4 sm:grid-cols-3 lg:grid-cols-5"
                    >
                      {rarityCards.map((card) => (
                        <article
                          key={card.id}
                          className="overflow-hidden rounded-2xl border border-white/80 bg-white/80 shadow-sm"
                        >
                          <button
                            type="button"
                            disabled={!card.unlocked || !card.imageUrl}
                            onClick={() => setSelectedCard(card)}
                            aria-label={
                              card.unlocked
                                ? `${card.name} 원본 일러스트 크게 보기`
                                : `${card.name} 미획득 카드`
                            }
                            className="relative block aspect-[5/7] w-full overflow-hidden bg-[#dfe4da] disabled:cursor-not-allowed"
                          >
                            {card.unlocked && card.imageUrl ? (
                              <>
                                <Image
                                  src={card.imageUrl}
                                  alt={`${card.name} 카드 일러스트`}
                                  fill
                                  sizes="(max-width: 640px) 50vw, (max-width: 1024px) 33vw, 20vw"
                                  className="object-cover transition duration-300 hover:scale-[1.03]"
                                />
                                <span className="absolute bottom-2 right-2 rounded-full bg-black/55 p-1.5 text-white backdrop-blur">
                                  <span className="material-symbols-outlined block text-[18px]">
                                    zoom_in
                                  </span>
                                </span>
                              </>
                            ) : (
                              <span className="absolute inset-0 flex flex-col items-center justify-center bg-[radial-gradient(circle_at_50%_35%,#eef2e9,#cdd4c7)] text-[#8c9686]">
                                <span className="material-symbols-outlined text-[42px]">
                                  lock
                                </span>
                                <span className="mt-2 text-[11px] font-black tracking-[0.15em]">
                                  NOT ACQUIRED
                                </span>
                              </span>
                            )}
                          </button>
                          <div className="p-3">
                            <p className="truncate text-sm font-black text-ink">
                              {card.name}
                            </p>
                            <p className="mt-1 line-clamp-2 min-h-8 text-[11px] leading-4 text-sub">
                              {card.description ?? "설명이 준비 중입니다."}
                            </p>
                          </div>
                        </article>
                      ))}
                    </div>
                  ) : null}
                </section>
              );
            })}
          </div>
        </section>
      ) : null}

      {!loading && !error && tab === "mine" ? (
        <section aria-labelledby="my-gallery-title">
          <div className="relative mb-6 overflow-hidden rounded-[26px] bg-[#151b15] p-6 text-white shadow-xl sm:p-8">
            <div className="absolute inset-0 bg-[radial-gradient(circle_at_80%_20%,rgba(201,170,71,.28),transparent_40%)]" />
            <div className="relative">
              <p className="text-xs font-bold uppercase tracking-[0.22em] text-[#d4b85f]">
                Private collection
              </p>
              <h2
                id="my-gallery-title"
                className="mt-2 text-3xl font-black tracking-[-0.04em]"
              >
                나의 카드 갤러리
              </h2>
              <p className="mt-2 max-w-lg text-sm leading-6 text-white/60">
                현재 보유 중인 카드만 전시됩니다. 일러스트를 누르면 원본을 크게
                감상할 수 있어요.
              </p>
              <div className="mt-6 grid grid-cols-3 gap-2 sm:max-w-lg">
                {[
                  ["보유 종류", `${ownedUniqueCount}종`],
                  ["총 카드", `${ownedTotalCount}장`],
                  [
                    "최고 등급",
                    highestOwnedRarity ? RARITY_LABEL[highestOwnedRarity] : "-",
                  ],
                ].map(([label, value]) => (
                  <div
                    key={label}
                    className="rounded-xl border border-white/10 bg-white/5 p-3 backdrop-blur"
                  >
                    <p className="text-[10px] font-bold text-white/45">
                      {label}
                    </p>
                    <p className="mt-1 text-sm font-black text-[#f3d77c] sm:text-base">
                      {value}
                    </p>
                  </div>
                ))}
              </div>
            </div>
          </div>

          <div className="mb-5 flex gap-2 overflow-x-auto pb-1">
            {(["ALL", ...RARITY_ORDER] as const).map((rarity) => (
              <button
                key={rarity}
                type="button"
                onClick={() => setMineRarity(rarity)}
                className={`whitespace-nowrap rounded-full px-3.5 py-2 text-xs font-black ${
                  mineRarity === rarity
                    ? "bg-[#20281f] text-[#f3d77c]"
                    : "border border-line bg-white text-sub"
                }`}
              >
                {rarity === "ALL" ? "전체" : RARITY_LABEL[rarity]}
              </button>
            ))}
          </div>

          {ownedCards.length === 0 ? (
            <div className="rounded-2xl border border-dashed border-line bg-white px-6 py-16 text-center">
              <span className="material-symbols-outlined text-5xl text-[#aeb7aa]">
                playing_cards
              </span>
              <p className="mt-3 font-black text-ink">
                전시할 카드가 아직 없어요.
              </p>
              <p className="mt-1 text-sm text-sub">
                오늘의 일지를 작성하고 첫 카드팩을 받아보세요.
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
              {ownedCards.map((card) => (
                <article
                  key={card.id}
                  className={`group relative overflow-hidden rounded-[20px] border-2 bg-[#20261f] p-2 shadow-lg ${
                    card.rarity === "GOLDEN_RARE"
                      ? "border-[#d8b640]"
                      : "border-[#303a2e]"
                  }`}
                >
                  <button
                    type="button"
                    onClick={() => setSelectedCard(card)}
                    aria-label={`${card.name} 원본 일러스트 크게 보기`}
                    className="relative block aspect-[5/7] w-full overflow-hidden rounded-[14px] bg-black"
                  >
                    {card.imageUrl ? (
                      <Image
                        src={card.imageUrl}
                        alt={`${card.name} 카드 일러스트`}
                        fill
                        sizes="(max-width: 640px) 50vw, (max-width: 1024px) 33vw, 20vw"
                        className="object-cover transition duration-300 group-hover:scale-[1.035]"
                      />
                    ) : null}
                    <span className="absolute right-2 top-2 min-w-8 rounded-full border border-white/20 bg-black/70 px-2 py-1 text-center text-xs font-black text-white backdrop-blur">
                      ×{card.ownedCount}
                    </span>
                    <span className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/90 to-transparent px-3 pb-3 pt-10 text-left">
                      <span className="block text-sm font-black text-white">
                        {card.name}
                      </span>
                      <span className="mt-0.5 block text-[10px] font-bold text-[#e1c76e]">
                        {RARITY_LABEL[card.rarity]}
                      </span>
                    </span>
                  </button>
                </article>
              ))}
            </div>
          )}
        </section>
      ) : null}

      {!loading && !error && tab === "history" ? (
        <section aria-labelledby="history-title">
          <h2 id="history-title" className="mb-4 text-2xl font-black text-ink">
            개봉 내역
          </h2>
          {history?.content.length ? (
            <div className="space-y-3">
              {history.content.map((draw) => {
                const content = (
                  <>
                    <div>
                      <p className="font-black">카드팩 #{draw.drawId}</p>
                      <p className="mt-1 text-xs text-sub">
                        {new Date(draw.createdAt).toLocaleString("ko-KR")}
                      </p>
                    </div>
                    <div className="text-right">
                      <p className="text-sm font-bold">
                        {draw.status === "COMPLETED"
                          ? "5장 확정"
                          : draw.status === "REFUNDED"
                            ? "포인트 반환"
                            : draw.status}
                      </p>
                      <p className="mt-1 text-xs text-brand">
                        {draw.status === "REFUNDED"
                          ? "구매가 취소됐어요"
                          : draw.resultViewedAt
                            ? "다시 보기"
                            : "결과 확인"}
                      </p>
                    </div>
                  </>
                );
                const className =
                  "flex items-center justify-between rounded-2xl border border-line bg-white px-5 py-4 text-ink shadow-sm";
                return draw.status === "REFUNDED" ? (
                  <div key={draw.drawId} className={className}>
                    {content}
                  </div>
                ) : (
                  <Link
                    key={draw.drawId}
                    href={`/gacha/open/${draw.drawId}`}
                    className={`${className} hover:text-ink`}
                  >
                    {content}
                  </Link>
                );
              })}
            </div>
          ) : (
            <div className="rounded-2xl bg-white p-12 text-center text-sub">
              아직 개봉 내역이 없습니다.
            </div>
          )}
        </section>
      ) : null}

      {rates ? (
        <section className="mt-10 rounded-2xl border border-line bg-white p-5">
          <h2 className="font-black text-ink">팩 확률 안내</h2>
          <div className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-5">
            {rates.rarities.map((rate) => (
              <div key={rate.rarity} className="rounded-xl bg-[#f6f7f3] p-3">
                <p className="text-[11px] font-bold text-sub">
                  {RARITY_LABEL[rate.rarity]}
                </p>
                <p className="mt-1 text-sm font-black text-ink">
                  {Number(rate.percent).toFixed(3)}%
                </p>
              </div>
            ))}
          </div>
          <ul className="mt-4 space-y-1 text-xs leading-5 text-sub">
            {rates.notices.map((notice) => (
              <li key={notice}>· {notice}</li>
            ))}
          </ul>
        </section>
      ) : null}

      {selectedCard?.imageUrl ? (
        <div
          role="presentation"
          onClick={(event) => {
            if (event.target === event.currentTarget) setSelectedCard(null);
          }}
          className="fixed inset-0 z-[100] flex items-center justify-center bg-[#101a12]/85 p-4 backdrop-blur-md"
        >
          <div
            role="dialog"
            aria-modal="true"
            aria-label={`${selectedCard.name} 원본 일러스트`}
            className="relative w-[min(92vw,58vh,560px)]"
          >
            <button
              type="button"
              onClick={() => setSelectedCard(null)}
              aria-label="원본 일러스트 닫기"
              className="absolute -top-12 right-0 flex h-10 w-10 items-center justify-center rounded-full bg-white/15 text-white backdrop-blur"
            >
              <span className="material-symbols-outlined">close</span>
            </button>
            <div className="relative aspect-[1122/1402] overflow-hidden rounded-[24px] border border-white/20 bg-white/[.04] shadow-[0_28px_80px_rgba(0,0,0,.38)]">
              <Image
                src={selectedCard.imageUrl}
                alt={`${selectedCard.name} 원본 카드 일러스트`}
                fill
                priority
                sizes="(max-width: 640px) 92vw, 560px"
                className="object-contain"
              />
            </div>
            <div className="mt-4 text-center text-white">
              <p className="text-xl font-black">{selectedCard.name}</p>
              <p className="mt-1 text-sm text-white/60">
                {RARITY_LABEL[selectedCard.rarity]}
                {selectedCard.ownedCount > 0
                  ? ` · ${selectedCard.ownedCount}장 보유`
                  : " · 도감 해금"}
              </p>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
