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
		String reason,
		String refundKey,
		LocalDateTime createdAt,
		LocalDateTime completedAt
) {
	public static PaymentRefundResponse from(PaymentRefund paymentRefund) {
		return new PaymentRefundResponse(
				paymentRefund.getId(),
				paymentRefund.getPayment().getId(),
				paymentRefund.getCashAmount(),
				paymentRefund.getPointAmount(),
				paymentRefund.getStatus(),
				paymentRefund.getReason(),
				paymentRefund.getRefundKey(),
				paymentRefund.getCreatedAt(),
				paymentRefund.getCompletedAt()
		);
	}
}
