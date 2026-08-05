import { describe, expect, it } from "vitest";
import {
  getPointActivityDescription,
  getPointActivityLink,
  getPointActivityTitle,
} from "@/features/point/activity-presentation";
import { PointActivity } from "@/features/point/api";

function activity(overrides: Partial<PointActivity>): PointActivity {
  return {
    id: 1,
    type: "PURCHASE",
    refType: "ORDER",
    refId: 10,
    amount: -1000,
    paidAmount: -700,
    freeAmount: -300,
    paidBalanceAfter: 2000,
    freeBalanceAfter: 500,
    createdAt: "2026-08-03T10:00:00",
    ...overrides,
  };
}

describe("point activity presentation", () => {
  it("상품 주문과 쿠폰 구매를 거래 출처로 구분한다", () => {
    expect(getPointActivityTitle(activity({ refType: "ORDER" }))).toBe(
      "상품 주문 결제",
    );
    expect(getPointActivityTitle(activity({ refType: "CARD_PURCHASE" }))).toBe(
      "쿠폰 구매",
    );
  });

  it("취소와 충전 환불을 사용자에게 자연스러운 문구로 설명한다", () => {
    expect(getPointActivityTitle(activity({ type: "RESTORE" }))).toBe(
      "상품 주문 취소",
    );
    const refund = activity({
      type: "REFUND",
      refType: "PAYMENT_REFUND",
      amount: -1000,
    });
    expect(getPointActivityTitle(refund)).toBe("포인트 충전 환불");
    expect(getPointActivityDescription(refund)).toContain("현금 환불 처리");
  });

  it("운영팀 지급과 차감을 금액 부호로 구분한다", () => {
    expect(
      getPointActivityTitle(activity({ type: "ADMIN_ADJUST", amount: 100 })),
    ).toBe("운영팀 포인트 지급");
    expect(
      getPointActivityTitle(activity({ type: "ADMIN_ADJUST", amount: -100 })),
    ).toBe("운영팀 포인트 차감");
  });

  it("거래 출처에 맞는 관련 내역 링크를 제공한다", () => {
    expect(
      getPointActivityLink(activity({ refType: "ORDER", refId: 10 })),
    ).toEqual({
      href: "/my/orders#order-10",
      label: "주문 내역 보기",
    });
    expect(
      getPointActivityLink(activity({ refType: "CARD_PURCHASE" })),
    ).toEqual({
      href: "/cards?scope=mine",
      label: "보유 쿠폰 보기",
    });
  });
});
