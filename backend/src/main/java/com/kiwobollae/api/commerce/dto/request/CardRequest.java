package com.kiwobollae.api.commerce.dto.request;

import com.kiwobollae.api.commerce.entity.enums.ActiveStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CardRequest(
		@NotBlank @Size(max = 100) String name,
    @NotNull @PositiveOrZero Long pointPrice,
    @NotNull Long exchangeProductId,
    @NotNull @Positive Integer requiredCountForExchange,
		@Size(max = 2000) String description,
		@Size(max = 500) String imageUrl,
		@NotNull ActiveStatus status
) {
}
