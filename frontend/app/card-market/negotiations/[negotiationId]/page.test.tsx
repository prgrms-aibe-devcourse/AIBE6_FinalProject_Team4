import { StrictMode } from "react";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getMyMarketNegotiation } from "@/features/card-market/api";
import MarketNegotiationDetailPage from "./page";

vi.mock("next/navigation", () => ({
  useParams: () => ({ negotiationId: "7" }),
}));

vi.mock("@/lib/store", () => ({
  useStore: () => ({
    hydrated: true,
    state: { accessToken: "token", user: { id: 5 } },
  }),
}));

vi.mock("@/features/card-market/api", async (importOriginal) => {
  const original =
    await importOriginal<typeof import("@/features/card-market/api")>();
  return { ...original, getMyMarketNegotiation: vi.fn() };
});

describe("MarketNegotiationDetailPage", () => {
  beforeEach(() => vi.clearAllMocks());

  it("Strict Mode 요청 정리 후 협상 상세를 정상 표시한다", async () => {
    vi.mocked(getMyMarketNegotiation)
      .mockRejectedValueOnce(new DOMException("aborted", "AbortError"))
      .mockResolvedValueOnce({
        id: 7,
        listingId: 2,
        buyerUserId: 5,
        buyerNickname: "구매자",
        sellerUserId: 3,
        cardId: 11,
        cardName: "별가루 딸기",
        imageUrl: null,
        askingPrice: 1000,
        status: "NEGOTIATING",
        turn: "SELLER",
        currentProposerType: "BUYER",
        currentPrice: 800,
        escrowedPaidPoint: 800,
        expiresAt: "2026-08-20T00:00:00",
        proposals: [],
      });

    render(
      <StrictMode>
        <MarketNegotiationDetailPage />
      </StrictMode>,
    );

    expect(
      await screen.findByRole("heading", { name: "별가루 딸기" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByText("가격 제안을 불러오지 못했어요."),
    ).not.toBeInTheDocument();
  });
});
