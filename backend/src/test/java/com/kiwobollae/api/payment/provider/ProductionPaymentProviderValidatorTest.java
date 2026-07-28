package com.kiwobollae.api.payment.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

class ProductionPaymentProviderValidatorTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(ProductionPaymentProviderValidator.class);

	@Test
	void productionProfileDefaultsProviderToToss() throws IOException {
		var propertySources = new YamlPropertySourceLoader()
				.load("application-prod", new ClassPathResource("application-prod.yaml"));

		assertThat(propertySources)
				.anySatisfy(propertySource ->
						assertThat(propertySource.getProperty("payment.provider")).isEqualTo("TOSS"));
	}

	@Test
	void rejectsMockProviderWhenProductionProfileIsActive() {
		contextRunner
				.withPropertyValues(
						"spring.profiles.active=prod",
						"payment.provider=MOCK"
				)
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure())
							.hasRootCauseInstanceOf(IllegalStateException.class)
							.hasRootCauseMessage("운영 환경에서는 Mock 결제 프로바이더를 사용할 수 없습니다.");
				});
	}

	@Test
	void acceptsTossProviderWhenProductionProfileIsActive() {
		contextRunner
				.withPropertyValues(
						"spring.profiles.active=prod",
						"payment.provider=TOSS"
				)
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).hasSingleBean(ProductionPaymentProviderValidator.class);
				});
	}

	@Test
	void doesNotApplyProductionValidatorToLocalMockProvider() {
		contextRunner
				.withPropertyValues(
						"spring.profiles.active=local",
						"payment.provider=MOCK"
				)
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).doesNotHaveBean(ProductionPaymentProviderValidator.class);
				});
	}

	@Test
	void doesNotLoadMockProviderWhenProductionProfileIsActive() {
		new ApplicationContextRunner()
				.withUserConfiguration(MockPaymentProvider.class)
				.withPropertyValues(
						"spring.profiles.active=prod",
						"payment.provider=MOCK"
				)
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).doesNotHaveBean(MockPaymentProvider.class);
				});
	}
}
