package com.kiwobollae.api.commerce.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CartItemQuantityRequest(
		@NotNull @Min(1) @Max(99) Integer quantity
) {
}
