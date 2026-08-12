import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import Charge from "./page";
import { ApiError } from "@/lib/api";

const mocks = vi.hoisted(() => ({
  getChargeProducts: vi.fn(),
  requestCharge: vi.fn(),
  reportPaymentFailure: vi.fn(),
  requestTossPayment: vi.fn(),
  routerPush: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mocks.routerPush }),
}));

vi.mock("@/features/payment/api", () => ({
  getChargeProducts: mocks.getChargeProducts,
  requestCharge: mocks.requestCharge,
  reportPaymentFailure: mocks.reportPaymentFailure,
}));

vi.mock("@/features/payment/toss-payment", () => ({
  getTossPaymentErrorCode: () => "UNKNOWN_PAYMENT_ERROR",
  requestTossPayment: mocks.requestTossPayment,
  TOSS_PENDING_ORDER_STORAGE_KEY: "kwb:toss-pending-order",
}));

vi.mock("@/lib/store", () => ({
  fmt: (value: number) => value.toLocaleString("ko-KR"),
  useStore: () => ({
    state: {
      accessToken: "user-token",
      user: {
        email: "green@example.com",
        nickname: "초록이",
      },
    },
  }),
}));

const product = {
  id: 42,
  version: 0,
  name: "여름 한정 보너스 충전",
  price: 12_345,
  pointAmount: 15_000,
  isActive: true,
};

const pendingPayment = {
  id: 91,
  userId: 7,
  chargeProductId: product.id,
  chargeProductName: product.name,
  cashAmount: product.price,
  pointAmount: product.pointAmount,
  status: "PENDING" as const,
  provider: "TOSS" as const,
  providerOrderId: "KWB-order-91",
  providerPaymentKey: null,
  approvedAt: null,
  createdAt: "2026-08-12T10:00:00",
  message: null,
};

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((nextResolve, nextReject) => {
    resolve = nextResolve;
    reject = nextReject;
  });
  return { promise, resolve, reject };
}

describe("Charge", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    mocks.getChargeProducts.mockResolvedValue([product]);
    mocks.requestCharge.mockResolvedValue(pendingPayment);
    mocks.requestTossPayment.mockResolvedValue(undefined);
  });

  it("API가 반환한 상품명과 결제 금액 및 지급 포인트를 표시한다", async () => {
    render(<Charge />);

    expect(await screen.findByText(product.name)).toBeInTheDocument();
    expect(screen.getByText("12,345원")).toBeInTheDocument();
    expect(screen.getByText("15,000P")).toBeInTheDocument();
  });

  it("선택한 상품 ID로 충전 요청을 생성한다", async () => {
    render(<Charge />);

    fireEvent.click(
      await screen.findByRole("button", { name: new RegExp(product.name) }),
    );

    await waitFor(() => {
      expect(mocks.requestCharge).toHaveBeenCalledWith(
        "user-token",
        product.id,
        expect.stringMatching(/^charge-/),
      );
    });
    expect(mocks.requestTossPayment).toHaveBeenCalledWith(
      expect.objectContaining({
        orderId: pendingPayment.providerOrderId,
        orderName: pendingPayment.chargeProductName,
        amount: pendingPayment.cashAmount,
      }),
    );
  });

  it("상품 조회가 끝나기 전에는 로딩 상태를 표시한다", () => {
    const request = deferred<(typeof product)[]>();
    mocks.getChargeProducts.mockReturnValue(request.promise);

    render(<Charge />);

    expect(
      screen.getByText("충전 상품을 불러오고 있어요."),
    ).toBeInTheDocument();
  });

  it("판매 중인 상품이 없으면 빈 목록 상태를 표시한다", async () => {
    mocks.getChargeProducts.mockResolvedValue([]);

    render(<Charge />);

    expect(
      await screen.findByText("지금은 구매할 수 있는 충전 상품이 없어요."),
    ).toBeInTheDocument();
  });

  it("조회 오류를 표시하고 다시 시도하면 상품을 다시 불러온다", async () => {
    mocks.getChargeProducts
      .mockRejectedValueOnce(
        new ApiError(
          "PAYMENT_PRODUCTS_UNAVAILABLE",
          "상품 조회에 실패했어요.",
          503,
        ),
      )
      .mockResolvedValueOnce([product]);

    render(<Charge />);

    expect(
      await screen.findByText("상품 조회에 실패했어요."),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));

    expect(await screen.findByText(product.name)).toBeInTheDocument();
    expect(mocks.getChargeProducts).toHaveBeenCalledTimes(2);
  });

  it("결제 요청 시 상품 견적이 달라지면 Toss 창을 열지 않고 최신 목록을 표시한다", async () => {
    const latestProduct = {
      ...product,
      version: 1,
      price: 13_000,
      pointAmount: 16_000,
    };
    mocks.getChargeProducts
      .mockResolvedValueOnce([product])
      .mockResolvedValueOnce([latestProduct]);
    mocks.requestCharge.mockResolvedValueOnce({
      ...pendingPayment,
      cashAmount: latestProduct.price,
      pointAmount: latestProduct.pointAmount,
    });
    mocks.reportPaymentFailure.mockResolvedValueOnce({
      ...pendingPayment,
      status: "FAILED",
    });

    render(<Charge />);
    fireEvent.click(
      await screen.findByRole("button", { name: new RegExp(product.name) }),
    );

    expect(
      await screen.findByText(
        "충전 상품의 금액 또는 지급 포인트가 변경됐어요. 최신 상품을 확인한 뒤 다시 결제해 주세요.",
      ),
    ).toBeInTheDocument();
    expect(mocks.requestTossPayment).not.toHaveBeenCalled();
    expect(mocks.reportPaymentFailure).toHaveBeenCalledWith(
      "user-token",
      pendingPayment.providerOrderId,
      "PAYMENT_QUOTE_CHANGED",
      `failure-${pendingPayment.providerOrderId}`,
    );
    expect(await screen.findByText("13,000원")).toBeInTheDocument();
    expect(screen.getByText("16,000P")).toBeInTheDocument();
    expect(mocks.getChargeProducts).toHaveBeenCalledTimes(2);
    expect(sessionStorage.getItem("kwb:toss-pending-order")).toBeNull();
  });

  it("견적 변경 결제의 실패 정리가 불명확하면 주문 ID를 보존하고 정리 재시도 화면으로 이동한다", async () => {
    mocks.requestCharge.mockResolvedValueOnce({
      ...pendingPayment,
      cashAmount: product.price + 1000,
    });
    mocks.reportPaymentFailure.mockRejectedValueOnce(new TypeError("network"));

    render(<Charge />);
    fireEvent.click(
      await screen.findByRole("button", { name: new RegExp(product.name) }),
    );

    await waitFor(() =>
      expect(mocks.routerPush).toHaveBeenCalledWith(
        expect.stringContaining("code=PAYMENT_QUOTE_CHANGED"),
      ),
    );
    expect(sessionStorage.getItem("kwb:toss-pending-order")).toBe(
      pendingPayment.providerOrderId,
    );
    expect(mocks.reportPaymentFailure).toHaveBeenCalledWith(
      "user-token",
      pendingPayment.providerOrderId,
      "PAYMENT_QUOTE_CHANGED",
      `failure-${pendingPayment.providerOrderId}`,
    );
    expect(mocks.requestTossPayment).not.toHaveBeenCalled();
    expect(mocks.getChargeProducts).toHaveBeenCalledTimes(1);
  });
});
