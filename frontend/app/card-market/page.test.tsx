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
  createMarketListing,
  buyMarketListing,
  getMarketListings,
  getMarketSellableCards,
  getMarketWallet,
  getMyMarketListings,
  getMyMarketNegotiations,
  getMyMarketTrades,
} from "@/features/card-market/api";

const auth = vi.hoisted(() => ({
  accessToken: null as string | null,
  user: null as { id: number } | null,
}));
const refreshWallet = vi.hoisted(() => vi.fn());
const navigation = vi.hoisted(() => ({
  replace: vi.fn(),
  params: new URLSearchParams(),
}));
const ui = vi.hoisted(() => ({ askConfirm: vi.fn() }));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: navigation.replace }),
  useSearchParams: () => navigation.params,
}));

vi.mock("@/lib/ui", () => ({
  useUI: () => ({ askConfirm: ui.askConfirm }),
}));

vi.mock("@/lib/store", () => ({
  useStore: () => ({
    state: auth,
    hydrated: true,
    refreshWallet,
  }),
}));

vi.mock("@/features/card-market/api", async (importOriginal) => {
  const original =
    await importOriginal<typeof import("@/features/card-market/api")>();
  return {
    ...original,
    getMarketListings: vi.fn(),
    getMarketWallet: vi.fn(),
    getMarketSellableCards: vi.fn(),
    getMyMarketListings: vi.fn(),
    getMyMarketNegotiations: vi.fn(),
    getMyMarketTrades: vi.fn(),
    createMarketNegotiation: vi.fn(),
    createMarketListing: vi.fn(),
    buyMarketListing: vi.fn(),
  };
});

const mockedListings = vi.mocked(getMarketListings);
const mockedWallet = vi.mocked(getMarketWallet);
const mockedSellable = vi.mocked(getMarketSellableCards);
const mockedMyListings = vi.mocked(getMyMarketListings);
const mockedNegotiations = vi.mocked(getMyMarketNegotiations);
const mockedTrades = vi.mocked(getMyMarketTrades);
const mockedCreateOffer = vi.mocked(createMarketNegotiation);
const mockedCreateListing = vi.mocked(createMarketListing);
const mockedBuy = vi.mocked(buyMarketListing);

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
    navigation.params = new URLSearchParams();
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
    mockedMyListings.mockResolvedValue({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });
    mockedNegotiations.mockResolvedValue({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });
    mockedTrades.mockResolvedValue({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });
    mockedCreateOffer.mockResolvedValue({} as never);
    mockedCreateListing.mockResolvedValue({} as never);
    mockedBuy.mockResolvedValue({} as never);
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

  it("판매가 이상으로 입력한 가격 제안은 전송하지 않는다", async () => {
    auth.accessToken = "access-token";
    auth.user = { id: 7 };
    render(<CardMarketPage />);

    fireEvent.click(await screen.findByRole("button", { name: "거래하기" }));
    const offerButton = screen.getByRole("button", { name: "제안" });
    fireEvent.change(screen.getByPlaceholderText("최소 100P"), {
      target: { value: "1000" },
    });

    expect(offerButton).toBeDisabled();
    fireEvent.click(offerButton);
    expect(mockedCreateOffer).not.toHaveBeenCalled();
  });

  it("바로 구매 전 취소 불가 확인을 받는다", async () => {
    auth.accessToken = "access-token";
    auth.user = { id: 7 };
    render(<CardMarketPage />);

    fireEvent.click(await screen.findByRole("button", { name: "거래하기" }));
    fireEvent.click(screen.getByRole("button", { name: /바로 구매/ }));

    expect(ui.askConfirm).toHaveBeenCalledWith(
      expect.objectContaining({
        title: "이 카드를 바로 구매할까요?",
        ok: "구매 확정",
      }),
    );
    expect(mockedBuy).not.toHaveBeenCalled();
  });

  it("매물 페이지와 등급 필터를 URL에 보존한다", async () => {
    mockedListings.mockResolvedValue({
      content: [listing],
      page: 0,
      size: 20,
      totalElements: 21,
      totalPages: 2,
    });
    render(<CardMarketPage />);

    fireEvent.click(await screen.findByRole("button", { name: "다음" }));
    expect(navigation.replace).toHaveBeenCalledWith("/card-market?page=2", {
      scroll: false,
    });

    fireEvent.click(await screen.findByRole("button", { name: "골든" }));
    expect(navigation.replace).toHaveBeenCalledWith(
      "/card-market?rarity=GOLDEN_RARE",
      { scroll: false },
    );
  });

  it("골든 판매 개체를 실제 카드명과 고유 ID로 표시한다", async () => {
    auth.accessToken = "access-token";
    auth.user = { id: 7 };
    mockedSellable.mockResolvedValue([
      {
        cardId: 43,
        cardName: "황금 옥수수",
        rarity: "GOLDEN_RARE",
        imageUrl: null,
        ownedCount: 1,
        sellableCount: 1,
        goldenInstances: [{ id: 731, originRank: 12, listed: false }],
      },
    ]);
    render(<CardMarketPage />);

    fireEvent.click(await screen.findByRole("button", { name: /내 판매/ }));

    expect(
      await screen.findByRole("option", { name: "황금 옥수수 · 개체 #731" }),
    ).toBeInTheDocument();
    expect(screen.queryByText("골든 #12")).not.toBeInTheDocument();
  });

  it("판매 등록 전에 가격과 수수료 및 예상 정산액을 안내한다", async () => {
    auth.accessToken = "access-token";
    auth.user = { id: 7 };
    mockedSellable.mockResolvedValue([
      {
        cardId: 11,
        cardName: "하이퍼 토마토",
        rarity: "HYPER_RARE",
        imageUrl: null,
        ownedCount: 2,
        sellableCount: 1,
        goldenInstances: [],
      },
    ]);
    render(<CardMarketPage />);

    fireEvent.click(await screen.findByRole("button", { name: /내 판매/ }));
    expect(
      await screen.findByText(/판매 가능 가격 100P ~ 99,999,999P/),
    ).toBeInTheDocument();
    fireEvent.change(screen.getByPlaceholderText("판매 가격 100P 이상"), {
      target: { value: "1000" },
    });
    fireEvent.click(screen.getByRole("button", { name: "등록" }));

    expect(ui.askConfirm).toHaveBeenCalledWith(
      expect.objectContaining({
        title: "판매 등록을 확인해 주세요",
        body: expect.stringContaining(
          "판매 카드: 하이퍼 토마토. 등록 가격은 1,000P이며, 거래 완료 시 수수료 20%를 제외한 800P",
        ),
        ok: "판매 등록",
      }),
    );
    expect(mockedCreateListing).not.toHaveBeenCalled();
  });

  it("골든 카드는 일반 확인 뒤 위험 확인을 한 번 더 받는다", async () => {
    auth.accessToken = "access-token";
    auth.user = { id: 7 };
    mockedSellable.mockResolvedValue([
      {
        cardId: 43,
        cardName: "황금 옥수수",
        rarity: "GOLDEN_RARE",
        imageUrl: null,
        ownedCount: 1,
        sellableCount: 1,
        goldenInstances: [{ id: 731, originRank: 12, listed: false }],
      },
    ]);
    render(<CardMarketPage />);

    fireEvent.click(await screen.findByRole("button", { name: /내 판매/ }));
    fireEvent.change(await screen.findByRole("combobox"), {
      target: { value: "731" },
    });
    fireEvent.change(screen.getByPlaceholderText("판매 가격 100P 이상"), {
      target: { value: "1000" },
    });
    fireEvent.click(screen.getByRole("button", { name: "등록" }));

    const firstConfirm = ui.askConfirm.mock.calls[0][0];
    firstConfirm.onOk();

    await waitFor(() => expect(ui.askConfirm).toHaveBeenCalledTimes(2));
    expect(ui.askConfirm).toHaveBeenLastCalledWith(
      expect.objectContaining({
        title: "귀중한 골든 카드를 정말 판매할까요?",
        ok: "위험을 확인하고 등록",
        danger: true,
        body: expect.stringContaining("황금 옥수수 · 개체 #731"),
      }),
    );
    expect(mockedCreateListing).not.toHaveBeenCalled();
  });
});
