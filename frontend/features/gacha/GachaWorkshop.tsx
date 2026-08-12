"use client";

import Image from "next/image";
import { useMemo, useState } from "react";
import GachaTitleBadge from "@/components/gacha/GachaTitleBadge";
import ProfileCosmeticFrame from "@/components/gacha/ProfileCosmeticFrame";
import { commerceErrorMessage } from "@/features/commerce/presentation";
import {
  GachaCollectionCard,
  GachaCosmetic,
  dismantleGachaCards,
  equipGachaCosmetic,
  purchaseGachaCosmetic,
  unequipGachaCosmetic,
} from "@/lib/gacha-api";
import { useUI } from "@/lib/ui";
import { useGachaCosmetics } from "@/features/gacha/use-gacha-cosmetics";
import GachaWorkshopOverview from "@/features/gacha/GachaWorkshopOverview";

const COSMETIC_DESCRIPTION: Record<string, string> = {
  TITLE_SPROUT_COLLECTOR: "새싹빛과 잎사귀가 피어나는 생동감 효과 칭호",
  TITLE_GARDEN_KEEPER: "수호 문양과 청록빛 별이 맴도는 오라 효과 칭호",
  TITLE_CARD_MASTER: "보랏빛 오로라와 황금 별빛이 흐르는 최상위 칭호",
  BORDER_SPROUT_VINE: "싱그러운 풀잎이 프로필을 감싸는 생명의 테두리",
  BORDER_BLOOM_GARDEN: "벚꽃 송이와 흩날리는 꽃잎이 피어나는 테두리",
  BORDER_GOLDEN_HARVEST: "황금 사과 문장과 찬란한 별빛이 빛나는 최고급 테두리",
};

const MAX_DISMANTLE_QUANTITY = 20;
const DISMANTLE_RARITY_PRIORITY: Record<string, number> = {
  COMMON: 0,
  RARE: 1,
  SUPER_RARE: 2,
};

export default function GachaWorkshop({
  accessToken,
  collection,
  onCollectionRefresh,
  onBack,
  initialSection = "menu",
}: {
  accessToken: string;
  collection: GachaCollectionCard[];
  onCollectionRefresh: () => Promise<void>;
  onBack?: () => void;
  initialSection?: "menu" | "dismantle" | "cosmetics";
}) {
  const { showToast, askConfirm } = useUI();
  const { data, setData, refresh } = useGachaCosmetics();
  const [section, setSection] = useState<"menu" | "dismantle" | "cosmetics">(
    initialSection,
  );
  const [quantities, setQuantities] = useState<Record<number, number>>({});
  const [busy, setBusy] = useState(false);
  const dismantleableCards = collection.filter(
    (card) => card.dismantleableCount > 0,
  );
  const selected = useMemo(
    () =>
      dismantleableCards
        .map((card) => ({
          card,
          quantity: Math.min(
            Math.max(quantities[card.id] ?? 0, 0),
            card.dismantleableCount,
          ),
        }))
        .filter((item) => item.quantity > 0),
    [dismantleableCards, quantities],
  );
  const selectedCount = selected.reduce((sum, item) => sum + item.quantity, 0);
  const expectedShards = selected.reduce(
    (sum, item) => sum + item.quantity * item.card.shardPerCard,
    0,
  );

  const selectLowestRarityCards = () => {
    let remaining = MAX_DISMANTLE_QUANTITY;
    const next: Record<number, number> = {};

    [...dismantleableCards]
      .sort(
        (left, right) =>
          (DISMANTLE_RARITY_PRIORITY[left.rarity] ?? 99) -
            (DISMANTLE_RARITY_PRIORITY[right.rarity] ?? 99) ||
          left.displayOrder - right.displayOrder ||
          left.id - right.id,
      )
      .forEach((card) => {
        if (remaining <= 0) return;
        const quantity = Math.min(card.dismantleableCount, remaining);
        if (quantity > 0) {
          next[card.id] = quantity;
          remaining -= quantity;
        }
      });

    setQuantities(next);
  };

  const runDismantle = async () => {
    if (!selected.length || busy) return;
    if (selectedCount > MAX_DISMANTLE_QUANTITY) {
      showToast("한 번에 최대 20장까지 분해할 수 있어요.", "err");
      return;
    }
    setBusy(true);
    try {
      const result = await dismantleGachaCards(
        selected.map(({ card, quantity }) => ({ cardId: card.id, quantity })),
        accessToken,
        crypto.randomUUID(),
      );
      setQuantities({});
      await onCollectionRefresh();
      await refresh();
      showToast(`${result.earnedShards}조각을 획득했어요.`);
    } catch (error) {
      showToast(
        commerceErrorMessage(error, "카드를 분해하지 못했어요."),
        "err",
      );
    } finally {
      setBusy(false);
    }
  };

  const purchase = async (cosmetic: GachaCosmetic) => {
    if (busy) return;
    setBusy(true);
    try {
      const next = await purchaseGachaCosmetic(
        cosmetic.code,
        accessToken,
        crypto.randomUUID(),
      );
      setData(next);
      showToast(`${cosmetic.name}을(를) 해금했어요.`);
    } catch (error) {
      showToast(commerceErrorMessage(error, "해금하지 못했어요."), "err");
    } finally {
      setBusy(false);
    }
  };

  const toggleEquip = async (cosmetic: GachaCosmetic) => {
    if (busy) return;
    setBusy(true);
    try {
      const next = cosmetic.equipped
        ? await unequipGachaCosmetic(cosmetic.type, accessToken)
        : await equipGachaCosmetic(cosmetic.code, accessToken);
      setData(next);
      showToast(cosmetic.equipped ? "장착을 해제했어요." : "장착했어요.");
    } catch (error) {
      showToast(
        commerceErrorMessage(error, "장착 상태를 바꾸지 못했어요."),
        "err",
      );
    } finally {
      setBusy(false);
    }
  };

  return (
    <section aria-labelledby="workshop-title" className="space-y-8">
      <GachaWorkshopOverview
        section={section}
        onSectionChange={setSection}
        data={data}
        onBack={onBack}
      />

      {section === "dismantle" ? (
        <div
          id="atelier-dismantle"
          role="tabpanel"
          className="rounded-[28px] border border-[#cbd9c8] bg-[#f1f6ee] p-5 shadow-sm sm:p-7"
        >
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
            <div>
              <h3 className="text-lg font-black">중복 카드 분해</h3>
              <p className="mt-1 text-sm text-sub">
                커먼 1 · 레어 3 · 슈퍼 레어 10조각
              </p>
            </div>
            <button
              type="button"
              disabled={!dismantleableCards.length || busy}
              onClick={selectLowestRarityCards}
              className="rounded-xl border border-brand px-4 py-2 text-sm font-extrabold text-brand disabled:opacity-40"
            >
              낮은 등급부터 20개 선택
            </button>
          </div>

          <div className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-[#d7e2d4] bg-white px-4 py-3">
            <p className="text-sm font-bold leading-6 text-[#5f6f62]">
              한 번에 최대 20장까지 변환할 수 있어요. 자동 선택은 커먼 → 레어 →
              슈퍼 레어 순서로 담습니다.
            </p>
            <div className="min-w-24 text-right" aria-live="polite">
              <p className="text-[10px] font-black uppercase tracking-[0.14em] text-sub">
                선택 수량
              </p>
              <p className="mt-0.5 text-xl font-black tabular-nums text-brand-dark">
                {selectedCount}/{MAX_DISMANTLE_QUANTITY}
              </p>
            </div>
          </div>

          {dismantleableCards.length ? (
            <div className="grid gap-3 sm:grid-cols-2">
              {dismantleableCards.map((card) => {
                const quantity = quantities[card.id] ?? 0;
                return (
                  <article
                    key={card.id}
                    className="flex items-center gap-3 rounded-2xl border border-[#dce5d9] bg-white p-3 shadow-sm"
                  >
                    <div className="relative aspect-[1122/1402] w-16 shrink-0 overflow-hidden rounded-lg bg-[#e3e9df]">
                      {card.imageUrl ? (
                        <Image
                          src={card.imageUrl}
                          alt=""
                          fill
                          sizes="64px"
                          className="object-cover"
                        />
                      ) : null}
                    </div>
                    <div className="min-w-0 flex-1">
                      <p className="truncate font-extrabold">{card.name}</p>
                      <p className="text-xs text-sub">
                        {card.ownedCount}장 보유 · 장당 {card.shardPerCard}조각
                      </p>
                    </div>
                    <div className="flex items-center rounded-xl bg-white p-1 shadow-sm">
                      <button
                        type="button"
                        aria-label={`${card.name} 분해 수량 감소`}
                        onClick={() =>
                          setQuantities((current) => ({
                            ...current,
                            [card.id]: Math.max(0, quantity - 1),
                          }))
                        }
                        className="h-8 w-8 rounded-lg font-black"
                      >
                        −
                      </button>
                      <span className="w-8 text-center text-sm font-black">
                        {quantity}
                      </span>
                      <button
                        type="button"
                        aria-label={`${card.name} 분해 수량 증가`}
                        onClick={() =>
                          setQuantities((current) => {
                            const currentTotal = Object.values(current).reduce(
                              (sum, value) => sum + Math.max(0, value),
                              0,
                            );
                            if (currentTotal >= MAX_DISMANTLE_QUANTITY) {
                              return current;
                            }
                            return {
                              ...current,
                              [card.id]: Math.min(
                                card.dismantleableCount,
                                (current[card.id] ?? 0) + 1,
                              ),
                            };
                          })
                        }
                        disabled={
                          selectedCount >= MAX_DISMANTLE_QUANTITY ||
                          quantity >= card.dismantleableCount
                        }
                        className="h-8 w-8 rounded-lg font-black disabled:cursor-not-allowed disabled:opacity-30"
                      >
                        +
                      </button>
                    </div>
                  </article>
                );
              })}
            </div>
          ) : (
            <p className="rounded-2xl border border-[#dce5d9] bg-white p-8 text-center text-sm text-sub">
              지금 분해할 수 있는 중복 카드가 없어요.
            </p>
          )}

          <div className="mt-5 flex flex-wrap items-center justify-between gap-3 border-t border-line pt-5">
            <p className="font-bold text-sub">
              {selectedCount}장 분해 ·{" "}
              <strong className="text-brand">{expectedShards}조각</strong> 예상
            </p>
            <button
              type="button"
              disabled={!selected.length || busy}
              onClick={() =>
                askConfirm({
                  icon: "recycling",
                  title: "선택한 카드를 분해할까요?",
                  body: `${selectedCount}장을 분해해 ${expectedShards}조각을 획득합니다. 카드별 한 장은 남습니다.`,
                  ok: `${selectedCount}개 변환하기`,
                  danger: true,
                  onOk: () => void runDismantle(),
                })
              }
              className="rounded-xl bg-brand px-5 py-3 font-extrabold text-white disabled:opacity-40"
            >
              {selectedCount}개 변환하기
            </button>
          </div>
        </div>
      ) : null}

      {section === "cosmetics" ? (
        <div
          id="atelier-cosmetics"
          role="tabpanel"
          className="rounded-[28px] border border-[#e4d6aa] bg-[#fff9e8] p-5 shadow-sm sm:p-7"
        >
          <p className="text-xs font-black uppercase tracking-[0.2em] text-[#8b6b16]">
            Atelier collection
          </p>
          <h3 className="mt-1 text-xl font-black">이펙트 칭호·프로필 테두리</h3>
          <p className="mt-1 text-sm text-sub">
            한 번 해금하면 자유롭게 장착하고 해제할 수 있어요.
          </p>
          <div className="mt-6 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {(data?.cosmetics ?? []).map((cosmetic) => (
              <article
                key={cosmetic.code}
                className={`rounded-3xl border bg-white p-5 shadow-sm ${
                  cosmetic.equipped
                    ? "border-brand ring-2 ring-brand/10"
                    : "border-line"
                }`}
              >
                <div className="flex items-start justify-between gap-2">
                  <span className="rounded-full bg-[#edf3e9] px-3 py-1 text-xs font-black text-brand">
                    {cosmetic.type === "TITLE" ? "칭호" : "프로필 테두리"}
                  </span>
                  <span className="font-black text-[#8b6b16]">
                    {cosmetic.price} ✦
                  </span>
                </div>
                <h4 className="mt-5 text-lg font-black">{cosmetic.name}</h4>
                {cosmetic.type === "TITLE" ? (
                  <div className="mt-4 flex min-h-24 items-center justify-center overflow-hidden rounded-2xl bg-[#101713] px-3 py-5">
                    <GachaTitleBadge
                      code={cosmetic.code}
                      name={cosmetic.name}
                      size="showcase"
                    />
                  </div>
                ) : (
                  <div
                    aria-label={`${cosmetic.name} 프로필 테두리 미리보기`}
                    className="mt-4 flex min-h-52 flex-col items-center justify-center overflow-hidden rounded-2xl bg-[radial-gradient(circle_at_50%_38%,#f7f1dc,#e7ddbf)] px-4 py-5"
                  >
                    <ProfileCosmeticFrame
                      borderCode={cosmetic.code}
                      className="h-28 w-28"
                    >
                      <div className="flex h-full w-full items-center justify-center rounded-full bg-gradient-to-br from-[#b8d992] to-[#679849] text-4xl font-black text-white shadow-inner">
                        키
                      </div>
                    </ProfileCosmeticFrame>
                    <span className="mt-5 text-[10px] font-black tracking-[0.16em] text-[#77643a]">
                      PROFILE FRAME
                    </span>
                  </div>
                )}
                <p className="mt-3 min-h-10 text-xs leading-5 text-sub">
                  {COSMETIC_DESCRIPTION[cosmetic.code]}
                </p>
                {cosmetic.owned ? (
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() => void toggleEquip(cosmetic)}
                    className={`mt-5 w-full rounded-xl py-2.5 font-extrabold ${
                      cosmetic.equipped
                        ? "border border-line text-sub"
                        : "bg-ink text-white"
                    }`}
                  >
                    {cosmetic.equipped ? "장착 해제" : "장착하기"}
                  </button>
                ) : (
                  <button
                    type="button"
                    disabled={
                      busy || (data?.shards.balance ?? 0) < cosmetic.price
                    }
                    onClick={() =>
                      askConfirm({
                        icon: "auto_awesome",
                        title: `${cosmetic.name} 해금`,
                        body: `${cosmetic.price}조각을 사용합니다.`,
                        ok: "해금하기",
                        onOk: () => void purchase(cosmetic),
                      })
                    }
                    className="mt-5 w-full rounded-xl bg-brand py-2.5 font-extrabold text-white disabled:opacity-35"
                  >
                    해금하기
                  </button>
                )}
              </article>
            ))}
          </div>
        </div>
      ) : null}
    </section>
  );
}
