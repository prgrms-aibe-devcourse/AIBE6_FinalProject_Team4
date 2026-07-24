package com.kiwobollae.api.payment.dto.response;

import com.kiwobollae.api.payment.entity.Payment;
import com.kiwobollae.api.payment.entity.enums.PaymentProvider;
import com.kiwobollae.api.payment.entity.enums.PaymentStatus;
import java.time.LocalDateTime;

public record PaymentResponse(
		Long id,
		Long userId,
		Long chargeProductId,
		String chargeProductName,
		Long amount,
		Long pointAmount,
		PaymentStatus status,
		PaymentProvider provider,
		String providerOrderId,
		String providerPaymentKey,
		String failureCode,
		String failureMessage,
		LocalDateTime approvedAt,
		LocalDateTime failedAt,
		LocalDateTime canceledAt,
		LocalDateTime createdAt
) {
	public static PaymentResponse from(Payment payment) {
		return new PaymentResponse(
				payment.getId(),
				payment.getUser().getId(),
				payment.getChargeProduct().getId(),
				payment.getChargeProduct().getName(),
				payment.getCashAmount(),
				payment.getPointAmount(),
				payment.getStatus(),
				payment.getProvider(),
				payment.getProviderOrderId(),
				payment.getProviderPaymentKey(),
				payment.getFailureCode(),
				payment.getFailureMessage(),
				payment.getApprovedAt(),
				payment.getFailedAt(),
				payment.getCanceledAt(),
				payment.getCreatedAt()
		);
	}
}
