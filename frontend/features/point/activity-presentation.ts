import {
  PointActivity,
  PointReferenceType,
  PointTransactionType,
} from "@/features/point/api";

export type PointHistoryFilterKey =
  | "all"
  | "charge"
  | "charge-refund"
  | "journal-reward"
  | "order"
  | "card-purchase"
  | "admin-adjust";

export interface PointHistoryFilter {
  key: PointHistoryFilterKey;
  label: string;
  type?: PointTransactionType;
  refType?: PointReferenceType;
}

export const POINT_HISTORY_FILTERS: PointHistoryFilter[] = [
  { key: "all", label: "전체" },
  { key: "charge", label: "충전", type: "CHARGE" },
  { key: "charge-refund", label: "충전 환불", type: "REFUND" },
  { key: "journal-reward", label: "성장일지 보상", type: "JOURNAL_REWARD" },
  { key: "order", label: "상품 주문", refType: "ORDER" },
  { key: "card-purchase", label: "쿠폰 구매", refType: "CARD_PURCHASE" },
  { key: "admin-adjust", label: "운영팀 조정", type: "ADMIN_ADJUST" },
];

export interface PointActivityLink {
  href: string;
  label: string;
}

export function getPointActivityTitle(activity: PointActivity): string {
  if (activity.type === "CHARGE") return "포인트 충전";
  if (activity.type === "JOURNAL_REWARD") return "성장일지 작성 보상";
  if (activity.type === "ADMIN_ADJUST") {
    return activity.amount > 0 ? "운영팀 포인트 지급" : "운영팀 포인트 차감";
  }
  if (activity.type === "REFUND") return "포인트 충전 환불";
  if (activity.type === "PURCHASE") {
    if (activity.refType === "ORDER") return "상품 주문 결제";
    if (activity.refType === "CARD_PURCHASE") return "쿠폰 구매";
    return "포인트 사용";
  }
  if (activity.type === "RESTORE") {
    if (activity.refType === "ORDER") return "상품 주문 취소";
    if (activity.refType === "CARD_PURCHASE")
      return "쿠폰 구매 취소 · 포인트 반환";
    return "결제 취소 · 포인트 반환";
  }
  return "포인트 내역";
}

export function getPointActivityDescription(activity: PointActivity): string {
  if (activity.type === "CHARGE") return "충전한 포인트가 잔액에 반영됐어요.";
  if (activity.type === "JOURNAL_REWARD")
    return "성장일지를 작성하고 보너스 포인트를 받았어요.";
  if (activity.type === "REFUND")
    return "현금 환불 처리로 충전 포인트가 함께 차감됐어요.";
  if (activity.type === "ADMIN_ADJUST") {
    return activity.amount > 0
      ? "운영팀에서 포인트를 지급했어요."
      : "운영팀 조정으로 포인트가 차감됐어요.";
  }
  if (activity.type === "RESTORE")
    return "결제에 사용한 포인트가 잔액으로 돌아왔어요.";
  if (activity.refType === "ORDER")
    return "상품 주문 결제에 포인트를 사용했어요.";
  if (activity.refType === "CARD_PURCHASE")
    return "쿠폰을 구매하는 데 포인트를 사용했어요.";
  return "포인트 잔액이 변경됐어요.";
}

export function getPointActivityLink(
  activity: PointActivity,
): PointActivityLink | null {
  if (activity.refType === "ORDER") {
    return {
      href: activity.refId
        ? `/my/orders#order-${activity.refId}`
        : "/my/orders",
      label: "주문 내역 보기",
    };
  }
  if (activity.refType === "CARD_PURCHASE") {
    return { href: "/cards?scope=mine", label: "보유 쿠폰 보기" };
  }
  if (activity.refType === "PAYMENT" || activity.refType === "PAYMENT_REFUND") {
    return { href: "/my/points/payments", label: "결제 내역 보기" };
  }
  if (activity.refType === "JOURNAL_COMPLETION") {
    return { href: "/journals", label: "성장일지 보기" };
  }
  return null;
}
