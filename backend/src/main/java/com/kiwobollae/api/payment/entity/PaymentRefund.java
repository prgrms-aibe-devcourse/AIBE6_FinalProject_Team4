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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Has richer timestamp semantics (requested_at/completed_at/failed_at) so it does not extend BaseTimeEntity. */
@Getter
@Entity
@Table(name = "payment_refunds", indexes = {
		@Index(name = "idx_payment_refund_payment_id_requested_at", columnList = "payment_id, requested_at"),
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

	@Column(name = "provider_refund_key", unique = true, length = 200)
	private String providerRefundKey;

	@Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
	private String idempotencyKey;

	@Column(length = 200)
	private String reason;

	@Column(name = "failure_code", length = 100)
	private String failureCode;

	@Column(name = "failure_message", length = 500)
	private String failureMessage;

	@Column(name = "requested_at", nullable = false)
	private LocalDateTime requestedAt;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	@Column(name = "failed_at")
	private LocalDateTime failedAt;
}
