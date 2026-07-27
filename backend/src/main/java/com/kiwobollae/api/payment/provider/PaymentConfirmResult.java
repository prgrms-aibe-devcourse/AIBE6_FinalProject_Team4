package com.kiwobollae.api.payment.provider;

public record PaymentConfirmResult(
		PaymentScenario result,
		String message
) {
	public static PaymentConfirmResult success() {
		return new PaymentConfirmResult(PaymentScenario.SUCCESS, "결제가 승인되었습니다.");
	}

	public static PaymentConfirmResult failure() {
		return new PaymentConfirmResult(PaymentScenario.FAILURE, "Mock 결제가 실패했습니다.");
	}

	public static PaymentConfirmResult canceled() {
		return new PaymentConfirmResult(PaymentScenario.CANCEL, "Mock 결제가 취소되었습니다.");
	}
}
