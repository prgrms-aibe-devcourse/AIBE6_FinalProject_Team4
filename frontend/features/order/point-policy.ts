export const ORDER_FREE_POINT_UNIT = 100;

interface OrderPointUsageInput {
  totalPoint: number;
  paidPoint: number;
  freePoint: number;
  requestedFreePoint: number;
}

export interface OrderPointUsage {
  usedFreePoint: number;
  usedPaidPoint: number;
  remainingFreePoint: number;
  remainingPaidPoint: number;
  valid: boolean;
  error: string | null;
}

export function getMaximumOrderFreePoint(totalPoint: number, freePoint: number) {
  const available = Math.min(Math.max(freePoint, 0), Math.max(totalPoint, 0));
  return Math.floor(available / ORDER_FREE_POINT_UNIT) * ORDER_FREE_POINT_UNIT;
}

export function getMinimumOrderFreePoint(totalPoint: number, paidPoint: number) {
  const shortage = Math.max(0, totalPoint - paidPoint);
  return Math.ceil(shortage / ORDER_FREE_POINT_UNIT) * ORDER_FREE_POINT_UNIT;
}

export function calculateOrderPointUsage({
  totalPoint,
  paidPoint,
  freePoint,
  requestedFreePoint,
}: OrderPointUsageInput): OrderPointUsage {
  const usedPaidPoint = Math.max(0, totalPoint - requestedFreePoint);
  let error: string | null = null;

  if (!Number.isInteger(requestedFreePoint) || requestedFreePoint < 0) {
    error = '사용할 보너스 포인트를 0P 이상으로 입력해 주세요.';
  } else if (requestedFreePoint % ORDER_FREE_POINT_UNIT !== 0) {
    error = `보너스 포인트는 ${ORDER_FREE_POINT_UNIT}P 단위로 사용할 수 있어요.`;
  } else if (requestedFreePoint > totalPoint) {
    error = '주문 금액보다 많은 보너스 포인트는 사용할 수 없어요.';
  } else if (requestedFreePoint > freePoint) {
    error = '보유한 보너스 포인트보다 많이 사용할 수 없어요.';
  } else if (usedPaidPoint > paidPoint) {
    error = `충전 포인트가 ${usedPaidPoint - paidPoint}P 부족해요.`;
  }

  return {
    usedFreePoint: requestedFreePoint,
    usedPaidPoint,
    remainingFreePoint: freePoint - requestedFreePoint,
    remainingPaidPoint: paidPoint - usedPaidPoint,
    valid: error === null,
    error,
  };
}
