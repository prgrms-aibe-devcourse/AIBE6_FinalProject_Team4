"use client";

import { Suspense } from "react";
import { MarketTradeModal } from "@/features/card-market/CardMarketComponents";
import CardMarketHeader from "@/features/card-market/CardMarketHeader";
import {
  MarketListingsSection,
  MarketNegotiationsSection,
  MarketSellSection,
  MarketTradesSection,
} from "@/features/card-market/CardMarketSections";
import { useCardMarketPage } from "@/features/card-market/use-card-market-page";
import { formatPoint } from "@/features/commerce/presentation";
import {
  MarketSellableCard,
  acceptMarketNegotiation,
  buyMarketListing,
  cancelMarketListing,
  cancelMarketNegotiation,
  createMarketListing,
  createMarketNegotiation,
  proposeMarketPrice,
  rejectMarketNegotiation,
} from "@/features/card-market/api";
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
  const { askConfirm } = useUI();
  const market = useCardMarketPage();
  const token = market.token;
  const negotiations = market.tab === "sent" ? market.sent : market.received;

  const submitListing = (card: MarketSellableCard) => {
    if (!token) return;
    const priceValue = Number(market.sellPrices[card.cardId]);
    const sellerReceived = Math.floor(priceValue * 0.8);
    const assetGuide =
      card.rarity === "GOLDEN_RARE"
        ? `선택한 개체 #${market.selectedGolden[card.cardId]}가 판매 등록됩니다.`
        : "판매 중에는 해당 카드 1장이 보유 수량에서 분리되며, 판매 취소나 기간 만료 시 돌아옵니다.";
    const execute = () =>
      void market.runAction(
        () =>
          createMarketListing(
            card.cardId,
            card.rarity === "GOLDEN_RARE"
              ? (market.selectedGolden[card.cardId] ?? null)
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
              body: `${card.cardName} · 개체 #${market.selectedGolden[card.cardId]}를 판매합니다. 등록 중에는 다른 거래에 사용할 수 없고, 판매가 완료되면 소유권이 구매자에게 즉시 이전되어 되돌릴 수 없습니다.`,
              ok: "위험을 확인하고 등록",
              danger: true,
              onOk: execute,
            }),
          0,
        );
      },
    });
  };

  return (
    <main className="min-h-screen bg-[#f4f6f1] px-4 pb-24 pt-[26px] text-[#263023] md:px-8">
      <div className="mx-auto max-w-[1080px]">
        <CardMarketHeader
          tab={market.tab}
          authenticated={Boolean(token)}
          wallet={market.wallet}
          onTab={market.selectPrivateTab}
        />

        {market.notice ? (
          <div className="mb-5 rounded-2xl bg-[#edf5e8] px-5 py-4 font-bold text-[#476541]">
            {market.notice}
          </div>
        ) : null}
        {market.error ? (
          <div className="mb-5 rounded-2xl bg-[#fff0eb] px-5 py-4 font-bold text-[#a64d35]">
            {market.error}
          </div>
        ) : null}
        {(
          market.tab === "market" ? market.marketLoading : market.privateLoading
        ) ? (
          <div className="rounded-3xl bg-white p-16 text-center font-bold text-[#7a8476]">
            거래소를 불러오는 중...
          </div>
        ) : null}

        {!market.marketLoading && market.tab === "market" ? (
          <MarketListingsSection
            listings={market.listings}
            userId={market.userId}
            assetType={market.assetType}
            sort={market.sort}
            keyword={market.keyword}
            keywordInput={market.keywordInput}
            page={market.marketPage}
            totalPages={market.marketTotalPages}
            onKeywordInput={market.setKeywordInput}
            onQuery={market.changeMarketQuery}
            onSelect={market.selectListing}
          />
        ) : null}

        {!market.privateLoading && market.tab === "sell" && token ? (
          <MarketSellSection
            sellable={market.sellable}
            activeListings={market.activeListings}
            prices={market.sellPrices}
            selectedGolden={market.selectedGolden}
            busy={market.actionLoading}
            page={market.privatePage}
            totalPages={market.privateTotalPages}
            onPrice={(cardId, value) =>
              market.setSellPrices((current) => ({
                ...current,
                [cardId]: value,
              }))
            }
            onGolden={(cardId, value) =>
              market.setSelectedGolden((current) => ({
                ...current,
                [cardId]: value,
              }))
            }
            onSubmit={submitListing}
            onCancel={(listing) =>
              void market.runAction(
                () => cancelMarketListing(listing.id, token),
                "판매를 취소했어요.",
              )
            }
            onPage={market.changePrivatePage}
          />
        ) : null}

        {!market.privateLoading &&
        (market.tab === "sent" || market.tab === "received") &&
        token ? (
          <MarketNegotiationsSection
            mode={market.tab}
            negotiations={negotiations}
            userId={market.userId!}
            prices={market.counterPrices}
            busy={market.actionLoading}
            page={market.privatePage}
            totalPages={market.privateTotalPages}
            onPrice={(negotiationId, value) =>
              market.setCounterPrices((current) => ({
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
                  void market.runAction(
                    () => acceptMarketNegotiation(negotiation.id, token),
                    "가격 제안을 수락해 거래를 완료했어요.",
                  ),
              })
            }
            onReject={(negotiation) =>
              void market.runAction(
                () => rejectMarketNegotiation(negotiation.id, token),
                "가격 제안을 거절했어요.",
              )
            }
            onCancel={(negotiation) =>
              void market.runAction(
                () => cancelMarketNegotiation(negotiation.id, token),
                "가격 제안을 취소했어요.",
              )
            }
            onPropose={(negotiation) =>
              void market.runAction(
                () =>
                  proposeMarketPrice(
                    negotiation.id,
                    Number(market.counterPrices[negotiation.id]),
                    null,
                    token,
                  ),
                "새로운 가격을 제안했어요.",
              )
            }
            onPage={market.changePrivatePage}
          />
        ) : null}

        {!market.privateLoading && market.tab === "trades" && token ? (
          <MarketTradesSection
            trades={market.trades}
            userId={market.userId!}
            page={market.privatePage}
            totalPages={market.privateTotalPages}
            onPage={market.changePrivatePage}
          />
        ) : null}
      </div>

      {market.selectedListing && token ? (
        <MarketTradeModal
          listing={market.selectedListing}
          offerPrice={market.offerPrice}
          busy={market.actionLoading}
          onOfferPrice={market.setOfferPrice}
          onClose={() => market.setSelectedListing(null)}
          onOffer={(price) =>
            void market.runAction(
              () =>
                createMarketNegotiation(
                  market.selectedListing!.id,
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
              body: `${market.selectedListing!.cardName}을 ${formatPoint(market.selectedListing!.askingPrice)}에 구매합니다. 거래 가능 포인트가 사용되며 완료 후 취소하거나 환불할 수 없습니다.`,
              ok: "구매 확정",
              onOk: () =>
                void market.runAction(
                  () => buyMarketListing(market.selectedListing!.id, token),
                  "카드 구매를 완료했어요.",
                ),
            })
          }
        />
      ) : null}
    </main>
  );
}
