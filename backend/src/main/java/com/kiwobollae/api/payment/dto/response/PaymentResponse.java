package com.kiwobollae.api.payment.dto.response;

import com.kiwobollae.api.payment.entity.Payment;
import com.kiwobollae.api.payment.entity.enums.PaymentProviderType;
import com.kiwobollae.api.payment.entity.enums.PaymentStatus;
import java.time.LocalDateTime;

public record PaymentResponse(
		Long id,
		Long userId,
		Long cashAmount,
		Long pointAmount,
		PaymentStatus status,
		PaymentProviderType provider,
		String providerOrderId,
		String providerPaymentKey,
		LocalDateTime approvedAt,
		LocalDateTime createdAt,
		String message
) {
	public static PaymentResponse from(Payment payment) {
		return from(payment, null);
	}

	public static PaymentResponse from(Payment payment, String message) {
		return new PaymentResponse(
				payment.getId(),
				payment.getUser().getId(),
				payment.getCashAmount(),
				payment.getPointAmount(),
				payment.getStatus(),
				payment.getProvider(),
				payment.getProviderOrderId(),
				payment.getProviderPaymentKey(),
				payment.getApprovedAt(),
				payment.getCreatedAt(),
				message
		);
	}
}
