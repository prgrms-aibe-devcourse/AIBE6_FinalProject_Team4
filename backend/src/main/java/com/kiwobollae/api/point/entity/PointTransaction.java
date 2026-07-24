package com.kiwobollae.api.point.entity;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.global.common.BaseEntity;
import com.kiwobollae.api.point.entity.enums.CurrencyType;
import com.kiwobollae.api.point.entity.enums.PointRefType;
import com.kiwobollae.api.point.entity.enums.PointTxType;
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
@Table(name = "point_transactions", indexes = {
		@Index(name = "idx_point_transaction_wallet_id_created_at", columnList = "wallet_id, created_at"),
		@Index(name = "idx_point_transaction_user_id_created_at", columnList = "user_id, created_at"),
		@Index(name = "idx_point_transaction_ref_type_ref_id", columnList = "ref_type, ref_id"),
		@Index(name = "idx_point_transaction_idempotency_key_currency_type", columnList = "idempotency_key, currency_type", unique = true)
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PointTransaction extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "wallet_id", nullable = false)
	private Wallet wallet;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PointTxType type;

	@Enumerated(EnumType.STRING)
	@Column(name = "currency_type", nullable = false, length = 10)
	private CurrencyType currencyType;

	@Column(nullable = false)
	private Long amount;

	@Column(name = "balance_before", nullable = false)
	private Long balanceBefore;

	@Column(name = "balance_after", nullable = false)
	private Long balanceAfter;

	@Enumerated(EnumType.STRING)
	@Column(name = "ref_type", nullable = false, length = 30)
	private PointRefType refType;

	/** Polymorphic reference — target table determined by refType. */
	@Column(name = "ref_id")
	private Long refId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "actor_user_id")
	private User actorUser;

	@Column(length = 200)
	private String reason;

	@Column(name = "idempotency_key", nullable = false, length = 100)
	private String idempotencyKey;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;
}
