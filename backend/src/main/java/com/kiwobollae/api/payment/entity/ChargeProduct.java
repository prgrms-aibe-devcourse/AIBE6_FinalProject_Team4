package com.kiwobollae.api.payment.entity;

import com.kiwobollae.api.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "charge_products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ChargeProduct extends BaseTimeEntity {

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false)
	private Long price;

	@Column(name = "point_amount", nullable = false)
	private Long pointAmount;

	@Column(name = "is_active", nullable = false)
	private Boolean isActive;
}
