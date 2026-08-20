package com.kiwobollae.api.global.config;

import com.kiwobollae.api.commerce.entity.Product;
import com.kiwobollae.api.commerce.entity.enums.ProductCategory;
import com.kiwobollae.api.commerce.entity.enums.ProductStatus;
import com.kiwobollae.api.commerce.repository.ProductRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local-only sample products for shop development.
 *
 * <p>Disable without changing code by setting {@code app.seed.product.enabled=false}.
 */
@Component
@Profile({"local", "prod"})
@ConditionalOnProperty(prefix = "app.seed.product", name = "enabled", havingValue = "true")
@Order(2)
@RequiredArgsConstructor
public class ProductInitData implements ApplicationRunner {

	private final ProductRepository productRepository;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (productRepository.count() > 0) {
			return;
		}

		productRepository.saveAll(List.of(
				product(
						"방울토마토 홈가드닝 키트",
						ProductCategory.KIT,
						2500L,
						18,
						null,
						"베란다에서도 방울토마토를 시작할 수 있는 화분, 배양토, 씨앗 구성의 입문 키트입니다.",
						"products/1/e9f8dd6d-7692-5c67-9172-7c9a2eeae96b.png"
				),
				product(
						"향긋한 바질 씨앗 키트",
						ProductCategory.KIT,
						1800L,
						24,
						null,
						"요리에 바로 활용하기 좋은 바질을 씨앗부터 키워보는 미니 재배 키트입니다.",
						"products/2/07e36a18-4d17-5b91-b3bd-9a9a13e75516.png"
				),
				product(
						"청상추 미니 텃밭 키트",
						ProductCategory.KIT,
						2200L,
						15,
						null,
						"실내 창가에서 잎채소를 손쉽게 키울 수 있도록 필요한 재료를 담았습니다.",
						"products/3/55ae1ce1-1ff1-5371-8b86-70e2bdd7a50e.png"
				),
				product(
						"루꼴라 스타터 키트",
						ProductCategory.KIT,
						2000L,
						0,
						null,
						"쌉싸름한 루꼴라를 집에서 길러 샐러드로 즐길 수 있는 초보자용 키트입니다.",
						"products/4/b9bdb189-9f98-5636-86ce-d5c9852cf082.png"
				),
				product(
						"해바라기 성장 관찰 키트",
						ProductCategory.KIT,
						1500L,
						30,
						null,
						"아이와 함께 발아부터 개화까지 관찰하기 좋은 교육용 해바라기 키트입니다.",
						"products/5/4a0eeb55-c63e-5f9c-8657-de18de80d689.png"
				),
				product(
						"스위트 바질 모종",
						ProductCategory.SEEDLING,
						900L,
						20,
						"스위트 바질",
						"향이 풍부하고 생육이 빠른 스위트 바질 모종입니다.",
						"products/6/26484928-4c05-592b-a3fc-cb3e76ad3578.png"
				),
				product(
						"방울토마토 모종",
						ProductCategory.SEEDLING,
						1200L,
						12,
						"방울토마토",
						"햇빛이 드는 베란다에서 키우기 좋은 방울토마토 모종입니다.",
						"products/7/682bcb8b-1814-52a4-ba81-d513ea2fcbeb.png"
				),
				product(
						"아삭한 청상추 모종",
						ProductCategory.SEEDLING,
						700L,
						35,
						"청상추",
						"수확까지 비교적 짧아 처음 텃밭을 시작할 때 좋은 청상추 모종입니다.",
						"products/8/64645cda-841a-55f3-893c-cab4715edc87.png"
				),
				product(
						"향긋한 로즈마리 모종",
						ProductCategory.SEEDLING,
						1100L,
						8,
						"로즈마리",
						"요리와 방향용으로 활용할 수 있는 향긋한 로즈마리 모종입니다.",
						"products/9/86294710-c193-5d62-8f4c-2dc50356355f.png"
				),
				product(
						"설향 딸기 모종",
						ProductCategory.SEEDLING,
						1500L,
						10,
						"설향 딸기",
						"가정에서 달콤한 열매를 수확해 볼 수 있는 설향 딸기 모종입니다.",
						"products/10/15e4d877-7091-5900-842e-6365fc9892c2.png"
				)
		));
	}

	private Product product(
			String name,
			ProductCategory category,
			Long pointPrice,
			Integer stock,
			String speciesName,
			String description,
			String imageKey
	) {
		return Product.builder()
				.name(name)
				.category(category)
				.pointPrice(pointPrice)
				.stock(stock)
				.speciesName(speciesName)
				.description(description)
				.imageUrl(imageKey)
				.status(ProductStatus.ACTIVE)
				.build();
	}
}
