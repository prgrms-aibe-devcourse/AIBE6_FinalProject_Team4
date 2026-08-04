package com.kiwobollae.api.global.config;

import com.kiwobollae.api.commerce.entity.Product;
import com.kiwobollae.api.commerce.entity.enums.ProductCategory;
import com.kiwobollae.api.commerce.entity.enums.ProductStatus;
import com.kiwobollae.api.commerce.repository.ProductRepository;
import com.kiwobollae.api.content.entity.PlantSpecies;
import com.kiwobollae.api.content.repository.PlantSpeciesRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
 *
 * <p>Ordered before PlantProfileInitData (see @Order) so the plant species it
 * seeds here already exist when profiles are created against them.
 */
@Component
@Profile({"local", "prod"})
@ConditionalOnProperty(prefix = "app.seed.product", name = "enabled", havingValue = "true")
@Order(2)
@RequiredArgsConstructor
public class ProductInitData implements ApplicationRunner {

	private static final String PLACEHOLDER_BASE_URL = "https://placehold.co/800x600/E8F5E9/2E7D32?text=";

	private final ProductRepository productRepository;
	private final PlantSpeciesRepository plantSpeciesRepository;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (productRepository.count() > 0) {
			return;
		}

		Map<String, PlantSpecies> plants = preparePlantSpecies();

		productRepository.saveAll(List.of(
				product(
						"방울토마토 홈가드닝 키트",
						ProductCategory.KIT,
						2500L,
						18,
						null,
						"베란다에서도 방울토마토를 시작할 수 있는 화분, 배양토, 씨앗 구성의 입문 키트입니다.",
						"Tomato+Kit"
				),
				product(
						"향긋한 바질 씨앗 키트",
						ProductCategory.KIT,
						1800L,
						24,
						null,
						"요리에 바로 활용하기 좋은 바질을 씨앗부터 키워보는 미니 재배 키트입니다.",
						"Basil+Kit"
				),
				product(
						"청상추 미니 텃밭 키트",
						ProductCategory.KIT,
						2200L,
						15,
						null,
						"실내 창가에서 잎채소를 손쉽게 키울 수 있도록 필요한 재료를 담았습니다.",
						"Lettuce+Kit"
				),
				product(
						"루꼴라 스타터 키트",
						ProductCategory.KIT,
						2000L,
						0,
						null,
						"쌉싸름한 루꼴라를 집에서 길러 샐러드로 즐길 수 있는 초보자용 키트입니다.",
						"Arugula+Kit"
				),
				product(
						"해바라기 성장 관찰 키트",
						ProductCategory.KIT,
						1500L,
						30,
						null,
						"아이와 함께 발아부터 개화까지 관찰하기 좋은 교육용 해바라기 키트입니다.",
						"Sunflower+Kit"
				),
				product(
						"스위트 바질 모종",
						ProductCategory.SEEDLING,
						900L,
						20,
						plants.get("스위트 바질"),
						"향이 풍부하고 생육이 빠른 스위트 바질 모종입니다.",
						"Sweet+Basil"
				),
				product(
						"방울토마토 모종",
						ProductCategory.SEEDLING,
						1200L,
						12,
						plants.get("방울토마토"),
						"햇빛이 드는 베란다에서 키우기 좋은 방울토마토 모종입니다.",
						"Cherry+Tomato"
				),
				product(
						"아삭한 청상추 모종",
						ProductCategory.SEEDLING,
						700L,
						35,
						plants.get("청상추"),
						"수확까지 비교적 짧아 처음 텃밭을 시작할 때 좋은 청상추 모종입니다.",
						"Green+Lettuce"
				),
				product(
						"향긋한 로즈마리 모종",
						ProductCategory.SEEDLING,
						1100L,
						8,
						plants.get("로즈마리"),
						"요리와 방향용으로 활용할 수 있는 향긋한 로즈마리 모종입니다.",
						"Rosemary"
				),
				product(
						"설향 딸기 모종",
						ProductCategory.SEEDLING,
						1500L,
						10,
						plants.get("설향 딸기"),
						"가정에서 달콤한 열매를 수확해 볼 수 있는 설향 딸기 모종입니다.",
						"Strawberry"
				)
		));
	}

	private Map<String, PlantSpecies> preparePlantSpecies() {
		Map<String, PlantSpecies> plants = plantSpeciesRepository.findAll().stream()
				.collect(Collectors.toMap(
						PlantSpecies::getName,
						Function.identity(),
						(existing, ignored) -> existing,
						LinkedHashMap::new
				));

		addPlantIfMissing(
				plants,
				"스위트 바질",
				"HERB",
				"햇빛이 잘 드는 곳에 두고 겉흙이 마르면 물을 주세요. 잎 끝을 자주 수확하면 풍성해집니다."
		);
		addPlantIfMissing(
				plants,
				"방울토마토",
				"FRUIT_VEGETABLE",
				"하루 6시간 이상 햇빛을 보여주고 흙이 마르기 전에 충분히 물을 주세요. 자라면 지지대를 세워주세요."
		);
		addPlantIfMissing(
				plants,
				"청상추",
				"LEAF_VEGETABLE",
				"서늘하고 밝은 곳에서 키우며 흙을 촉촉하게 유지하세요. 바깥 잎부터 수확하면 오래 먹을 수 있습니다."
		);
		addPlantIfMissing(
				plants,
				"로즈마리",
				"HERB",
				"통풍과 햇빛이 좋은 곳에 두고 흙이 충분히 마른 뒤 물을 주세요. 과습에 특히 주의하세요."
		);
		addPlantIfMissing(
				plants,
				"설향 딸기",
				"FRUIT",
				"햇빛이 잘 드는 곳에서 키우고 꽃이 피면 가볍게 흔들어 수분을 도와주세요. 흙은 촉촉하게 유지하세요."
		);

		return plants;
	}

	private void addPlantIfMissing(
			Map<String, PlantSpecies> plants,
			String name,
			String category,
			String careGuide
	) {
		plants.computeIfAbsent(name, ignored -> plantSpeciesRepository.save(
				PlantSpecies.builder()
						.name(name)
						.category(category)
						.careGuide(careGuide)
						.build()
		));
	}

	private Product product(
			String name,
			ProductCategory category,
			Long pointPrice,
			Integer stock,
			PlantSpecies plant,
			String description,
			String imageText
	) {
		return Product.builder()
				.name(name)
				.category(category)
				.pointPrice(pointPrice)
				.stock(stock)
				.plant(plant)
				.description(description)
				.imageUrl(PLACEHOLDER_BASE_URL + imageText)
				.status(ProductStatus.ACTIVE)
				.build();
	}
}
