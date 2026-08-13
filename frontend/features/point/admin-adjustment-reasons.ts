import { AdminPointAdjustmentReason } from "@/features/point/api";

export const ADMIN_POINT_ADJUSTMENT_REASON_LABELS: Record<
  AdminPointAdjustmentReason,
  string
> = {
  SPECIAL_EVENT: "특별 이벤트",
  OUTSTANDING_MEMBER: "우수 회원 선정",
  FRAUD_PENALTY: "부정행위 패널티",
};

export const ADMIN_POINT_GRANT_REASONS: AdminPointAdjustmentReason[] = [
  "SPECIAL_EVENT",
  "OUTSTANDING_MEMBER",
];

export const ADMIN_POINT_DEDUCT_REASONS: AdminPointAdjustmentReason[] = [
  "FRAUD_PENALTY",
];

export function getAdminPointAdjustmentReasonLabel(
  reason: AdminPointAdjustmentReason | null,
): string {
  return reason
    ? ADMIN_POINT_ADJUSTMENT_REASON_LABELS[reason]
    : "사유 기록 없음";
}
