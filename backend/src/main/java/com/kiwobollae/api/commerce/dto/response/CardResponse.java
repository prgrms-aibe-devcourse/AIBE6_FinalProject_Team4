package com.kiwobollae.api.commerce.dto.response;

import com.kiwobollae.api.commerce.entity.Card;
import com.kiwobollae.api.commerce.entity.enums.ActiveStatus;
import java.time.LocalDateTime;

public record CardResponse(
		Long id,
		String name,
		Long pointPrice,
		Long exchangeProductId,
		String exchangeProductName,
		Integer requiredCountForExchange,
		String description,
		String imageUrl,
		ActiveStatus status,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {
	public static CardResponse from(Card card) {
		return new CardResponse(
				card.getId(),
				card.getName(),
				card.getPointPrice(),
				card.getExchangeProduct().getId(),
				card.getExchangeProduct().getName(),
				card.getRequiredCountForExchange(),
				card.getDescription(),
				card.getImageUrl(),
				card.getStatus(),
				card.getCreatedAt(),
				card.getUpdatedAt()
		);
	}
}
