import { WalletCard } from "@/features/card-market/CardMarketComponents";
import { formatPoint } from "@/features/commerce/presentation";
import { MarketWallet } from "@/features/card-market/api";

export type MarketTab = "market" | "sell" | "sent" | "received" | "trades";

interface CardMarketHeaderProps {
  tab: MarketTab;
  authenticated: boolean;
  wallet: MarketWallet | null;
  onTab: (tab: MarketTab) => void;
}

export default function CardMarketHeader({
  tab,
  authenticated,
  wallet,
  onTab,
}: CardMarketHeaderProps) {
  return (
    <>
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
            하이퍼와 골든 카드를 판매하거나 원하는 가격을 제안해 보세요. 소중한
            컬렉션을 안전한 규칙 안에서 거래할 수 있어요.
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

        {authenticated && wallet ? (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-1">
            <WalletCard
              icon="account_balance_wallet"
              label="거래 가능 포인트"
              value={formatPoint(wallet.paidPoint)}
              tone="gold"
            />
            <WalletCard
              icon="lock"
              label="가격 제안 보관 중"
              value={formatPoint(wallet.escrowedPaidPoint)}
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
            onClick={() => onTab(key)}
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
    </>
  );
}
