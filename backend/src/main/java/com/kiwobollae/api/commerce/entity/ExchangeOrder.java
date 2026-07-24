package com.kiwobollae.api.commerce.entity;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.commerce.entity.enums.CancelledBy;
import com.kiwobollae.api.commerce.entity.enums.ExchangeStatus;
import com.kiwobollae.api.global.common.BaseEntity;
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

/** Has richer timestamp semantics (requested_at/cancelled_at/delivered_at) so it does not extend BaseTimeEntity. */
@Getter
@Entity
@Table(name = "exchange_orders", indexes = {
		@Index(name = "idx_exchange_orders_user_id_requested_at", columnList = "user_id, requested_at"),
		@Index(name = "idx_exchange_orders_status", columnList = "status")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ExchangeOrder extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "card_id", nullable = false)
	private Card card;

	@Column(name = "card_name", nullable = false, length = 100)
	private String cardName;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "exchange_product_id", nullable = false)
	private ExchangeProduct exchangeProduct;

	@Column(name = "exchange_product_name", nullable = false, length = 100)
	private String exchangeProductName;

	@Column(name = "used_card_count", nullable = false)
	private Integer usedCardCount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ExchangeStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "cancelled_by", length = 20)
	private CancelledBy cancelledBy;

	@Column(name = "cancel_reason", length = 200)
	private String cancelReason;

	@Column(name = "cancelled_at")
	private LocalDateTime cancelledAt;

	@Column(name = "delivered_at")
	private LocalDateTime deliveredAt;

	@Column(name = "receiver_name", nullable = false, length = 50)
	private String receiverName;

	@Column(name = "receiver_phone", nullable = false, length = 20)
	private String receiverPhone;

	@Column(nullable = false, length = 200)
	private String address;

	@Column(name = "address_detail", length = 100)
	private String addressDetail;

	@Column(name = "requested_at", nullable = false)
	private LocalDateTime requestedAt;
}
