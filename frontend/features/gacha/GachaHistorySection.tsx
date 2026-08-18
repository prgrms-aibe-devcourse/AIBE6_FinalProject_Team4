import Link from "next/link";
import { RARITY_LABEL } from "@/features/gacha/GachaCardPresentation";
import { GachaDrawPage, GachaRateData } from "@/lib/gacha-api";

interface GachaHistorySectionProps {
  history: GachaDrawPage | null;
  page: number;
  onPage: (page: number) => void;
}

export default function GachaHistorySection({
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
