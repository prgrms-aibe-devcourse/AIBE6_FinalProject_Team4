import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import ProductDetail from "./page";
import { getProduct } from "@/features/shop/api";
import { purchaseGachaPacks } from "@/lib/gacha-api";

const navigation = vi.hoisted(() => ({ push: vi.fn() }));
const ui = vi.hoisted(() => ({
  showToast: vi.fn(),
  askConfirm: vi.fn((options: { onOk?: () => void }) => options.onOk?.()),
}));
const store = vi.hoisted(() => ({
  state: {
    accessToken: "access-token",
    wallet: { free: 1_000, paid: 0 },
    cartCount: 0,
  },
  hydrated: true,
  set: vi.fn(),
  refreshWallet: vi.fn().mockResolvedValue(undefined),
  walletLoaded: true,
  walletLoading: false,
}));

vi.mock("next/navigation", () => ({
  useRouter: () => navigation,
}));

vi.mock("@/lib/store", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/lib/store")>();
  return {
    ...original,
    useStore: () => store,
  };
});

vi.mock("@/lib/ui", () => ({
  useUI: () => ui,
}));

vi.mock("@/features/shop/api", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/features/shop/api")>();
  return { ...original, getProduct: vi.fn() };
});

vi.mock("@/lib/gacha-api", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/lib/gacha-api")>();
  return { ...original, purchaseGachaPacks: vi.fn() };
});

const mockedGetProduct = vi.mocked(getProduct);
const mockedPurchase = vi.mocked(purchaseGachaPacks);

describe("shop gacha pack detail", () => {
  afterEach(cleanup);

  beforeEach(() => {
    vi.clearAllMocks();
    mockedGetProduct.mockResolvedValue({
      id: 9,
      name: "시즌 1 가챠 카드팩",
      category: "GACHA_PACK",
      pointPrice: 100,
      stock: 100,
      soldOut: false,
      description: "식물 캐릭터 카드 5장이 즉시 개봉됩니다.",
      imageUrl: "/cards/900001/pack.svg",
      plantGuide: null,
      createdAt: "2026-07-31T00:00:00",
      updatedAt: "2026-07-31T00:00:00",
    });
    mockedPurchase.mockResolvedValue({
      purchaseId: 501,
      productId: 9,
      productName: "시즌 1 가챠 카드팩",
      quantity: 1,
      unitPoint: 100,
      totalPoint: 100,
      usedFreePoint: 100,
      usedPaidPoint: 0,
      remainingBalance: 900,
      drawIds: [701],
    });
  });

  it("100P 팩을 상점에서 구매한 뒤 가챠 개봉 화면으로 이동한다", async () => {
    render(<ProductDetail params={{ id: "9" }} />);

    const purchaseButton = await screen.findByRole("button", {
      name: "100P로 1팩 구매하고 개봉하기",
    });
    expect(screen.getByText("팩은 한 번에 1개씩 구매할 수 있어요")).toBeInTheDocument();
    expect(
      screen.getByText(
        "보너스 포인트가 먼저 차감됩니다. 잔액 부족 시 충전포인트로 결제됩니다.",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText("수량")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "+" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "−" })).not.toBeInTheDocument();

    fireEvent.click(purchaseButton);

    await waitFor(() =>
      expect(mockedPurchase).toHaveBeenCalledWith(
        9,
        1,
        100,
        "access-token",
        expect.any(String),
      ),
    );
    await waitFor(() => expect(navigation.push).toHaveBeenCalledWith("/gacha/open/701"));
  });

  it("일반 상품에는 가챠 포인트 차감 안내를 표시하지 않는다", async () => {
    mockedGetProduct.mockResolvedValueOnce({
      id: 10,
      name: "방울토마토 키트",
      category: "KIT",
      pointPrice: 500,
      stock: 10,
      soldOut: false,
      description: "재배 키트입니다.",
      imageUrl: null,
      plantGuide: null,
      createdAt: "2026-07-31T00:00:00",
      updatedAt: "2026-07-31T00:00:00",
    });

    render(<ProductDetail params={{ id: "10" }} />);

    expect(await screen.findByText("방울토마토 키트")).toBeInTheDocument();
    expect(
      screen.queryByText(
        "보너스 포인트가 먼저 차감됩니다. 잔액 부족 시 충전포인트로 결제됩니다.",
      ),
    ).not.toBeInTheDocument();
  });

  it("모종 상품은 종 이름으로 AI 재배가이드 패널을 보여준다", async () => {
    mockedGetProduct.mockResolvedValueOnce({
      id: 11,
      name: "방울토마토 모종",
      category: "SEEDLING",
      pointPrice: 1200,
      stock: 12,
      soldOut: false,
      description: "햇빛이 드는 베란다에서 키우기 좋은 방울토마토 모종입니다.",
      imageUrl: null,
      plantGuide: { speciesName: "방울토마토" },
      createdAt: "2026-07-31T00:00:00",
      updatedAt: "2026-07-31T00:00:00",
    });

    render(<ProductDetail params={{ id: "11" }} />);

    expect(
      await screen.findByText("방울토마토 재배법을 AI가 정리해 드려요."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "재배 가이드 보기" }),
    ).toBeInTheDocument();
  });

  it("구매 사이에 가격이 바뀌면 차감하지 않고 최신 가격을 다시 표시한다", async () => {
    mockedPurchase.mockRejectedValueOnce(
      new (await import("@/lib/api")).ApiError(
        "GACHA_PRODUCT_PRICE_CHANGED",
        "가챠 팩 가격이 변경되었습니다. 최신 가격을 확인해 주세요.",
        409,
      ),
    );
    mockedGetProduct
      .mockResolvedValueOnce({
        id: 9,
        name: "시즌 1 가챠 카드팩",
        category: "GACHA_PACK",
        pointPrice: 100,
        stock: 100,
        soldOut: false,
        description: "식물 캐릭터 카드 5장이 즉시 개봉됩니다.",
        imageUrl: "/cards/900001/pack.svg",
        plantGuide: null,
        createdAt: "2026-07-31T00:00:00",
        updatedAt: "2026-07-31T00:00:00",
      })
      .mockResolvedValueOnce({
        id: 9,
        name: "시즌 1 가챠 카드팩",
        category: "GACHA_PACK",
        pointPrice: 120,
        stock: 100,
        soldOut: false,
        description: "식물 캐릭터 카드 5장이 즉시 개봉됩니다.",
        imageUrl: "/cards/900001/pack.svg",
        plantGuide: null,
        createdAt: "2026-07-31T00:00:00",
        updatedAt: "2026-08-12T00:00:00",
      });

    render(<ProductDetail params={{ id: "9" }} />);
    fireEvent.click(
      await screen.findByRole("button", { name: "100P로 1팩 구매하고 개봉하기" }),
    );

    expect(
      await screen.findByRole("button", { name: "120P로 1팩 구매하고 개봉하기" }),
    ).toBeInTheDocument();
    expect(ui.showToast).toHaveBeenCalledWith(
      "가챠 팩 가격이 변경되었습니다. 최신 가격을 확인해 주세요.",
      "err",
    );
    expect(navigation.push).not.toHaveBeenCalled();
  });

  it("포인트 부족으로 거절된 구매를 다시 시도할 때 새 멱등키를 사용한다", async () => {
    const { ApiError } = await import("@/lib/api");
    mockedPurchase
      .mockRejectedValueOnce(
        new ApiError(
          "POINT_INSUFFICIENT_BALANCE",
          "사용 가능한 포인트가 부족합니다.",
          422,
        ),
      )
      .mockResolvedValueOnce({
        purchaseId: 502,
        productId: 9,
        productName: "시즌 1 가챠 카드팩",
        quantity: 1,
        unitPoint: 100,
        totalPoint: 100,
        usedFreePoint: 100,
        usedPaidPoint: 0,
        remainingBalance: 900,
        drawIds: [702],
      });

    render(<ProductDetail params={{ id: "9" }} />);
    const purchaseButton = await screen.findByRole("button", {
      name: "100P로 1팩 구매하고 개봉하기",
    });

    fireEvent.click(purchaseButton);
    await waitFor(() => expect(mockedPurchase).toHaveBeenCalledTimes(1));
    await waitFor(() =>
      expect(ui.showToast).toHaveBeenCalledWith(
        "사용 가능한 포인트가 부족합니다.",
        "err",
      ),
    );

    fireEvent.click(purchaseButton);
    await waitFor(() => expect(mockedPurchase).toHaveBeenCalledTimes(2));

    expect(mockedPurchase.mock.calls[0][4]).not.toBe(
      mockedPurchase.mock.calls[1][4],
    );
    await waitFor(() =>
      expect(navigation.push).toHaveBeenCalledWith("/gacha/open/702"),
    );
  });
});
