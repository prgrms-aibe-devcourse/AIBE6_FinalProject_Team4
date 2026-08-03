package com.kiwobollae.api.payment.dto.request;

import com.kiwobollae.api.payment.entity.enums.PaymentProviderType;
import jakarta.validation.constraints.NotNull;

public record PaymentRequest(
		@NotNull Long chargeProductId,
		PaymentProviderType provider
) {
	public PaymentRequest(Long chargeProductId) {
		this(chargeProductId, null);
	}
}
