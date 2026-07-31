package com.kiwobollae.api.payment.provider;

public record PaymentRefundCommand(
		String providerOrderId,
		String paymentKey,
		Long cashAmount,
		String reason
) {
}
