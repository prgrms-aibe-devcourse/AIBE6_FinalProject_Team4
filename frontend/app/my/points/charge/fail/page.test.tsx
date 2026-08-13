import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import TossPaymentFailPage from "./page";

const mocks = vi.hoisted(() => ({
  reportPaymentFailure: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useSearchParams: () =>
    new URLSearchParams(
      "code=PAYMENT_QUOTE_CHANGED&message=%EA%B2%AC%EC%A0%81%EC%9D%B4+%EB%B3%80%EA%B2%BD%EB%90%90%EC%96%B4%EC%9A%94.",
    ),
}));

vi.mock("@/features/payment/api", () => ({
  reportPaymentFailure: mocks.reportPaymentFailure,
}));

vi.mock("@/features/payment/toss-payment", () => ({
  TOSS_PENDING_ORDER_STORAGE_KEY: "kwb:toss-pending-order",
}));

vi.mock("@/lib/store", () => ({
  useStore: () => ({
    hydrated: true,
    state: { accessToken: "user-token" },
  }),
}));

describe("TossPaymentFailPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    sessionStorage.setItem("kwb:toss-pending-order", "KWB-order-91");
    mocks.reportPaymentFailure.mockResolvedValue({ status: "FAILED" });
  });

  it("보존한 주문 ID와 견적 변경 코드로 실패 정리를 재시도한다", async () => {
    render(<TossPaymentFailPage />);

    await waitFor(() =>
      expect(mocks.reportPaymentFailure).toHaveBeenCalledWith(
        "user-token",
        "KWB-order-91",
        "PAYMENT_QUOTE_CHANGED",
        "failure-KWB-order-91",
      ),
    );
    expect(
      await screen.findByText("결제 내역에도 실패로 반영됐어요."),
    ).toBeInTheDocument();
    expect(sessionStorage.getItem("kwb:toss-pending-order")).toBeNull();
  });

  it("자동 정리에 실패하면 주문 ID를 유지하고 버튼으로 다시 정리할 수 있다", async () => {
    mocks.reportPaymentFailure
      .mockRejectedValueOnce(new TypeError("network"))
      .mockResolvedValueOnce({ status: "FAILED" });

    render(<TossPaymentFailPage />);

    fireEvent.click(
      await screen.findByRole("button", { name: "결제 내역 다시 정리" }),
    );

    await waitFor(() =>
      expect(mocks.reportPaymentFailure).toHaveBeenCalledTimes(2),
    );
    expect(
      await screen.findByText("결제 내역에도 실패로 반영됐어요."),
    ).toBeInTheDocument();
    expect(sessionStorage.getItem("kwb:toss-pending-order")).toBeNull();
  });
});
