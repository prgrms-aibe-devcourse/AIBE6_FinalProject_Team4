package com.kiwobollae.api.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaymentRefundRequest(
		@NotBlank @Size(max = 200) String reason
) {
}
