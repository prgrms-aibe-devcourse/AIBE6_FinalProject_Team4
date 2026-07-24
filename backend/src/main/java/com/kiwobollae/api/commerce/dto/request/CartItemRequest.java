package com.kiwobollae.api.commerce.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CartItemRequest(
		@NotNull Long productId,
		@NotNull @Positive Integer quantity
) {
}
