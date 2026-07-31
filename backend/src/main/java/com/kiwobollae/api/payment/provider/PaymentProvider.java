package com.kiwobollae.api.payment.provider;

import com.kiwobollae.api.payment.entity.enums.PaymentProviderType;

public interface PaymentProvider {

	PaymentProviderType getType();

	PaymentConfirmResult confirm(PaymentConfirmCommand command);

	PaymentRefundResult refund(PaymentRefundCommand command);
}
