package com.kiwobollae.api.payment.entity;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.global.common.BaseTimeEntity;
import com.kiwobollae.api.payment.entity.enums.PaymentProvider;
import com.kiwobollae.api.payment.entity.enums.PaymentStatus;
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

@Getter
@Entity
@Table(name = "payments", indexes = {
		@Index(name = "idx_payment_user_id_created_at", columnList = "user_id, created_at"),
		@Index(name = "idx_payment_status", columnList = "status")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Payment extends BaseTimeEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "charge_product_id", nullable = false)
	private ChargeProduct chargeProduct;

	@Column(name = "cash_amount", nullable = false)
	private Long cashAmount;

	@Column(name = "point_amount", nullable = false)
	private Long pointAmount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PaymentStatus status;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PaymentProvider provider;

	@Column(name = "provider_order_id", nullable = false, unique = true, length = 100)
	private String providerOrderId;

	@Column(name = "payment_key", unique = true, length = 200)
	private String providerPaymentKey;

	@Column(name = "failure_code", length = 100)
	private String failureCode;

	@Column(name = "failure_message", length = 500)
	private String failureMessage;

	@Column(name = "approved_at")
	private LocalDateTime approvedAt;

	@Column(name = "failed_at")
	private LocalDateTime failedAt;

	@Column(name = "canceled_at")
	private LocalDateTime canceledAt;
}
