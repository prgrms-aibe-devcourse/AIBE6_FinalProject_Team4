package com.kiwobollae.api.payment.entity;

import com.kiwobollae.api.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 최종 ERD 기준: charge_products는 created_at/updated_at 없음(마스터 데이터, soft delete는 is_active).
@Getter
@Entity
@Table(name = "charge_products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ChargeProduct extends BaseEntity {

	@Column(nullable = false, length = 50)
	private String name;

	@Column(nullable = false)
	private Long price;

	@Column(name = "point_amount", nullable = false)
	private Long pointAmount;

	@Column(name = "is_active", nullable = false)
	private Boolean isActive;

	public void update(String name, Long price, Long pointAmount, Boolean isActive) {
		this.name = name;
		this.price = price;
		this.pointAmount = pointAmount;
		this.isActive = isActive;
	}

	public void deactivate() {
		this.isActive = false;
	}
}
