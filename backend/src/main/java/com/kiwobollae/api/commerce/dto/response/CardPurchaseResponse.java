package com.kiwobollae.api.commerce.dto.response;

import com.kiwobollae.api.commerce.entity.CardPurchaseLog;
import com.kiwobollae.api.point.dto.response.PointDeductionResult;
import java.time.LocalDateTime;

public record CardPurchaseResponse(
		Long purchaseId,
		Long cardId,
		String cardName,
		Long unitPoint,
		Integer quantity,
		Long usedPoint,
		Long usedFreePoint,
		Long usedPaidPoint,
		Integer ownedCount,
		Long remainingBalance,
		LocalDateTime purchasedAt
) {
	public static CardPurchaseResponse from(
			CardPurchaseLog log,
			PointDeductionResult pointUsage,
			Integer ownedCount
	) {
		return new CardPurchaseResponse(
				log.getId(),
				log.getCard().getId(),
				log.getCardName(),
				log.getUnitPoint(),
				log.getQuantity(),
				log.getUsedPoint(),
				pointUsage.usedFreePoint(),
				pointUsage.usedPaidPoint(),
				ownedCount,
				pointUsage.remainingBalance(),
				log.getCreatedAt()
		);
	}
}
