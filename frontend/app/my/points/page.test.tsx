import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import PointsHome from "@/app/my/points/page";

const mocks = vi.hoisted(() => ({
  getPointActivities: vi.fn(),
  refreshWallet: vi.fn(),
}));

vi.mock("@/features/point/api", () => ({
  getPointActivities: mocks.getPointActivities,
}));

vi.mock("@/lib/store", () => ({
  fmt: (value: number) => value.toLocaleString("ko-KR"),
  useStore: () => ({
    state: {
      accessToken: "user-token",
      wallet: { paid: 2000, free: 500 },
    },
    balance: 2500,
    walletLoading: false,
    walletLoaded: true,
    walletError: "",
    refreshWallet: mocks.refreshWallet,
  }),
}));

const activityPage = {
  content: [
    {
      id: 12,
      type: "PURCHASE" as const,
      refType: "ORDER" as const,
      refId: 10,
      adjustmentReason: null,
      amount: -1000,
      paidAmount: -700,
      freeAmount: -300,
      paidBalanceAfter: 2000,
      freeBalanceAfter: 500,
      createdAt: "2026-08-03T10:00:00",
    },
  ],
  number: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
  numberOfElements: 1,
  first: true,
  last: true,
  empty: false,
};

const gachaPurchaseActivityPage = {
  ...activityPage,
  content: [
    {
      ...activityPage.content[0],
      id: 13,
      refType: "GACHA_PURCHASE" as const,
      refId: 501,
      amount: -100,
      paidAmount: 0,
      freeAmount: -100,
    },
  ],
};

const adminAdjustmentActivityPage = {
  ...activityPage,
  content: [
    {
      ...activityPage.content[0],
      id: 14,
      type: "ADMIN_ADJUST" as const,
      refType: "ADMIN" as const,
      refId: 1,
      adjustmentReason: "OUTSTANDING_MEMBER" as const,
      amount: 1000,
      paidAmount: 0,
      freeAmount: 1000,
    },
  ],
};

describe("PointsHome", () => {
  beforeEach(() => {
    mocks.getPointActivities.mockReset().mockResolvedValue(activityPage);
    mocks.refreshWallet.mockReset();
  });

  it("혼합 결제를 한 건으로 표시하고 출처에 맞는 링크를 제공한다", async () => {
    render(<PointsHome />);

    expect(await screen.findByText("상품 주문 결제")).toBeInTheDocument();
    expect(screen.getByText("충전 포인트")).toBeInTheDocument();
    expect(screen.getByText("보너스 포인트")).toBeInTheDocument();
    expect(screen.getByText("-1,000P")).toBeInTheDocument();
    expect(screen.getByText(/충전 -700P/)).toHaveTextContent(
      "충전 -700P · 보너스 -300P",
    );
    expect(
      screen.getByRole("link", { name: "주문 내역 보기 →" }),
    ).toHaveAttribute("href", "/my/orders#order-10");
  });

  it("쿠폰 구매 필터를 거래 출처 조건으로 요청한다", async () => {
    render(<PointsHome />);
    await screen.findByText("상품 주문 결제");

    fireEvent.click(screen.getByRole("button", { name: "쿠폰 구매" }));

    await waitFor(() => {
      expect(mocks.getPointActivities).toHaveBeenLastCalledWith(
        expect.objectContaining({
          accessToken: "user-token",
          refType: "CARD_PURCHASE",
          type: undefined,
          page: 0,
        }),
      );
    });
  });

  it("가챠 카드팩 구매 내역과 관련 개봉 내역 링크를 표시한다", async () => {
    mocks.getPointActivities.mockResolvedValue(gachaPurchaseActivityPage);

    render(<PointsHome />);

    expect(await screen.findByText("가챠 카드팩 구매")).toBeInTheDocument();
    expect(
      screen.getByText("가챠 카드팩을 구매하는 데 포인트를 사용했어요."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "가챠 개봉 내역 보기 →" }),
    ).toHaveAttribute("href", "/gacha?tab=history");
  });

  it("가챠 카드팩 구매 필터를 거래 출처 조건으로 요청한다", async () => {
    render(<PointsHome />);
    await screen.findByText("상품 주문 결제");

    fireEvent.click(screen.getByRole("button", { name: "가챠 카드팩 구매" }));

    await waitFor(() => {
      expect(mocks.getPointActivities).toHaveBeenLastCalledWith(
        expect.objectContaining({
          accessToken: "user-token",
          refType: "GACHA_PURCHASE",
          type: undefined,
          page: 0,
        }),
      );
    });
  });

  it("관리자 조정 사유를 사용자 거래내역에 표시한다", async () => {
    mocks.getPointActivities.mockResolvedValue(adminAdjustmentActivityPage);

    render(<PointsHome />);

    expect(await screen.findByText("운영팀 포인트 지급")).toBeInTheDocument();
    expect(
      screen.getByText("운영팀에서 우수 회원 선정 사유로 포인트를 지급했어요."),
    ).toBeInTheDocument();
  });
});
