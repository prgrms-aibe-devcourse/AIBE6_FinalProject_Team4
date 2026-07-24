package com.kiwobollae.api.commerce.entity;

import com.kiwobollae.api.commerce.entity.enums.ActiveStatus;
import com.kiwobollae.api.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "exchange_products", indexes = {
		@Index(name = "idx_exchange_products_status", columnList = "status")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ExchangeProduct extends BaseTimeEntity {

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false)
	private Integer stock;

	@Column(length = 2000)
	private String description;

	@Column(name = "image_url", length = 500)
	private String imageUrl;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ActiveStatus status;
}
