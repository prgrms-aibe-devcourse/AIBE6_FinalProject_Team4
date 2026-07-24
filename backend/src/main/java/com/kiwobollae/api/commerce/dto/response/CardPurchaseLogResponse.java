package com.kiwobollae.api.commerce.dto.response;

import com.kiwobollae.api.commerce.entity.CardPurchaseLog;
import java.time.LocalDateTime;

public record CardPurchaseLogResponse(
		Long id,
		Long userId,
		Long cardId,
		String cardName,
		Long unitPoint,
		Integer quantity,
		Long usedPoint,
		LocalDateTime createdAt
) {
	public static CardPurchaseLogResponse from(CardPurchaseLog cardPurchaseLog) {
		return new CardPurchaseLogResponse(
				cardPurchaseLog.getId(),
				cardPurchaseLog.getUser().getId(),
				cardPurchaseLog.getCard().getId(),
				cardPurchaseLog.getCardName(),
				cardPurchaseLog.getUnitPoint(),
				cardPurchaseLog.getQuantity(),
				cardPurchaseLog.getUsedPoint(),
				cardPurchaseLog.getCreatedAt()
		);
	}
}
