package com.kiwobollae.api.commerce.entity;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "card_purchase_logs", indexes = {
		@Index(name = "idx_card_purchase_logs_user_id_created_at", columnList = "user_id, created_at")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CardPurchaseLog extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "card_id", nullable = false)
	private Card card;

	@Column(name = "card_name", nullable = false, length = 100)
	private String cardName;

	@Column(name = "unit_point", nullable = false)
	private Long unitPoint;

	@Column(nullable = false)
	private Integer quantity;

	@Column(name = "used_point", nullable = false)
	private Long usedPoint;

	@Column(name = "used_free_point", nullable = false)
	private Long usedFreePoint;

	@Column(name = "used_paid_point", nullable = false)
	private Long usedPaidPoint;

	@Column(nullable = false, length = 20)
	private String status;

	@Column(name = "refunded_at")
	private LocalDateTime refundedAt;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;
}
