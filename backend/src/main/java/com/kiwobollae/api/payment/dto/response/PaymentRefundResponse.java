package com.kiwobollae.api.payment.dto.response;

import com.kiwobollae.api.payment.entity.PaymentRefund;
import com.kiwobollae.api.payment.entity.enums.PaymentRefundStatus;
import java.time.LocalDateTime;

public record PaymentRefundResponse(
		Long id,
		Long paymentId,
		Long cashAmount,
		Long pointAmount,
		PaymentRefundStatus status,
		String providerRefundKey,
		String idempotencyKey,
		String reason,
		String failureCode,
		String failureMessage,
		LocalDateTime requestedAt,
		LocalDateTime completedAt,
		LocalDateTime failedAt
) {
	public static PaymentRefundResponse from(PaymentRefund paymentRefund) {
		return new PaymentRefundResponse(
				paymentRefund.getId(),
				paymentRefund.getPayment().getId(),
				paymentRefund.getCashAmount(),
				paymentRefund.getPointAmount(),
				paymentRefund.getStatus(),
				paymentRefund.getProviderRefundKey(),
				paymentRefund.getIdempotencyKey(),
				paymentRefund.getReason(),
				paymentRefund.getFailureCode(),
				paymentRefund.getFailureMessage(),
				paymentRefund.getRequestedAt(),
				paymentRefund.getCompletedAt(),
				paymentRefund.getFailedAt()
		);
	}
}
