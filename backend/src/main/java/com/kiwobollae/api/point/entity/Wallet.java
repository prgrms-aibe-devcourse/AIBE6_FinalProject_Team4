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
// 유상(paid_point)은 실결제 자산이라 음수 불가. free_point는 관리자 강제 조정으로 음수 허용 → CHECK 없음.
@Table(name = "wallets",
		uniqueConstraints = {
				@UniqueConstraint(name = "uq_wallets_user_id", columnNames = "user_id")
		},
		check = {
				@CheckConstraint(name = "chk_wallets_paid_point", constraint = "paid_point >= 0")
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

	/** Applies a signed delta to the free balance and returns the new balance. 음수 허용 여부는 호출부가 거래 유형별로 검증한다. */
	public long increaseFreePoint(long delta) {
		this.freePoint += delta;
		return this.freePoint;
	}

	/** 화면 표시용 유상/무상 합산 잔액. */
	public long totalBalance() {
		return this.paidPoint + this.freePoint;
	}
}
