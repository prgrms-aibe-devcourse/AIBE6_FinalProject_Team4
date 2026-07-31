package com.kiwobollae.api.global.config;

import com.kiwobollae.api.commerce.entity.Product;
import com.kiwobollae.api.commerce.entity.enums.ProductCategory;
import com.kiwobollae.api.commerce.entity.enums.ProductStatus;
import com.kiwobollae.api.commerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local")
@ConditionalOnProperty(prefix = "app.seed.gacha-pack", name = "enabled", havingValue = "true")
@Order(3)
@RequiredArgsConstructor
public class GachaPackProductInitData implements ApplicationRunner {

	private static final String PACK_IMAGE =
			"/cards/900001/0005fbe2-236e-5543-a4d4-69f8b57bd3f7.svg";

	private final ProductRepository productRepository;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (productRepository.existsByCategory(ProductCategory.GACHA_PACK)) {
			return;
		}
		productRepository.save(
				Product.builder()
						.name("시즌 1 가챠 카드팩")
						.category(ProductCategory.GACHA_PACK)
						.pointPrice(100L)
						.stock(100)
						.description("식물 캐릭터 카드 5장이 즉시 개봉되는 시즌 1 카드팩입니다.")
						.imageUrl(PACK_IMAGE)
						.status(ProductStatus.ACTIVE)
						.build()
		);
	}
}
