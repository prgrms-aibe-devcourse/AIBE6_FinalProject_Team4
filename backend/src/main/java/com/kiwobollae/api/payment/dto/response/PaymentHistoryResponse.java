package com.kiwobollae.api.payment.dto.response;

import java.util.List;

public record PaymentHistoryResponse(
		PaymentResponse payment,
		List<PaymentRefundResponse> refunds
) {
}
