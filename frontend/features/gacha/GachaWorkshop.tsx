"use client";

import Image from "next/image";
import { useMemo, useState } from "react";
import { ApiError } from "@/lib/api";
import {
  GachaCollectionCard,
  GachaCosmetic,
  dismantleGachaCards,
  equipGachaCosmetic,
  purchaseGachaCosmetic,
  unequipGachaCosmetic,
} from "@/lib/gacha-api";
import { useUI } from "@/lib/ui";
import {
  notifyGachaCosmeticsChanged,
  useGachaCosmetics,
} from "@/features/gacha/use-gacha-cosmetics";

const COSMETIC_DESCRIPTION: Record<string, string> = {
  TITLE_SPROUT_COLLECTOR: "수집을 시작한 정원사를 위한 첫 칭호",
  TITLE_GARDEN_KEEPER: "꾸준히 카드를 가꾼 수집가의 칭호",
  TITLE_CARD_MASTER: "도감을 사랑하는 숙련 수집가의 칭호",
  BORDER_SPROUT_VINE: "싱그러운 덩굴과 잎이 감싸는 카드 테두리",
  BORDER_BLOOM_GARDEN: "분홍빛 꽃잎과 은은한 광택의 카드 테두리",
  BORDER_GOLDEN_HARVEST: "금빛 이중선과 잔잔한 빛이 흐르는 카드 테두리",
};

export default function GachaWorkshop({
  accessToken,
  collection,
  onCollectionRefresh,
}: {
  accessToken: string;
  collection: GachaCollectionCard[];
  onCollectionRefresh: () => Promise<void>;
}) {
  const { showToast, askConfirm } = useUI();
  const { data, setData, refresh } = useGachaCosmetics(accessToken);
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

  const runDismantle = async () => {
    if (!selected.length || busy) return;
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
      notifyGachaCosmeticsChanged();
      showToast(`${result.earnedShards}조각을 획득했어요.`);
    } catch (error) {
      showToast(message(error, "카드를 분해하지 못했어요."), "err");
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
      notifyGachaCosmeticsChanged();
      showToast(`${cosmetic.name}을(를) 해금했어요.`);
    } catch (error) {
      showToast(message(error, "해금하지 못했어요."), "err");
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
      notifyGachaCosmeticsChanged();
      showToast(cosmetic.equipped ? "장착을 해제했어요." : "장착했어요.");
    } catch (error) {
      showToast(message(error, "장착 상태를 바꾸지 못했어요."), "err");
    } finally {
      setBusy(false);
    }
  };

  return (
    <section aria-labelledby="workshop-title" className="space-y-7">
      <div className="rounded-[28px] bg-gradient-to-br from-[#24432d] to-[#47644d] p-6 text-white shadow-sm">
        <p className="text-xs font-black uppercase tracking-[0.2em] text-[#cde2c9]">
          Shard workshop
        </p>
        <div className="mt-2 flex flex-wrap items-end justify-between gap-4">
          <div>
            <h2 id="workshop-title" className="text-2xl font-black">
              조각 공방
            </h2>
            <p className="mt-1 text-sm text-white/70">
              같은 카드는 한 장을 남기고 분해할 수 있어요.
            </p>
          </div>
          <div className="rounded-2xl bg-white/10 px-5 py-3 text-right backdrop-blur">
            <p className="text-xs text-white/65">보유 카드 조각</p>
            <p className="text-2xl font-black">{data?.shards.balance ?? 0} ✦</p>
            <p className="text-xs text-white/55">
              누적 획득 {data?.shards.lifetimeEarned ?? 0}
            </p>
          </div>
        </div>
      </div>

      <div className="rounded-3xl border border-line bg-white p-5 shadow-sm sm:p-6">
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
            onClick={() =>
              setQuantities(
                Object.fromEntries(
                  dismantleableCards.map((card) => [
                    card.id,
                    card.dismantleableCount,
                  ]),
                ),
              )
            }
            className="rounded-xl border border-brand px-4 py-2 text-sm font-extrabold text-brand disabled:opacity-40"
          >
            분해 가능한 중복 전체 선택
          </button>
        </div>

        {dismantleableCards.length ? (
          <div className="grid gap-3 sm:grid-cols-2">
            {dismantleableCards.map((card) => {
              const quantity = quantities[card.id] ?? 0;
              return (
                <article
                  key={card.id}
                  className="flex items-center gap-3 rounded-2xl bg-[#f5f7f2] p-3"
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
                        setQuantities((current) => ({
                          ...current,
                          [card.id]: Math.min(
                            card.dismantleableCount,
                            quantity + 1,
                          ),
                        }))
                      }
                      className="h-8 w-8 rounded-lg font-black"
                    >
                      +
                    </button>
                  </div>
                </article>
              );
            })}
          </div>
        ) : (
          <p className="rounded-2xl bg-[#f5f7f2] p-8 text-center text-sm text-sub">
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
                ok: "분해하기",
                danger: true,
                onOk: () => void runDismantle(),
              })
            }
            className="rounded-xl bg-brand px-5 py-3 font-extrabold text-white disabled:opacity-40"
          >
            분해하기
          </button>
        </div>
      </div>

      <div>
        <h3 className="text-xl font-black">칭호·테두리 상점</h3>
        <p className="mt-1 text-sm text-sub">
          한 번 해금하면 자유롭게 장착하고 해제할 수 있어요.
        </p>
        <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
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
                  {cosmetic.type === "TITLE" ? "칭호" : "테두리"}
                </span>
                <span className="font-black text-[#8b6b16]">
                  {cosmetic.price} ✦
                </span>
              </div>
              <h4 className="mt-5 text-lg font-black">{cosmetic.name}</h4>
              <p className="mt-1 min-h-10 text-xs leading-5 text-sub">
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
    </section>
  );
}

function message(error: unknown, fallback: string) {
  return error instanceof ApiError ? error.message : fallback;
}
