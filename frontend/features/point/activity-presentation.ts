import {
  PointActivity,
  PointReferenceType,
  PointTransactionType,
} from "@/features/point/api";
import { getAdminPointAdjustmentReasonLabel } from "@/features/point/admin-adjustment-reasons";

export type PointHistoryFilterKey =
  | "all"
  | "charge"
  | "charge-refund"
  | "journal-reward"
  | "order"
  | "card-purchase"
  | "gacha-purchase"
  | "market-offer"
  | "market-trade"
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
  {
    key: "gacha-purchase",
    label: "가챠 카드팩 구매",
    refType: "GACHA_PURCHASE",
  },
  { key: "market-offer", label: "거래소 제안", refType: "MARKET_OFFER" },
  { key: "market-trade", label: "카드 거래", refType: "MARKET_TRADE" },
  { key: "admin-adjust", label: "운영팀 조정", type: "ADMIN_ADJUST" },
];

export interface PointActivityLink {
  href: string;
  label: string;
}

export function getPointActivityTitle(activity: PointActivity): string {
  if (activity.type === "CHARGE") return "포인트 충전";
  if (activity.type === "JOURNAL_REWARD") return "성장일지 작성 보상";
  if (activity.type === "MARKET_ESCROW") return "거래소 제안 포인트 보관";
  if (activity.type === "MARKET_RELEASE") return "거래소 보관 포인트 반환";
  if (activity.type === "MARKET_PURCHASE") return "카드 거래소 구매";
  if (activity.type === "MARKET_SALE") return "카드 거래소 판매 정산";
  if (activity.type === "ADMIN_ADJUST") {
    return activity.amount > 0 ? "운영팀 포인트 지급" : "운영팀 포인트 차감";
  }
  if (activity.type === "REFUND") return "포인트 충전 환불";
  if (activity.type === "PURCHASE") {
    if (activity.refType === "ORDER") return "상품 주문 결제";
    if (activity.refType === "CARD_PURCHASE") return "쿠폰 구매";
    if (activity.refType === "GACHA_PURCHASE") return "가챠 카드팩 구매";
    return "포인트 사용";
  }
  if (activity.type === "RESTORE") {
    if (activity.refType === "ORDER") return "상품 주문 취소";
    if (activity.refType === "CARD_PURCHASE")
      return "쿠폰 구매 취소 · 포인트 반환";
    if (activity.refType === "GACHA_PURCHASE")
      return "가챠 카드팩 구매 취소 · 포인트 반환";
    return "결제 취소 · 포인트 반환";
  }
  return "포인트 내역";
}

export function getPointActivityDescription(activity: PointActivity): string {
  if (activity.type === "CHARGE") return "충전한 포인트가 잔액에 반영됐어요.";
  if (activity.type === "JOURNAL_REWARD")
    return "성장일지를 작성하고 보너스 포인트를 받았어요.";
  if (activity.type === "MARKET_ESCROW")
    return "가격 제안에 사용할 충전 포인트를 거래 완료 전까지 보관해요.";
  if (activity.type === "MARKET_RELEASE")
    return "제안이 종료되거나 보관 금액이 남아 충전 포인트가 돌아왔어요.";
  if (activity.type === "MARKET_PURCHASE")
    return "카드 거래소 구매에 충전 포인트를 사용했어요.";
  if (activity.type === "MARKET_SALE")
    return "카드 판매 대금에서 거래 수수료를 제외한 포인트를 받았어요.";
  if (activity.type === "REFUND")
    return "현금 환불 처리로 충전 포인트가 함께 차감됐어요.";
  if (activity.type === "ADMIN_ADJUST") {
    const action = activity.amount > 0 ? "지급" : "차감";
    return activity.adjustmentReason
      ? `운영팀에서 ${getAdminPointAdjustmentReasonLabel(activity.adjustmentReason)} 사유로 포인트를 ${action}했어요.`
      : `운영팀에서 포인트를 ${action}했어요. 기존 내역이라 사유 기록 없음.`;
  }
  if (activity.type === "RESTORE" && activity.refType === "GACHA_PURCHASE")
    return "가챠 카드팩 구매에 사용한 포인트가 잔액으로 돌아왔어요.";
  if (activity.type === "RESTORE")
    return "결제에 사용한 포인트가 잔액으로 돌아왔어요.";
  if (activity.refType === "ORDER")
    return "상품 주문 결제에 포인트를 사용했어요.";
  if (activity.refType === "CARD_PURCHASE")
    return "쿠폰을 구매하는 데 포인트를 사용했어요.";
  if (activity.refType === "GACHA_PURCHASE")
    return "가챠 카드팩을 구매하는 데 포인트를 사용했어요.";
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
  if (activity.refType === "GACHA_PURCHASE") {
    return { href: "/gacha?tab=history", label: "가챠 개봉 내역 보기" };
  }
  if (activity.refType === "PAYMENT" || activity.refType === "PAYMENT_REFUND") {
    return { href: "/my/points/payments", label: "결제 내역 보기" };
  }
  if (activity.refType === "JOURNAL_COMPLETION") {
    return {
      href: activity.refId ? `/journals/${activity.refId}` : "/journals",
      label: "성장일지 보기",
    };
  }
  if (activity.refType === "MARKET_OFFER") {
    return {
      href: activity.refId
        ? `/card-market/negotiations/${activity.refId}`
        : "/card-market?view=sent",
      label: "가격 협상 보기",
    };
  }
  if (activity.refType === "MARKET_TRADE") {
    return { href: "/card-market?view=trades", label: "거래 내역 보기" };
  }
  return null;
}
