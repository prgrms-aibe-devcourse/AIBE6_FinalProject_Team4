import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import Charge from "./page";

const mocks = vi.hoisted(() => ({
  requestCharge: vi.fn(),
  reportPaymentFailure: vi.fn(),
  requestTossPayment: vi.fn(),
  routerPush: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mocks.routerPush }),
}));

vi.mock("@/features/payment/api", () => ({
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

const pendingPayment = {
  id: 91,
  userId: 7,
  cashAmount: 12_340,
  pointAmount: 12_340,
  status: "PENDING" as const,
  provider: "TOSS" as const,
  providerOrderId: "KWB-order-91",
  providerPaymentKey: null,
  approvedAt: null,
  createdAt: "2026-08-21T10:00:00",
  message: null,
};

describe("Charge", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    mocks.requestCharge.mockResolvedValue(pendingPayment);
    mocks.reportPaymentFailure.mockResolvedValue({
      ...pendingPayment,
      status: "FAILED",
    });
    mocks.requestTossPayment.mockResolvedValue(undefined);
  });

  it("직접 충전 정책과 1원 대 1포인트 금액을 표시한다", () => {
    render(<Charge />);

    expect(screen.getByText(/1원 = 1P/)).toHaveTextContent(
      "최소 1,000P · 최대 300,000P · 10P 단위",
    );
    expect(screen.getByText("1,000원")).toBeInTheDocument();
    expect(screen.getByText("1,000P")).toBeInTheDocument();
  });

  it("입력한 포인트 금액으로 충전 요청과 Toss 결제를 시작한다", async () => {
    render(<Charge />);
    fireEvent.change(screen.getByLabelText("충전할 포인트"), {
      target: { value: "12340" },
    });
    fireEvent.click(screen.getByRole("button", { name: "12,340P 충전하기" }));

    await waitFor(() => {
      expect(mocks.requestCharge).toHaveBeenCalledWith(
        "user-token",
        12_340,
        expect.stringMatching(/^charge-/),
      );
    });
    expect(mocks.requestTossPayment).toHaveBeenCalledWith(
      expect.objectContaining({
        orderId: pendingPayment.providerOrderId,
        orderName: "12,340P 충전",
        amount: 12_340,
      }),
    );
  });

  it.each([
    ["999", "최소 충전 금액은 1,000원이에요."],
    ["2801", "충전 금액은 10P 단위로 입력해 주세요."],
    ["300010", "최대 충전 금액은 300,000원이에요."],
  ])("정책에 맞지 않는 %sP 입력을 거부한다", async (amount, message) => {
    render(<Charge />);
    fireEvent.change(screen.getByLabelText("충전할 포인트"), {
      target: { value: amount },
    });
    fireEvent.submit(screen.getByLabelText("충전할 포인트").closest("form")!);

    expect(await screen.findByRole("alert")).toHaveTextContent(message);
    expect(mocks.requestCharge).not.toHaveBeenCalled();
    expect(mocks.requestTossPayment).not.toHaveBeenCalled();
  });

  it("서버 금액이 입력값과 다르면 결제를 실패 처리하고 Toss 창을 열지 않는다", async () => {
    mocks.requestCharge.mockResolvedValueOnce({
      ...pendingPayment,
      cashAmount: 12_350,
      pointAmount: 12_350,
    });

    render(<Charge />);
    fireEvent.change(screen.getByLabelText("충전할 포인트"), {
      target: { value: "12340" },
    });
    fireEvent.click(screen.getByRole("button", { name: "12,340P 충전하기" }));

    expect(
      await screen.findByText(
        "요청한 충전 금액과 결제 금액이 달라 결제를 진행하지 않았어요. 다시 시도해 주세요.",
      ),
    ).toBeInTheDocument();
    expect(mocks.reportPaymentFailure).toHaveBeenCalledWith(
      "user-token",
      pendingPayment.providerOrderId,
      "PAYMENT_AMOUNT_MISMATCH",
      `failure-${pendingPayment.providerOrderId}`,
    );
    expect(mocks.requestTossPayment).not.toHaveBeenCalled();
    expect(sessionStorage.getItem("kwb:toss-pending-order")).toBeNull();
  });
});
