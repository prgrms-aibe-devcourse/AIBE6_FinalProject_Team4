package com.kiwobollae.api.payment.service;

import com.kiwobollae.api.payment.dto.response.PaymentRefundResponse;
import com.kiwobollae.api.payment.provider.PaymentRefundCommand;

public record PaymentRefundPreparation(
		Long userId,
		String idempotencyKey,
		String requestHash,
		Long paymentId,
		Long refundId,
		Long attemptId,
		Long pointAmount,
		PaymentRefundCommand command,
		PaymentRefundResponse replayResponse
) {

	static PaymentRefundPreparation pending(
			Long userId,
			String idempotencyKey,
			String requestHash,
			Long paymentId,
			Long refundId,
			Long attemptId,
			Long pointAmount,
			PaymentRefundCommand command
	) {
		return new PaymentRefundPreparation(
				userId,
				idempotencyKey,
				requestHash,
				paymentId,
				refundId,
				attemptId,
				pointAmount,
				command,
				null
		);
	}

	static PaymentRefundPreparation replay(PaymentRefundResponse response) {
		return new PaymentRefundPreparation(null, null, null, null, null, null, null, null, response);
	}

	boolean replay() {
		return replayResponse != null;
	}
}
