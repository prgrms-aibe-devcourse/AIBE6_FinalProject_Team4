import Image from "next/image";
import Link from "next/link";
import GachaTitleBadge from "@/components/gacha/GachaTitleBadge";
import ProfileCosmeticFrame from "@/components/gacha/ProfileCosmeticFrame";
import {
  DisplayCard,
  RARITY_LABEL,
  RARITY_ORDER,
  RARITY_PANEL,
  RARITY_STYLE,
} from "@/features/gacha/GachaCardPresentation";
import {
  GachaCollectionCard,
  GachaCosmetic,
  GachaDrawPage,
  GachaRateData,
  GachaRarity,
} from "@/lib/gacha-api";

interface GachaCatalogSectionProps {
  cards: DisplayCard[];
  unlockedCount: number;
  totalCount: number;
  expandedRarities: Set<GachaRarity>;
  onToggleRarity: (rarity: GachaRarity) => void;
  onSelectCard: (card: DisplayCard) => void;
}

export function GachaCatalogSection({
  cards,
  unlockedCount,
  totalCount,
  expandedRarities,
  onToggleRarity,
  onSelectCard,
}: GachaCatalogSectionProps) {
  return (
    <section aria-labelledby="catalog-title">
      <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="text-xs font-bold uppercase tracking-[0.18em] text-brand">
            Collection book
          </p>
          <h2 id="catalog-title" className="mt-1 text-2xl font-black text-ink">
            카드 도감
          </h2>
          <p className="mt-1 text-sm text-sub">
            등급을 눌러 펼쳐보세요. 모든 도감은 처음에는 접혀 있습니다.
          </p>
        </div>
        <div className="rounded-full bg-white px-4 py-2 text-sm font-bold text-sub shadow-sm">
          해금 {unlockedCount} / {totalCount}
        </div>
      </div>

      <div className="space-y-3">
        {RARITY_ORDER.map((rarity) => {
          const rarityCards = cards.filter((card) => card.rarity === rarity);
          const rarityUnlockedCount = rarityCards.filter(
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
                onClick={() => onToggleRarity(rarity)}
                className="flex w-full items-center justify-between gap-4 px-5 py-4 text-left"
              >
                <span className="flex items-center gap-3">
                  <span
                    className={`rounded-full px-3 py-1.5 text-xs font-black ${RARITY_STYLE[rarity]}`}
                  >
                    {RARITY_LABEL[rarity]}
                  </span>
                  <span className="text-sm font-bold text-sub">
                    {rarityUnlockedCount}/{rarityCards.length} 해금
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
                        onClick={() => onSelectCard(card)}
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
  );
}

interface GachaMineSectionProps {
  cards: GachaCollectionCard[];
  rarity: GachaRarity | "ALL";
  ownedUniqueCount: number;
  ownedTotalCount: number;
  highestOwnedRarity: GachaRarity | null;
  dismantleableTotal: number;
  shardBalance: number;
  nickname?: string;
  title: GachaCosmetic | null;
  border: GachaCosmetic | null;
  onRarity: (rarity: GachaRarity | "ALL") => void;
  onSelectCard: (card: GachaCollectionCard) => void;
  onOpenWorkshop: () => void;
}

export function GachaMineSection({
  cards,
  rarity,
  ownedUniqueCount,
  ownedTotalCount,
  highestOwnedRarity,
  dismantleableTotal,
  shardBalance,
  nickname,
  title,
  border,
  onRarity,
  onSelectCard,
  onOpenWorkshop,
}: GachaMineSectionProps) {
  return (
    <section aria-labelledby="my-gallery-title">
      <div className="relative mb-6 overflow-hidden rounded-[26px] bg-[#151b15] p-6 text-white shadow-xl sm:p-8">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_80%_20%,rgba(201,170,71,.28),transparent_40%)]" />
        <div className="relative grid gap-6 lg:grid-cols-[minmax(0,1fr)_auto] lg:items-start">
          <div>
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
                  <p className="text-[10px] font-bold text-white/45">{label}</p>
                  <p className="mt-1 text-sm font-black text-[#f3d77c] sm:text-base">
                    {value}
                  </p>
                </div>
              ))}
            </div>
          </div>

          <div className="flex min-w-0 items-center gap-4 rounded-[22px] border border-[#d4b85f]/30 bg-black/25 p-4 backdrop-blur-sm sm:min-w-[285px] sm:p-5">
            <ProfileCosmeticFrame
              borderCode={border?.code}
              className="h-[76px] w-[76px]"
            >
              <div className="flex h-full w-full items-center justify-center rounded-full bg-gradient-to-br from-[#b8d992] to-[#679849] text-2xl font-black text-white">
                {nickname?.charAt(0) ?? "키"}
              </div>
            </ProfileCosmeticFrame>
            <div className="min-w-0">
              <p className="text-[10px] font-black uppercase tracking-[0.18em] text-[#d4b85f]">
                Collector identity
              </p>
              <p className="mt-1 truncate text-lg font-black text-white">
                {nickname ?? "컬렉터"}
              </p>
              {title ? (
                <GachaTitleBadge
                  code={title.code}
                  name={title.name}
                  className="mt-2 max-w-[175px]"
                />
              ) : (
                <p className="mt-2 text-xs font-bold text-white/40">
                  장착한 칭호가 없어요
                </p>
              )}
            </div>
          </div>
        </div>
      </div>

      <button
        type="button"
        onClick={onOpenWorkshop}
        className="group relative mb-6 flex w-full items-center justify-between gap-4 overflow-hidden rounded-[24px] border border-[#b59a48]/35 bg-gradient-to-r from-[#18251d] via-[#2d4632] to-[#675523] px-5 py-5 text-left text-white shadow-[0_18px_44px_-28px_rgba(26,56,34,.9)] transition hover:-translate-y-0.5 hover:shadow-[0_22px_50px_-25px_rgba(26,56,34,.95)] sm:px-6"
      >
        <span className="absolute -right-8 -top-12 h-36 w-36 rounded-full bg-[#e4c052]/20 blur-2xl transition group-hover:bg-[#e4c052]/30" />
        <span className="relative flex min-w-0 items-center gap-4">
          <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl border border-white/10 bg-white/10 text-[#efd476]">
            <span className="material-symbols-outlined text-[26px]">
              recycling
            </span>
          </span>
          <span className="min-w-0">
            <span className="block text-xs font-black uppercase tracking-[0.16em] text-[#d7bd6e]">
              Collector atelier
            </span>
            <span className="mt-1 block text-lg font-black">
              조각 공방 열기
            </span>
            <span className="mt-1 block truncate text-xs text-white/55 sm:text-sm">
              중복 {dismantleableTotal}장 · 보유 조각 {shardBalance}개
            </span>
          </span>
        </span>
        <span className="material-symbols-outlined relative text-[#efd476] transition-transform group-hover:translate-x-1">
          arrow_forward
        </span>
      </button>

      <div className="mb-5 flex gap-2 overflow-x-auto pb-1">
        {(["ALL", ...RARITY_ORDER] as const).map((item) => (
          <button
            key={item}
            type="button"
            onClick={() => onRarity(item)}
            className={`whitespace-nowrap rounded-full px-3.5 py-2 text-xs font-black ${
              rarity === item
                ? "bg-[#20281f] text-[#f3d77c]"
                : "border border-line bg-white text-sub"
            }`}
          >
            {item === "ALL" ? "전체" : RARITY_LABEL[item]}
          </button>
        ))}
      </div>

      {cards.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-line bg-white px-6 py-16 text-center">
          <span className="material-symbols-outlined text-5xl text-[#aeb7aa]">
            playing_cards
          </span>
          <p className="mt-3 font-black text-ink">전시할 카드가 아직 없어요.</p>
          <p className="mt-1 text-sm text-sub">
            오늘의 일지를 작성하고 첫 카드팩을 받아보세요.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
          {cards.map((card) => (
            <div
              key={card.id}
              className="group relative aspect-[1122/1402] rounded-[20px] bg-[#20261f]"
            >
              <button
                type="button"
                onClick={() => onSelectCard(card)}
                aria-label={`${card.name} 원본 일러스트 크게 보기`}
                className="relative block h-full w-full overflow-hidden rounded-[14px] bg-black"
              >
                {card.imageUrl ? (
                  <Image
                    src={card.imageUrl}
                    alt={`${card.name} 카드 일러스트`}
                    fill
                    sizes="(max-width: 640px) 50vw, (max-width: 1024px) 33vw, 20vw"
                    className="object-contain transition duration-300 group-hover:scale-[1.035]"
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
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

interface GachaHistorySectionProps {
  history: GachaDrawPage | null;
  page: number;
  onPage: (page: number) => void;
}

export function GachaHistorySection({
  history,
  page,
  onPage,
}: GachaHistorySectionProps) {
  return (
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
          {history.totalPages > 1 ? (
            <div className="flex items-center justify-center gap-3 pt-4">
              <button
                type="button"
                disabled={page <= 0}
                onClick={() => onPage(page - 1)}
                className="rounded-xl border border-line bg-white px-4 py-2 font-bold disabled:opacity-40"
              >
                이전
              </button>
              <span className="text-sm font-bold text-sub">
                {page + 1} / {history.totalPages}
              </span>
              <button
                type="button"
                disabled={page + 1 >= history.totalPages}
                onClick={() => onPage(page + 1)}
                className="rounded-xl border border-line bg-white px-4 py-2 font-bold disabled:opacity-40"
              >
                다음
              </button>
            </div>
          ) : null}
        </div>
      ) : (
        <div className="rounded-2xl bg-white p-12 text-center text-sub">
          아직 개봉 내역이 없습니다.
        </div>
      )}
    </section>
  );
}

export function GachaRatesSection({ rates }: { rates: GachaRateData }) {
  return (
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
  );
}
