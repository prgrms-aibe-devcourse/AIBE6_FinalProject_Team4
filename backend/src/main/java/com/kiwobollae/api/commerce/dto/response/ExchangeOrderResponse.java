package com.kiwobollae.api.commerce.dto.response;

import com.kiwobollae.api.commerce.entity.ExchangeOrder;
import com.kiwobollae.api.commerce.entity.enums.CancelledBy;
import com.kiwobollae.api.commerce.entity.enums.ExchangeStatus;
import java.time.LocalDateTime;

public record ExchangeOrderResponse(
		Long id,
		Long userId,
		Long cardId,
		String cardName,
		Long exchangeProductId,
		String exchangeProductName,
		Integer usedCardCount,
		ExchangeStatus status,
		CancelledBy cancelledBy,
		String cancelReason,
		LocalDateTime cancelledAt,
		LocalDateTime deliveredAt,
		String receiverName,
		String receiverPhone,
		String address,
		String addressDetail,
		LocalDateTime requestedAt
) {
	public static ExchangeOrderResponse from(ExchangeOrder exchangeOrder) {
		return new ExchangeOrderResponse(
				exchangeOrder.getId(),
				exchangeOrder.getUser().getId(),
				exchangeOrder.getCard().getId(),
				exchangeOrder.getCardName(),
				exchangeOrder.getExchangeProduct().getId(),
				exchangeOrder.getExchangeProductName(),
				exchangeOrder.getUsedCardCount(),
				exchangeOrder.getStatus(),
				exchangeOrder.getCancelledBy(),
				exchangeOrder.getCancelReason(),
				exchangeOrder.getCancelledAt(),
				exchangeOrder.getDeliveredAt(),
				exchangeOrder.getReceiverName(),
				exchangeOrder.getReceiverPhone(),
				exchangeOrder.getAddress(),
				exchangeOrder.getAddressDetail(),
				exchangeOrder.getRequestedAt()
		);
	}
}
