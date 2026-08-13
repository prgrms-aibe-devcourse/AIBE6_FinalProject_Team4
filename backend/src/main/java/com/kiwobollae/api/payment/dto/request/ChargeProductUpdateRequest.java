package com.kiwobollae.api.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ChargeProductUpdateRequest(
		@NotBlank @Size(max = 50) String name,
		@NotNull @Positive Long price,
		@NotNull @Positive Long pointAmount,
		@NotNull Boolean isActive,
		@NotNull @PositiveOrZero Long version
) {
}
