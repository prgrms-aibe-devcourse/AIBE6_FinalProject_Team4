import { afterEach, describe, expect, it, vi } from "vitest";
import {
  getTossPaymentErrorCode,
  requestTossPayment,
  TOSS_PENDING_ORDER_STORAGE_KEY,
} from "@/features/payment/toss-payment";

describe("requestTossPayment", () => {
  afterEach(() => {
    delete window.TossPayments;
    sessionStorage.clear();
  });

  it("Toss V2 테스트 결제창에 서버가 만든 주문 정보를 전달한다", async () => {
    const requestPayment = vi.fn().mockResolvedValue(undefined);
    const payment = vi.fn().mockReturnValue({ requestPayment });
    const TossPayments = Object.assign(vi.fn().mockReturnValue({ payment }), {
      ANONYMOUS: "ANONYMOUS",
    });
    window.TossPayments = TossPayments;

    await requestTossPayment({
      clientKey: "test_ck_test_client",
      orderId: "KWB-order-1",
      orderName: "5,000 포인트 충전",
      amount: 5_000,
      customerEmail: "test@test.com",
      customerName: "테스트",
    });

    expect(TossPayments).toHaveBeenCalledWith("test_ck_test_client");
    expect(payment).toHaveBeenCalledWith({ customerKey: "ANONYMOUS" });
    expect(requestPayment).toHaveBeenCalledWith({
      method: "CARD",
      amount: { currency: "KRW", value: 5_000 },
      orderId: "KWB-order-1",
      orderName: "5,000 포인트 충전",
      successUrl: "http://localhost:3000/my/points/charge/success",
      failUrl: "http://localhost:3000/my/points/charge/fail",
      customerEmail: "test@test.com",
      customerName: "테스트",
    });
    expect(sessionStorage.getItem(TOSS_PENDING_ORDER_STORAGE_KEY)).toBe(
      "KWB-order-1",
    );
  });

  it("라이브 클라이언트 키는 결제창을 열기 전에 거부한다", async () => {
    await expect(
      requestTossPayment({
        clientKey: "live_ck_client",
        orderId: "KWB-order-1",
        orderName: "포인트 충전",
        amount: 5_000,
      }),
    ).rejects.toThrow("Toss Payments 테스트 클라이언트 키를 설정해 주세요.");
  });

  it("결제창 취소 오류 코드를 실패 반영 API에 전달할 수 있게 추출한다", () => {
    expect(getTossPaymentErrorCode({ code: "PAY_PROCESS_CANCELED" })).toBe(
      "PAY_PROCESS_CANCELED",
    );
    expect(getTossPaymentErrorCode(new Error("결제창 오류"))).toBe(
      "PAYMENT_WINDOW_ERROR",
    );
  });
});
