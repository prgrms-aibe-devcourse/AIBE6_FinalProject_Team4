package com.kiwobollae.api.payment.provider;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;

public class PaymentProviderBusyException extends BusinessException {

	public PaymentProviderBusyException() {
		super(ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE);
	}
}
