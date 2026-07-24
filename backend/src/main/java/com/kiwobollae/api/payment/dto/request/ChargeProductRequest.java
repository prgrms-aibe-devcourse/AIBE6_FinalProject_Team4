package com.kiwobollae.api.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChargeProductRequest(
		@NotBlank @Size(max = 100) String name,
		@NotNull Long price,
		@NotNull Long pointAmount,
		@NotNull Boolean isActive
) {
}
