package com.kiwobollae.api.commerce.entity;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.commerce.entity.enums.ConfirmedBy;
import com.kiwobollae.api.commerce.entity.enums.DeliveryStatus;
import com.kiwobollae.api.commerce.entity.enums.OrderStatus;
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

/** Has richer timestamp semantics (ordered_at/delivered_at/cancelled_at/confirmed_at) so it does not extend BaseTimeEntity. */
@Getter
@Entity
@Table(name = "orders", indexes = {
		@Index(name = "idx_orders_user_id_ordered_at", columnList = "user_id, ordered_at"),
		@Index(name = "idx_orders_status_delivery_status_delivered_at", columnList = "status, delivery_status, delivered_at")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Order extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "total_point", nullable = false)
	private Long totalPoint;

	@Column(name = "used_free_point", nullable = false)
	private Long usedFreePoint;

	@Column(name = "used_paid_point", nullable = false)
	private Long usedPaidPoint;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OrderStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "delivery_status", nullable = false, length = 20)
	private DeliveryStatus deliveryStatus;

	@Column(name = "receiver_name", nullable = false, length = 50)
	private String receiverName;

	@Column(name = "receiver_phone", nullable = false, length = 20)
	private String receiverPhone;

	@Column(nullable = false, length = 200)
	private String address;

	@Column(name = "address_detail", length = 100)
	private String addressDetail;

	@Column(name = "ordered_at", nullable = false)
	private LocalDateTime orderedAt;

	@Column(name = "delivered_at")
	private LocalDateTime deliveredAt;

	@Column(name = "cancelled_at")
	private LocalDateTime cancelledAt;

	@Column(name = "confirmed_at")
	private LocalDateTime confirmedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "confirmed_by", length = 10)
	private ConfirmedBy confirmedBy;
}
