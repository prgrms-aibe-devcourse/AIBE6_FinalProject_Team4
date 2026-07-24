package com.kiwobollae.api.payment.dto.request;

import jakarta.validation.constraints.NotNull;

public record PaymentRequest(
		@NotNull Long chargeProductId
) {
}
