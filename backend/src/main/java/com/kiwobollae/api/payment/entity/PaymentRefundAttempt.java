package com.kiwobollae.api.payment.entity;

import com.kiwobollae.api.global.common.BaseEntity;
import com.kiwobollae.api.payment.entity.enums.PaymentRefundAttemptStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * PG 환불을 호출하기 직전에 남기는 시도 기록. 환불 준비 트랜잭션에서 REQUESTED 환불 및
 * 포인트 회수와 함께 커밋되므로, 외부 호출 중에는 DB 트랜잭션 없이도 대조 근거가 남는다.
 *
 * <p><b>payments·users에 FK를 걸지 않는다.</b> 감사·대조용 기록이므로 결제 내부 확정 실패 후에도
 * 독립적으로 조회할 수 있는 현재 스키마 정책을 유지한다.
 */
@Getter
@Entity
@Table(name = "payment_refund_attempts",
		indexes = {
				@Index(name = "idx_payment_refund_attempt_payment_id_status",
						columnList = "payment_id, status"),
				@Index(name = "idx_payment_refund_attempt_started_at", columnList = "started_at")
		})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PaymentRefundAttempt extends BaseEntity {

	@Column(name = "payment_id", nullable = false)
	private Long paymentId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	// 이 시도로 나갈 예정이던 현금·유상 포인트. 사후 대조 시 PG 명세와 맞춰보는 기준값이다.
	@Column(name = "cash_amount", nullable = false)
	private Long cashAmount;

	@Column(name = "point_amount", nullable = false)
	private Long pointAmount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PaymentRefundAttemptStatus status;

	@Column(length = 200)
	private String reason;

	@Column(name = "started_at", nullable = false, updatable = false)
	private LocalDateTime startedAt;

	@Column(name = "settled_at")
	private LocalDateTime settledAt;
}
