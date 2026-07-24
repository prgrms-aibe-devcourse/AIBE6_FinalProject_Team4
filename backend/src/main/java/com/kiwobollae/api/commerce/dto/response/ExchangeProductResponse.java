package com.kiwobollae.api.commerce.dto.response;

import com.kiwobollae.api.commerce.entity.ExchangeProduct;
import com.kiwobollae.api.commerce.entity.enums.ActiveStatus;
import java.time.LocalDateTime;

public record ExchangeProductResponse(
		Long id,
		String name,
		Integer stock,
		String description,
		String imageUrl,
		ActiveStatus status,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {
	public static ExchangeProductResponse from(ExchangeProduct exchangeProduct) {
		return new ExchangeProductResponse(
				exchangeProduct.getId(),
				exchangeProduct.getName(),
				exchangeProduct.getStock(),
				exchangeProduct.getDescription(),
				exchangeProduct.getImageUrl(),
				exchangeProduct.getStatus(),
				exchangeProduct.getCreatedAt(),
				exchangeProduct.getUpdatedAt()
		);
	}
}
