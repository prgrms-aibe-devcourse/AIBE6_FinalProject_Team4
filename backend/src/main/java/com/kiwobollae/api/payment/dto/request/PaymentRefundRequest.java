package com.kiwobollae.api.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PaymentRefundRequest(
		@NotNull Long paymentId,
		@NotBlank @Size(max = 200) String reason
) {
}
