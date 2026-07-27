package com.kiwobollae.api.payment.entity;

import com.kiwobollae.api.global.common.BaseEntity;
import com.kiwobollae.api.payment.entity.enums.PaymentRefundStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

// 최종 ERD 기준 로그성 레코드: created_at/completed_at만(updated_at 없음). 환불 멱등은 공통 idempotency_keys 테이블로.
@Getter
@Entity
@Table(name = "payment_refunds",
		uniqueConstraints = {
				@UniqueConstraint(name = "uq_payment_refunds_refund_key", columnNames = "refund_key")
		},
		indexes = {
				@Index(name = "idx_payment_refund_payment_id_created_at", columnList = "payment_id, created_at"),
				@Index(name = "idx_payment_refund_status", columnList = "status")
		})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PaymentRefund extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "payment_id", nullable = false)
	private Payment payment;

	@Column(name = "cash_amount", nullable = false)
	private Long cashAmount;

	@Column(name = "point_amount", nullable = false)
	private Long pointAmount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PaymentRefundStatus status;

	@Column(length = 200)
	private String reason;

	// PG 환불키. UNIQUE는 @Table에서 명명 제약으로 관리(승인 전 null 다건 허용).
	@Column(name = "refund_key", length = 200)
	private String refundKey;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;
}
