package com.kiwobollae.api.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaymentFailureRequest(
		@NotBlank @Size(max = 100) String providerOrderId,
		@NotBlank @Size(max = 100) String code
) {
}
