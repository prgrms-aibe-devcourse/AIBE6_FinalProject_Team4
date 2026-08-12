import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import AdminCardMarketRevenuePanel from "./AdminCardMarketRevenuePanel";
import { getAdminCardMarketRevenue } from "@/lib/admin-card-market-api";

vi.mock("@/lib/admin-card-market-api", () => ({
  getAdminCardMarketRevenue: vi.fn(),
}));

describe("AdminCardMarketRevenuePanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getAdminCardMarketRevenue).mockResolvedValue({
      totalTradeCount: 1,
      totalTradePoint: 1000,
      totalFeePoint: 200,
      totalSellerReceivedPoint: 800,
      content: [
        {
          tradeId: 1,
          listingId: 2,
          cardName: "골든 옥수수",
          tradeType: "BUY_NOW",
          sellerUserId: 3,
          sellerNickname: "판매자",
          buyerUserId: 4,
          buyerNickname: "구매자",
          tradePoint: 1000,
          feePoint: 200,
          sellerReceivedPoint: 800,
          completedAt: "2026-08-12T00:00:00",
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
  });

  it("누적 수수료와 거래별 수익을 표시한다", async () => {
    render(<AdminCardMarketRevenuePanel accessToken="admin-token" />);

    expect(await screen.findByText("골든 옥수수")).toBeInTheDocument();
    expect(screen.getByText("누적 플랫폼 수익")).toBeInTheDocument();
    expect(screen.getAllByText("200P").length).toBeGreaterThanOrEqual(1);
    await waitFor(() =>
      expect(getAdminCardMarketRevenue).toHaveBeenCalledWith(
        "admin-token",
        expect.objectContaining({ page: 0 }),
      ),
    );
  });
});
