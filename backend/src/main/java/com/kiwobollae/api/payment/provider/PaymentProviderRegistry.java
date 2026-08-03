package com.kiwobollae.api.payment.provider;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.payment.entity.enums.PaymentProviderType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PaymentProviderRegistry {

	private final Map<PaymentProviderType, PaymentProvider> providers;
	private final PaymentProviderType defaultProviderType;

	public PaymentProviderRegistry(
			List<PaymentProvider> paymentProviders,
			@Value("${payment.provider:MOCK}") PaymentProviderType defaultProviderType
	) {
		this.providers = new EnumMap<>(PaymentProviderType.class);
		for (PaymentProvider paymentProvider : paymentProviders) {
			PaymentProvider previous = providers.put(paymentProvider.getType(), paymentProvider);
			if (previous != null) {
				throw new IllegalStateException("같은 유형의 결제 Provider가 중복 등록되었습니다.");
			}
		}
		this.defaultProviderType = defaultProviderType;
		if (!providers.containsKey(defaultProviderType)) {
			throw new IllegalStateException("기본 결제 Provider가 등록되지 않았습니다: " + defaultProviderType);
		}
	}

	public PaymentProvider getDefault() {
		return get(defaultProviderType);
	}

	public PaymentProvider get(PaymentProviderType providerType) {
		PaymentProvider paymentProvider = providers.get(providerType);
		if (paymentProvider == null) {
			throw new BusinessException(
					ErrorCode.COMMON_VALIDATION_FAILED,
					"현재 환경에서 지원하지 않는 결제 방식입니다."
			);
		}
		return paymentProvider;
	}

	public PaymentProvider resolve(PaymentProviderType requestedProviderType) {
		return requestedProviderType == null ? getDefault() : get(requestedProviderType);
	}
}
