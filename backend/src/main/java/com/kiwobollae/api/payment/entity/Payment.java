package com.kiwobollae.api.payment.entity;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.global.common.BaseTimeEntity;
import com.kiwobollae.api.payment.entity.enums.PaymentProviderType;
import com.kiwobollae.api.payment.entity.enums.PaymentStatus;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "payments",
		uniqueConstraints = {
				@UniqueConstraint(name = "uq_payments_provider_order_id", columnNames = "provider_order_id"),
				@UniqueConstraint(name = "uq_payments_payment_key", columnNames = "payment_key")
		},
		indexes = {
				@Index(name = "idx_payment_user_id_created_at", columnList = "user_id, created_at"),
				@Index(name = "idx_payment_status", columnList = "status")
		},
		check = {
				@CheckConstraint(name = "ck_payments_direct_charge_amount",
						constraint = "charge_product_id is not null or (cash_amount = point_amount and cash_amount between 1000 and 300000 and mod(cash_amount, 10) = 0)")
		})
// JPA Auditing에 더해 Asia/Seoul DB 세션 기준 ON UPDATE 안전망(공용 BaseTimeEntity는 건드리지 않고 override).
@AttributeOverride(name = "updatedAt", column = @Column(name = "updated_at", nullable = false,
		columnDefinition = "datetime(6) default CURRENT_TIMESTAMP(6) on update CURRENT_TIMESTAMP(6)"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Payment extends BaseTimeEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	// 기존 정액 충전 상품으로 생성된 결제 이력을 식별하기 위한 legacy 참조다.
	// 신규 직접 충전 결제에는 null을 저장하며 애플리케이션에서는 엔티티 연관으로 사용하지 않는다.
	@Column(name = "charge_product_id")
	private Long chargeProductId;

	@Column(name = "charge_product_name", nullable = false, length = 50)
	private String chargeProductName;

	@Column(name = "cash_amount", nullable = false)
	private Long cashAmount;

	@Column(name = "point_amount", nullable = false)
	private Long pointAmount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PaymentStatus status;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PaymentProviderType provider;

	@Column(name = "provider_order_id", nullable = false, length = 100)
	private String providerOrderId;

	// 승인 전에는 null(다건 허용). UNIQUE는 @Table에서 명명 제약으로 관리.
	@Column(name = "payment_key", length = 200)
	private String providerPaymentKey;

	@Column(name = "approved_at")
	private LocalDateTime approvedAt;

	@PrePersist
	private void ensureChargeProductNameSnapshot() {
		if ((chargeProductName == null || chargeProductName.isBlank()) && pointAmount != null) {
			chargeProductName = String.format("%,dP 충전", pointAmount);
		}
	}
}
