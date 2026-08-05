import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import AdminCouponPanel from "@/components/admin/AdminCouponPanel";
import AdminGachaOperationsPanel from "@/components/admin/AdminGachaOperationsPanel";

const mocks = vi.hoisted(() => ({
  getCards: vi.fn(),
  getExchangeProducts: vi.fn(),
  getDraws: vi.fn(),
  retryDraw: vi.fn(),
  showToast: vi.fn(),
  askConfirm: vi.fn(),
}));

vi.mock("@/lib/admin-card-api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/admin-card-api")>()),
  getAdminCards: mocks.getCards,
  getAdminExchangeProductOptions: mocks.getExchangeProducts,
}));
vi.mock("@/lib/admin-gacha-api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/admin-gacha-api")>()),
  getAdminGachaDraws: mocks.getDraws,
  retryAdminGachaDraw: mocks.retryDraw,
}));
vi.mock("@/lib/ui", () => ({
  useUI: () => ({ showToast: mocks.showToast, askConfirm: mocks.askConfirm }),
}));

describe("admin commerce panels", () => {
  beforeEach(() => vi.clearAllMocks());

  it("숨김 쿠폰과 연결 가능한 교환 상품을 함께 표시한다", async () => {
    mocks.getCards.mockResolvedValue([
      {
        id: 1,
        name: "수박 쿠폰",
        pointPrice: 300,
        exchangeProductId: 3,
        exchangeProductName: "제철 수박",
        requiredCountForExchange: 5,
        description: null,
        imageKey: null,
        imageUrl: null,
        status: "HIDDEN",
        createdAt: "2026-08-05T00:00:00",
        updatedAt: "2026-08-05T00:00:00",
      },
    ]);
    mocks.getExchangeProducts.mockResolvedValue([
      { id: 3, name: "제철 수박", stock: 8 },
    ]);

    render(<AdminCouponPanel accessToken="token" />);

    expect(await screen.findByText("수박 쿠폰")).toBeInTheDocument();
    expect(
      screen.getByRole("option", { name: "제철 수박 · 재고 8개" }),
    ).toBeInTheDocument();
    expect(screen.getByText("#1 · 숨김")).toBeInTheDocument();
  });

  it("수동 확인이 필요한 가챠에만 재시도 작업을 제공한다", async () => {
    mocks.getDraws.mockResolvedValue({
      content: [
        {
          drawId: 10,
          userId: 7,
          userNickname: "테스터",
          sourceType: "PURCHASE",
          sourceId: 20,
          status: "MANUAL_REVIEW",
          drawCount: 5,
          attemptCount: 4,
          lastErrorCode: "GACHA_MASTER_DATA_INVALID",
          nextRetryAt: null,
          resultViewedAt: null,
          completedAt: null,
          createdAt: "2026-08-05T00:00:00",
          updatedAt: "2026-08-05T00:00:00",
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });

    render(<AdminGachaOperationsPanel accessToken="token" />);

    expect(await screen.findByText("수동 확인 필요")).toBeInTheDocument();
    expect(screen.getByText("GACHA_MASTER_DATA_INVALID")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "재시도" })).toBeEnabled();
  });
});
