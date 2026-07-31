package com.kiwobollae.api.payment.provider;

public record PaymentRefundResult(
		boolean successful,
		String refundKey,
		String message
) {
	public static PaymentRefundResult success(String refundKey) {
		return new PaymentRefundResult(true, refundKey, "환불이 완료되었습니다.");
	}

	public static PaymentRefundResult failure(String message) {
		return new PaymentRefundResult(false, null, message);
	}
}
