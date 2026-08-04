package com.kiwobollae.api.point.entity;

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
import org.hibernate.annotations.CreationTimestamp;

// 불변 원장(append-only). 멱등은 공통 idempotency_keys 테이블 + (ref_type, ref_id) 조회로 처리 → 자체 멱등 컬럼 없음.
@Getter
@Entity
@Table(name = "point_transactions", indexes = {
		@Index(name = "idx_point_transaction_wallet_id_created_at", columnList = "wallet_id, created_at"),
		@Index(name = "idx_point_transaction_ref_type_ref_id", columnList = "ref_type, ref_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PointTransaction extends BaseEntity {

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

	@Column(name = "balance_after", nullable = false)
	private Long balanceAfter;

	@Enumerated(EnumType.STRING)
	@Column(name = "ref_type", length = 20)
	private PointRefType refType;

	/** Polymorphic reference — target table determined by refType (ADMIN 조정 시 실행 관리자 user ID). */
	@Column(name = "ref_id")
	private Long refId;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
}
