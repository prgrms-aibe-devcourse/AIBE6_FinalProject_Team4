import {
  ListingCard,
  NegotiationCard,
  PrivatePager,
  SectionTitle,
  SellableCardRow,
} from "@/features/card-market/CardMarketComponents";
import CommerceCardImage from "@/features/commerce/CommerceCardImage";
import CommerceEmptyState from "@/features/commerce/CommerceEmptyState";
import {
  formatCommerceDateTime,
  formatPoint,
} from "@/features/commerce/presentation";
import {
  MarketAssetType,
  MarketListing,
  MarketNegotiation,
  MarketSellableCard,
  MarketTrade,
} from "@/features/card-market/api";

export type MarketSort =
  "createdAt,desc" | "askingPrice,asc" | "askingPrice,desc";

interface MarketListingsSectionProps {
  listings: MarketListing[];
  userId?: number;
  assetType?: MarketAssetType;
  sort: MarketSort;
  keyword: string;
  keywordInput: string;
  page: number;
  totalPages: number;
  onKeywordInput: (value: string) => void;
  onQuery: (
    assetType: MarketAssetType | undefined,
    page: number,
    sort?: MarketSort,
    keyword?: string,
  ) => void;
  onSelect: (listing: MarketListing) => void;
}

export function MarketListingsSection({
  listings,
  userId,
  assetType,
  sort,
  keyword,
  keywordInput,
  page,
  totalPages,
  onKeywordInput,
  onQuery,
  onSelect,
}: MarketListingsSectionProps) {
  return (
    <section className="rounded-[30px] border border-[#e0e6dc] bg-white/45 p-5 md:p-8">
      <div className="mb-5 flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-xs font-black uppercase tracking-[0.18em] text-[#8b947f]">
            Open listings
          </p>
          <h2 className="mt-1 text-2xl font-black">판매 중인 카드</h2>
        </div>
        <div className="flex flex-wrap gap-2">
          {([undefined, "HYPER_RARE", "GOLDEN_RARE"] as const).map((type) => (
            <button
              key={type ?? "ALL"}
              type="button"
              onClick={() => onQuery(type, 0)}
              className={`rounded-full px-4 py-2 text-xs font-black ${assetType === type ? "bg-[#344b32] text-white" : "bg-white text-[#667260]"}`}
            >
              {type === undefined
                ? "전체"
                : type === "HYPER_RARE"
                  ? "하이퍼"
                  : "골든"}
            </button>
          ))}
        </div>
      </div>
      <form
        className="mb-6 grid gap-3 rounded-2xl border border-[#dce4d7] bg-white p-3 sm:grid-cols-[1fr_180px_auto]"
        onSubmit={(event) => {
          event.preventDefault();
          onQuery(assetType, 0, sort, keywordInput);
        }}
      >
        <label className="flex items-center gap-2 rounded-xl bg-[#f4f6f1] px-4">
          <span className="material-symbols-outlined text-xl text-[#76816f]">
            search
          </span>
          <input
            value={keywordInput}
            maxLength={50}
            onChange={(event) => onKeywordInput(event.target.value)}
            placeholder="카드 이름 검색"
            className="min-w-0 flex-1 bg-transparent py-3 text-sm font-bold outline-none"
          />
        </label>
        <select
          value={sort}
          onChange={(event) =>
            onQuery(assetType, 0, event.target.value as MarketSort, keyword)
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
                onBuy={() => onSelect(listing)}
              />
            ))}
          </div>
          {totalPages > 1 ? (
            <div className="mt-8 flex items-center justify-center gap-3 border-t border-[#dfe5da] pt-6">
              <button
                type="button"
                disabled={page <= 0}
                onClick={() => onQuery(assetType, page - 1)}
                className="rounded-xl border border-[#cad5c5] bg-white px-5 py-2.5 text-sm font-black text-[#53644e] disabled:opacity-35"
              >
                이전
              </button>
              <span className="min-w-20 text-center text-sm font-black text-[#667260]">
                {page + 1} / {totalPages}
              </span>
              <button
                type="button"
                disabled={page + 1 >= totalPages}
                onClick={() => onQuery(assetType, page + 1)}
                className="rounded-xl border border-[#cad5c5] bg-white px-5 py-2.5 text-sm font-black text-[#53644e] disabled:opacity-35"
              >
                다음
              </button>
            </div>
          ) : null}
        </>
      ) : (
        <CommerceEmptyState text="현재 판매 중인 카드가 없어요." />
      )}
    </section>
  );
}

interface MarketSellSectionProps {
  sellable: MarketSellableCard[];
  activeListings: MarketListing[];
  prices: Record<number, string>;
  selectedGolden: Record<number, number>;
  busy: boolean;
  page: number;
  totalPages: number;
  onPrice: (cardId: number, value: string) => void;
  onGolden: (cardId: number, value: number) => void;
  onSubmit: (card: MarketSellableCard) => void;
  onCancel: (listing: MarketListing) => void;
  onPage: (page: number) => void;
}

export function MarketSellSection({
  sellable,
  activeListings,
  prices,
  selectedGolden,
  busy,
  page,
  totalPages,
  onPrice,
  onGolden,
  onSubmit,
  onCancel,
  onPage,
}: MarketSellSectionProps) {
  return (
    <section className="grid gap-12 rounded-[30px] border border-[#e0e6dc] bg-white/45 p-5 md:p-8 lg:grid-cols-[1.1fr_0.9fr]">
      <div>
        <SectionTitle eyebrow="Sell a card" title="판매할 카드 선택" />
        <p className="mb-5 text-sm leading-6 text-[#737d6e]">
          하이퍼는 동일 카드를 한 장 남기고 판매할 수 있으며, 골든은 개체를 직접
          선택합니다.
        </p>
        <div className="space-y-4">
          {sellable
            .filter((card) => card.sellableCount > 0)
            .map((card) => (
              <SellableCardRow
                key={card.cardId}
                card={card}
                price={prices[card.cardId] ?? ""}
                goldenId={selectedGolden[card.cardId]}
                busy={busy}
                onPrice={(value) => onPrice(card.cardId, value)}
                onGolden={(value) => onGolden(card.cardId, value)}
                onSubmit={() => onSubmit(card)}
              />
            ))}
          {!sellable.some((card) => card.sellableCount > 0) ? (
            <CommerceEmptyState text="현재 판매할 수 있는 카드가 없어요." />
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
                <CommerceCardImage
                  src={listing.imageUrl}
                  name={listing.cardName}
                />
              </div>
              <div className="min-w-0 flex-1">
                <p className="truncate font-black">{listing.cardName}</p>
                <p className="mt-1 text-sm font-bold text-[#8b6b18]">
                  {formatPoint(listing.askingPrice)}
                </p>
                <p className="mt-1 text-xs text-[#7a8476]">
                  받은 제안 {listing.activeOfferCount}개
                </p>
              </div>
              <button
                type="button"
                disabled={busy}
                onClick={() => onCancel(listing)}
                className="rounded-xl border border-[#d8bcb2] px-3 py-2 text-xs font-black text-[#9c4d3c]"
              >
                취소
              </button>
            </div>
          ))}
          {!activeListings.length ? (
            <CommerceEmptyState text="판매 중인 카드가 없어요." />
          ) : null}
        </div>
        <PrivatePager page={page} totalPages={totalPages} onChange={onPage} />
      </div>
    </section>
  );
}

interface MarketNegotiationsSectionProps {
  mode: "sent" | "received";
  negotiations: MarketNegotiation[];
  userId: number;
  prices: Record<number, string>;
  busy: boolean;
  page: number;
  totalPages: number;
  onPrice: (negotiationId: number, value: string) => void;
  onAccept: (negotiation: MarketNegotiation) => void;
  onReject: (negotiation: MarketNegotiation) => void;
  onCancel: (negotiation: MarketNegotiation) => void;
  onPropose: (negotiation: MarketNegotiation) => void;
  onPage: (page: number) => void;
}

export function MarketNegotiationsSection({
  mode,
  negotiations,
  userId,
  prices,
  busy,
  page,
  totalPages,
  onPrice,
  onAccept,
  onReject,
  onCancel,
  onPropose,
  onPage,
}: MarketNegotiationsSectionProps) {
  return (
    <section className="rounded-[30px] border border-[#e0e6dc] bg-white/45 p-5 md:p-8">
      <SectionTitle
        eyebrow={mode === "sent" ? "Sent offers" : "Received offers"}
        title={mode === "sent" ? "내가 보낸 가격 제안" : "내가 받은 가격 제안"}
      />
      <div className="grid gap-5 lg:grid-cols-2">
        {negotiations.map((negotiation) => (
          <NegotiationCard
            key={negotiation.id}
            negotiation={negotiation}
            userId={userId}
            price={prices[negotiation.id] ?? ""}
            busy={busy}
            onPrice={(value) => onPrice(negotiation.id, value)}
            onAccept={() => onAccept(negotiation)}
            onReject={() => onReject(negotiation)}
            onCancel={() => onCancel(negotiation)}
            onPropose={() => onPropose(negotiation)}
          />
        ))}
      </div>
      {!negotiations.length ? (
        <CommerceEmptyState text="가격 제안 내역이 없어요." />
      ) : null}
      <PrivatePager page={page} totalPages={totalPages} onChange={onPage} />
    </section>
  );
}

interface MarketTradesSectionProps {
  trades: MarketTrade[];
  userId: number;
  page: number;
  totalPages: number;
  onPage: (page: number) => void;
}

export function MarketTradesSection({
  trades,
  userId,
  page,
  totalPages,
  onPage,
}: MarketTradesSectionProps) {
  return (
    <section className="rounded-[30px] border border-[#e0e6dc] bg-white/45 p-5 md:p-8">
      <SectionTitle eyebrow="Trade history" title="완료된 거래" />
      <div className="space-y-3">
        {trades.map((trade) => (
          <div
            key={trade.id}
            className="grid items-center gap-4 rounded-2xl border border-[#dce3d7] bg-white p-4 md:grid-cols-[72px_1fr_auto_auto]"
          >
            <div className="h-24 w-16 overflow-hidden rounded-lg bg-[#edf0e9]">
              <CommerceCardImage src={trade.imageUrl} name={trade.cardName} />
            </div>
            <div>
              <p className="font-black">{trade.cardName}</p>
              <p className="mt-1 text-xs text-[#788273]">
                {trade.tradeType === "BUY_NOW" ? "바로 구매" : "가격 협상"} ·{" "}
                {formatCommerceDateTime(trade.completedAt)}
              </p>
            </div>
            <div className="text-left md:text-right">
              <p className="text-xs text-[#788273]">거래 금액</p>
              <p className="font-black text-[#765b12]">
                {formatPoint(trade.tradePrice)}
              </p>
            </div>
            <span
              className={`w-fit rounded-full px-3 py-1 text-xs font-black ${trade.buyerUserId === userId ? "bg-[#e7f0e2] text-[#43623c]" : "bg-[#f7ebc9] text-[#806419]"}`}
            >
              {trade.buyerUserId === userId ? "구매" : "판매"}
            </span>
          </div>
        ))}
        {!trades.length ? (
          <CommerceEmptyState text="완료된 거래가 없어요." />
        ) : null}
      </div>
      <PrivatePager page={page} totalPages={totalPages} onChange={onPage} />
    </section>
  );
}
