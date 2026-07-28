package com.kiwobollae.api.payment.provider;

import com.kiwobollae.api.payment.entity.enums.PaymentProviderType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!prod")
@ConditionalOnProperty(prefix = "payment", name = "provider", havingValue = "MOCK", matchIfMissing = true)
public class MockPaymentProvider implements PaymentProvider {

	@Override
	public PaymentProviderType getType() {
		return PaymentProviderType.MOCK;
	}

	@Override
	public PaymentConfirmResult confirm(PaymentConfirmCommand command) {
		return switch (command.scenario()) {
			case SUCCESS -> PaymentConfirmResult.success();
			case FAILURE -> PaymentConfirmResult.failure();
			case CANCEL -> PaymentConfirmResult.canceled();
		};
	}
}
