package com.kiwobollae.api.payment.provider;

import com.kiwobollae.api.payment.entity.enums.PaymentProviderType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionPaymentProviderValidator {

	public ProductionPaymentProviderValidator(
			@Value("${payment.provider}") PaymentProviderType paymentProvider
	) {
		if (paymentProvider == PaymentProviderType.MOCK) {
			throw new IllegalStateException("운영 환경에서는 Mock 결제 프로바이더를 사용할 수 없습니다.");
		}
	}
}
