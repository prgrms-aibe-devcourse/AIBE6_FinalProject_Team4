package com.kiwobollae.api.payment.service;

import com.kiwobollae.api.payment.dto.response.PaymentResponse;
import com.kiwobollae.api.payment.provider.PaymentConfirmCommand;

public record PaymentConfirmationPreparation(
		Long userId,
		String idempotencyKey,
		String requestHash,
		Long paymentId,
		PaymentConfirmCommand command,
		PaymentResponse replayResponse
) {

	static PaymentConfirmationPreparation pending(
			Long userId,
			String idempotencyKey,
			String requestHash,
			Long paymentId,
			PaymentConfirmCommand command
	) {
		return new PaymentConfirmationPreparation(
				userId,
				idempotencyKey,
				requestHash,
				paymentId,
				command,
				null
		);
	}

	static PaymentConfirmationPreparation replay(PaymentResponse replayResponse) {
		return new PaymentConfirmationPreparation(null, null, null, null, null, replayResponse);
	}

	boolean replay() {
		return replayResponse != null;
	}
}
