package com.kiwobollae.api.commerce.entity;

import com.kiwobollae.api.commerce.entity.enums.ActiveStatus;
import com.kiwobollae.api.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "cards", indexes = {
		@Index(name = "idx_cards_status_created_at", columnList = "status, created_at")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Card extends BaseTimeEntity {

	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "point_price", nullable = false)
	private Long pointPrice;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "exchange_product_id", nullable = false)
	private ExchangeProduct exchangeProduct;

	@Column(name = "required_count_for_exchange", nullable = false)
	private Integer requiredCountForExchange;

	@Column(length = 2000)
	private String description;

	@Column(name = "image_url", length = 500)
	private String imageUrl;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ActiveStatus status;

	public void updateInfo(
			String name,
			Long pointPrice,
			ExchangeProduct exchangeProduct,
			Integer requiredCountForExchange,
			String description,
			String imageUrl
	) {
		this.name = name;
		this.pointPrice = pointPrice;
		this.exchangeProduct = exchangeProduct;
		this.requiredCountForExchange = requiredCountForExchange;
		this.description = description;
		this.imageUrl = imageUrl;
	}

	public void changeStatus(ActiveStatus status) {
		this.status = status;
	}

	public void updateImage(String imageUrl) {
		this.imageUrl = imageUrl;
	}
}
