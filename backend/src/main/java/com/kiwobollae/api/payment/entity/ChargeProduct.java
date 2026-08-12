package com.kiwobollae.api.payment.entity;

import com.kiwobollae.api.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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

	@Version
	// 구버전 인스턴스가 Blue/Green 혼재 중 INSERT해도 DB 기본값으로 기록할 수 있어야 한다.
	@Column(nullable = false, columnDefinition = "bigint default 0")
	private Long version;

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
