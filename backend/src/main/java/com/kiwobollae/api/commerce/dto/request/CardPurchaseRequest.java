package com.kiwobollae.api.commerce.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CardPurchaseRequest(
		@NotNull Long cardId,
		@NotNull @Positive Integer quantity
) {
}
