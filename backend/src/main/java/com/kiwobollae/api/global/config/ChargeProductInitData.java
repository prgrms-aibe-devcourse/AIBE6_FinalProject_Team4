package com.kiwobollae.api.global.config;

import com.kiwobollae.api.payment.entity.ChargeProduct;
import com.kiwobollae.api.payment.repository.ChargeProductRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile({"local", "prod"})
@ConditionalOnProperty(prefix = "app.seed.charge-product", name = "enabled", havingValue = "true",
		matchIfMissing = true)
@Order(2)
@RequiredArgsConstructor
public class ChargeProductInitData implements ApplicationRunner {

	private final ChargeProductRepository chargeProductRepository;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (chargeProductRepository.count() > 0) {
			return;
		}

		chargeProductRepository.saveAll(List.of(
				chargeProduct("1,000P 충전", 1_000L, 1_000L),
				chargeProduct("5,000P 충전", 5_000L, 5_000L),
				chargeProduct("10,000P 충전", 10_000L, 10_000L)
		));
	}

	private ChargeProduct chargeProduct(String name, Long price, Long pointAmount) {
		return ChargeProduct.builder()
				.name(name)
				.price(price)
				.pointAmount(pointAmount)
				.isActive(true)
				.build();
	}
}
