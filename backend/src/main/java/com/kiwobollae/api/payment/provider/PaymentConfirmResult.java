package com.kiwobollae.api.payment.provider;

public record PaymentConfirmResult(
		boolean successful,
		String message
) {
	public static PaymentConfirmResult success() {
		return new PaymentConfirmResult(true, "결제가 승인되었습니다.");
	}

	public static PaymentConfirmResult failure(String message) {
		return new PaymentConfirmResult(false, message);
	}
}
