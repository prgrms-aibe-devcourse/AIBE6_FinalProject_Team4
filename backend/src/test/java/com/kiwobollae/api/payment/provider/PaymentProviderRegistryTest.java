package com.kiwobollae.api.payment.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.payment.entity.enums.PaymentProviderType;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentProviderRegistryTest {

	@Test
	void resolvesDefaultAndRequestedProviderIndependently() {
		PaymentProvider mockProvider = provider(PaymentProviderType.MOCK);
		PaymentProvider tossProvider = provider(PaymentProviderType.TOSS);
		PaymentProviderRegistry registry = new PaymentProviderRegistry(
				List.of(mockProvider, tossProvider),
				PaymentProviderType.TOSS
		);

		assertThat(registry.getDefault()).isSameAs(tossProvider);
		assertThat(registry.resolve(null)).isSameAs(tossProvider);
		assertThat(registry.resolve(PaymentProviderType.MOCK)).isSameAs(mockProvider);
	}

	@Test
	void rejectsProviderUnavailableInCurrentEnvironment() {
		PaymentProviderRegistry registry = new PaymentProviderRegistry(
				List.of(provider(PaymentProviderType.TOSS)),
				PaymentProviderType.TOSS
		);

		assertThatThrownBy(() -> registry.get(PaymentProviderType.MOCK))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode())
								.isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED));
	}

	private PaymentProvider provider(PaymentProviderType providerType) {
		PaymentProvider paymentProvider = mock(PaymentProvider.class);
		when(paymentProvider.getType()).thenReturn(providerType);
		return paymentProvider;
	}
}
