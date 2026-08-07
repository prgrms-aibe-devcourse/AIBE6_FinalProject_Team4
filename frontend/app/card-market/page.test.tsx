import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import CardMarketPage from "./page";
import {
  createMarketNegotiation,
  getMarketListings,
  getMarketSellableCards,
  getMarketWallet,
  getMyMarketListings,
  getMyMarketNegotiations,
  getMyMarketTrades,
} from "@/lib/card-market-api";

const auth = vi.hoisted(() => ({
  accessToken: null as string | null,
  user: null as { id: number } | null,
}));
const refreshWallet = vi.hoisted(() => vi.fn());

vi.mock("@/lib/store", () => ({
  useStore: () => ({
    state: auth,
    hydrated: true,
    refreshWallet,
  }),
}));

vi.mock("@/lib/card-market-api", async (importOriginal) => {
  const original =
    await importOriginal<typeof import("@/lib/card-market-api")>();
  return {
    ...original,
    getMarketListings: vi.fn(),
    getMarketWallet: vi.fn(),
    getMarketSellableCards: vi.fn(),
    getMyMarketListings: vi.fn(),
    getMyMarketNegotiations: vi.fn(),
    getMyMarketTrades: vi.fn(),
    createMarketNegotiation: vi.fn(),
  };
});

const mockedListings = vi.mocked(getMarketListings);
const mockedWallet = vi.mocked(getMarketWallet);
const mockedSellable = vi.mocked(getMarketSellableCards);
const mockedMyListings = vi.mocked(getMyMarketListings);
const mockedNegotiations = vi.mocked(getMyMarketNegotiations);
const mockedTrades = vi.mocked(getMyMarketTrades);
const mockedCreateOffer = vi.mocked(createMarketNegotiation);

const listing = {
  id: 17,
  sellerUserId: 3,
  sellerNickname: "판매자",
  cardId: 11,
  goldenInstanceId: null,
  cardCode: "HYPER_11",
  cardName: "하이퍼 토마토",
  rarity: "HYPER_RARE" as const,
  imageUrl: null,
  assetType: "HYPER_RARE" as const,
  askingPrice: 1000,
  status: "OPEN" as const,
  activeOfferCount: 1,
  expiresAt: "2026-08-13T00:00:00",
  createdAt: "2026-08-06T00:00:00",
};

describe("CardMarketPage", () => {
  afterEach(cleanup);

  beforeEach(() => {
    vi.clearAllMocks();
    auth.accessToken = null;
    auth.user = null;
    mockedListings.mockResolvedValue({
      content: [listing],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    mockedWallet.mockResolvedValue({
      paidPoint: 1000,
      freePoint: 500,
      escrowedPaidPoint: 300,
      paidPointGuide: "유상 포인트만 사용",
      freePointGuide: "무상 포인트 사용 불가",
    });
    mockedSellable.mockResolvedValue([]);
    mockedMyListings.mockResolvedValue([]);
    mockedNegotiations.mockResolvedValue([]);
    mockedTrades.mockResolvedValue([]);
    mockedCreateOffer.mockResolvedValue({} as never);
    refreshWallet.mockResolvedValue(undefined);
  });

  it("비로그인 사용자는 판매 목록을 보고 개인 탭 진입 안내를 받는다", async () => {
    render(<CardMarketPage />);

    expect(
      await screen.findByRole("heading", { name: "카드 거래소" }),
    ).toBeInTheDocument();
    expect(await screen.findByText("하이퍼 토마토")).toBeInTheDocument();
    expect(screen.queryByText("거래 가능 포인트")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: /가챠로 돌아가기/ }),
    ).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /내 판매/ }));

    expect(
      screen.getByText("로그인하면 카드 판매와 가격 협상을 이용할 수 있어요."),
    ).toBeInTheDocument();
    expect(mockedWallet).not.toHaveBeenCalled();
  });

  it("로그인 사용자는 거래 가능 금액과 제안 보관 금액을 확인한다", async () => {
    auth.accessToken = "access-token";
    auth.user = { id: 7 };

    render(<CardMarketPage />);

    expect(await screen.findByText("거래 가능 포인트")).toBeInTheDocument();
    expect(screen.getByText("가격 제안 보관 중")).toBeInTheDocument();
    expect(
      screen.getByRole("heading", {
        name: "왜 충전한 포인트만 사용할까요?",
      }),
    ).toBeInTheDocument();
    expect(screen.getAllByText("1,000P").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("300P")).toBeInTheDocument();
    expect(screen.queryByText("500P")).not.toBeInTheDocument();
    expect(screen.queryByText(/유상|무상/)).not.toBeInTheDocument();
  });

  it("거래 모달에서 환불 불가를 안내하고 가격 제안을 전송한다", async () => {
    auth.accessToken = "access-token";
    auth.user = { id: 7 };
    render(<CardMarketPage />);

    fireEvent.click(await screen.findByRole("button", { name: "거래하기" }));
    expect(
      screen.getByText(/완료된 거래는 취소할 수 없습니다/),
    ).toBeInTheDocument();
    fireEvent.change(screen.getByPlaceholderText("최소 100P"), {
      target: { value: "800" },
    });
    fireEvent.click(screen.getByRole("button", { name: "제안" }));

    await waitFor(() =>
      expect(mockedCreateOffer).toHaveBeenCalledWith(
        17,
        800,
        "PRICE_ADJUST_REQUEST",
        "access-token",
      ),
    );
    expect(
      await screen.findByText("판매자에게 가격을 제안했어요."),
    ).toBeInTheDocument();
  });
});
