package com.kiwobollae.api.payment.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PaymentRequest(
		@NotNull
		@Min(value = 1_000, message = "충전 금액은 1,000원 이상이어야 합니다.")
		@Max(value = 300_000, message = "충전 금액은 300,000원 이하여야 합니다.")
		Long pointAmount
) {
}
