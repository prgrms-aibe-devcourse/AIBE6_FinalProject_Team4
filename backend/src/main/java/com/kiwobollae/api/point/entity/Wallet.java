package com.kiwobollae.api.point.entity;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.global.common.BaseTimeEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
// 유상·무상 포인트 모두 음수 불가. 모든 차감은 서비스에서 먼저 검증하고 DB CHECK를 마지막 안전망으로 둔다.
@Table(name = "wallets",
		uniqueConstraints = {
				@UniqueConstraint(name = "uq_wallets_user_id", columnNames = "user_id")
		},
		check = {
				@CheckConstraint(name = "chk_wallets_paid_point", constraint = "paid_point >= 0"),
				@CheckConstraint(name = "chk_wallets_free_point", constraint = "free_point >= 0")
		})
// JPA Auditing(@LastModifiedDate)에 더해 Asia/Seoul DB 세션 기준 ON UPDATE 안전망까지 둔다(공용 BaseTimeEntity는 건드리지 않고 override).
@AttributeOverride(name = "updatedAt", column = @Column(name = "updated_at", nullable = false,
		columnDefinition = "datetime(6) default CURRENT_TIMESTAMP(6) on update CURRENT_TIMESTAMP(6)"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Wallet extends BaseTimeEntity {

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "paid_point", nullable = false)
	private Long paidPoint;

	@Column(name = "free_point", nullable = false)
	private Long freePoint;

	/** Applies a signed delta to the paid balance and returns the new balance. paid는 음수 불가(호출부가 검증). */
	public long increasePaidPoint(long delta) {
		this.paidPoint += delta;
		return this.paidPoint;
	}

	/** Applies a signed delta to the free balance and returns the new balance. 음수 방지는 호출부와 DB CHECK가 검증한다. */
	public long increaseFreePoint(long delta) {
		this.freePoint += delta;
		return this.freePoint;
	}

	/** 화면 표시용 유상/무상 합산 잔액. */
	public long totalBalance() {
		return this.paidPoint + this.freePoint;
	}
}
