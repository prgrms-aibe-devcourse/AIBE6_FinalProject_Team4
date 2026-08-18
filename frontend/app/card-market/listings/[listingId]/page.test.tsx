import { StrictMode } from "react";
import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getMarketListing } from "@/features/card-market/api";
import MarketListingDetailPage from "./page";

vi.mock("next/navigation", () => ({
  useParams: () => ({ listingId: "2" }),
}));

vi.mock("@/features/card-market/api", async (importOriginal) => {
  const original =
    await importOriginal<typeof import("@/features/card-market/api")>();
  return { ...original, getMarketListing: vi.fn() };
});

describe("MarketListingDetailPage", () => {
  beforeEach(() => vi.clearAllMocks());

  it("Strict Mode 요청 정리의 AbortError를 오류 화면으로 표시하지 않는다", async () => {
    vi.mocked(getMarketListing)
      .mockRejectedValueOnce(new DOMException("aborted", "AbortError"))
      .mockResolvedValueOnce({
        id: 2,
        sellerUserId: 3,
        sellerNickname: "판매자",
        cardId: 11,
        goldenInstanceId: null,
        cardCode: "HYPER_11",
        cardName: "별가루 딸기",
        rarity: "HYPER_RARE",
        imageUrl: "https://assets.example/cards/11/card.png",
        assetType: "HYPER_RARE",
        askingPrice: 1000,
        status: "OPEN",
        activeOfferCount: 0,
        expiresAt: "2026-08-20T00:00:00",
        createdAt: "2026-08-13T00:00:00",
      });

    render(
      <StrictMode>
        <MarketListingDetailPage />
      </StrictMode>,
    );

    expect(
      await screen.findByRole("heading", { name: "별가루 딸기" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByText("매물 정보를 불러오지 못했어요."),
    ).not.toBeInTheDocument();

    fireEvent.click(
      screen.getByRole("button", {
        name: "별가루 딸기 일러스트 크게 보기",
      }),
    );
    expect(
      screen.getByRole("dialog", { name: "별가루 딸기 원본 일러스트" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("img", { name: "별가루 딸기 원본 일러스트" }),
    ).toBeInTheDocument();

    fireEvent.keyDown(window, { key: "Escape" });
    expect(
      screen.queryByRole("dialog", { name: "별가루 딸기 원본 일러스트" }),
    ).not.toBeInTheDocument();
  });
});
