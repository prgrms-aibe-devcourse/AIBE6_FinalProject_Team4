package com.kiwobollae.api.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PaymentConfirmRequest(
		@NotBlank @Size(max = 100) String providerOrderId,
		@NotBlank @Size(max = 200) String paymentKey,
		@NotNull @Positive Long amount
) {
}
