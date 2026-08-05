package com.kiwobollae.api.global.config;

import com.kiwobollae.api.commerce.entity.Card;
import com.kiwobollae.api.commerce.entity.ExchangeProduct;
import com.kiwobollae.api.commerce.entity.enums.ActiveStatus;
import com.kiwobollae.api.commerce.repository.CardRepository;
import com.kiwobollae.api.commerce.repository.ExchangeProductRepository;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local-only sample cards and exchange products for card screen development.
 *
 * <p>Disable without changing code by setting {@code app.seed.card.enabled=false}.
 */
@Component
@Profile({"local", "prod"})
@ConditionalOnProperty(prefix = "app.seed.card", name = "enabled", havingValue = "true")
@Order(2)
@RequiredArgsConstructor
public class CardInitData implements ApplicationRunner {

	private static final String PRODUCT_IMAGE_BASE_URL = "https://placehold.co/800x600/FFF3CC/8A6D00?text=";
	private static final List<String> COUPON_IMAGE_KEYS = List.of(
			"coupons/1/5d085536-b249-56bf-b42f-82e56bd785dd.png",
			"coupons/2/a4d206ed-e57c-57aa-b347-6a633f1f08b4.png",
			"coupons/3/817208ec-104b-5e78-8d90-84ebeab76ffe.png",
			"coupons/4/81c3e0a4-e115-5cc9-a21e-5accc4504cbd.png",
			"coupons/5/64303f95-e437-5ee9-8fb0-d9bccefe3a1b.png",
			"coupons/6/0a0fac0b-b987-5240-8802-e13c719b6475.png",
			"coupons/7/c14efdb8-491e-546d-b234-15ee6ef863b1.png",
			"coupons/8/6ef1c07a-be33-5e1d-a6c2-75427e43e13e.png",
			"coupons/9/cda323a0-8b66-5071-9730-34c3cad1ea16.png",
			"coupons/10/a5a1f56f-5b64-5df6-b955-9df23797ca9f.png"
	);

	private final CardRepository cardRepository;
	private final ExchangeProductRepository exchangeProductRepository;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (cardRepository.count() > 0 || exchangeProductRepository.count() > 0) {
			return;
		}

		List<CardSeed> seeds = List.of(
				new CardSeed("수박 쿠폰", 300L, 5, "제철 수박 한 통", 8, "시원하고 달콤한 여름 수박입니다.", "Watermelon"),
				new CardSeed("방울토마토 쿠폰", 200L, 4, "방울토마토 1kg", 12, "농장에서 갓 수확한 방울토마토입니다.", "Cherry+Tomato"),
				new CardSeed("설향 딸기 쿠폰", 350L, 6, "설향 딸기 한 팩", 5, "향긋하고 달콤한 설향 딸기입니다.", "Strawberry"),
				new CardSeed("유기농 당근 쿠폰", 150L, 3, "유기농 당근 1kg", 10, "아삭한 식감의 유기농 당근입니다.", "Carrot"),
				new CardSeed("수미감자 쿠폰", 180L, 5, "수미감자 2kg", 20, "포슬포슬한 식감의 수미감자입니다.", "Potato"),
				new CardSeed("샤인머스캣 쿠폰", 450L, 8, "샤인머스캣 한 송이", 4, "달콤하고 향긋한 샤인머스캣입니다.", "Shine+Muscat"),
				new CardSeed("초당옥수수 쿠폰", 250L, 5, "초당옥수수 4개", 9, "생으로도 달콤한 초당옥수수입니다.", "Sweet+Corn"),
				new CardSeed("꿀고구마 쿠폰", 220L, 4, "꿀고구마 2kg", 14, "구우면 더욱 달콤해지는 꿀고구마입니다.", "Sweet+Potato"),
				new CardSeed("부사 사과 쿠폰", 280L, 5, "부사 사과 2kg", 11, "아삭하고 새콤달콤한 부사 사과입니다.", "Apple"),
				new CardSeed("제주 감귤 쿠폰", 260L, 5, "제주 감귤 3kg", 0, "제주에서 자란 새콤달콤한 감귤입니다.", "Tangerine")
		);

		List<ExchangeProduct> exchangeProducts = exchangeProductRepository.saveAll(
				seeds.stream()
						.map(this::exchangeProduct)
						.toList()
		);

		cardRepository.saveAll(
				IntStream.range(0, seeds.size())
						.mapToObj(index -> card(
								seeds.get(index),
								exchangeProducts.get(index),
								COUPON_IMAGE_KEYS.get(index)
						))
						.toList()
		);
	}

	private ExchangeProduct exchangeProduct(CardSeed seed) {
		return ExchangeProduct.builder()
				.name(seed.exchangeProductName())
				.stock(seed.exchangeProductStock())
				.description(seed.exchangeProductDescription())
				.imageUrl(PRODUCT_IMAGE_BASE_URL + seed.imageText())
				.status(ActiveStatus.ON_SALE)
				.build();
	}

	private Card card(CardSeed seed, ExchangeProduct exchangeProduct, String imageKey) {
		return Card.builder()
				.name(seed.cardName())
				.pointPrice(seed.pointPrice())
				.exchangeProduct(exchangeProduct)
				.requiredCountForExchange(seed.requiredCountForExchange())
				.description(seed.exchangeProductName() + " 교환을 위해 모으는 쿠폰입니다.")
				.imageUrl(imageKey)
				.status(ActiveStatus.ON_SALE)
				.build();
	}

	private record CardSeed(
			String cardName,
			Long pointPrice,
			Integer requiredCountForExchange,
			String exchangeProductName,
			Integer exchangeProductStock,
			String exchangeProductDescription,
			String imageText
	) {
	}
}
