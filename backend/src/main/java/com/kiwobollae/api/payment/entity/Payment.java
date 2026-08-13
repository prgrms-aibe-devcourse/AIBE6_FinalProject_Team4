package com.kiwobollae.api.payment.entity;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.global.common.BaseTimeEntity;
import com.kiwobollae.api.payment.entity.enums.PaymentProviderType;
import com.kiwobollae.api.payment.entity.enums.PaymentStatus;
import jakarta.persistence.AttributeOverride;
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

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "charge_product_id", nullable = false)
	private ChargeProduct chargeProduct;

	// Blue/Green 배포 중 구버전 인스턴스는 이 컬럼을 쓰지 않으므로 expand 단계에서는 nullable로
	// 유지한다. 신규 결제는 서비스와 @PrePersist에서 항상 스냅샷을 기록하고, legacy null/blank는
	// 조회 시 chargeProduct의 현재 이름으로 호환한다. NOT NULL 전환은 구버전 종료와 backfill을
	// 확인한 후 별도 contract migration에서 수행한다.
	@Column(name = "charge_product_name", length = 50)
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

	public String getChargeProductName() {
		return chargeProductName != null && !chargeProductName.isBlank()
				? chargeProductName
				: chargeProduct.getName();
	}

	@PrePersist
	private void ensureChargeProductNameSnapshot() {
		if ((chargeProductName == null || chargeProductName.isBlank()) && chargeProduct != null) {
			chargeProductName = chargeProduct.getName();
		}
	}
}
