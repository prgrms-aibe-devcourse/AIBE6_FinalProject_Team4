package com.kiwobollae.api.payment.provider;

public record PaymentConfirmCommand(
		String providerOrderId,
		String paymentKey,
		Long amount
) {
}
