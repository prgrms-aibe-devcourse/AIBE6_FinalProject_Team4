package com.kiwobollae.api.point.entity;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "wallets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Wallet extends BaseTimeEntity {

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private User user;

	@Column(name = "paid_point", nullable = false)
	private Long paidPoint;

	@Column(name = "free_point", nullable = false)
	private Long freePoint;

	@Column(nullable = false)
	private Long version;
}
