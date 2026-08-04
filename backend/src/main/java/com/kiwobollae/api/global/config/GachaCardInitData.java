package com.kiwobollae.api.global.config;

import com.kiwobollae.api.commerce.gacha.entity.TradingCard;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardStatus;
import com.kiwobollae.api.commerce.gacha.repository.TradingCardRepository;
import com.kiwobollae.api.commerce.gacha.service.GachaMasterValidator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile({"local", "prod"})
@ConditionalOnProperty(prefix = "app.seed.gacha", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class GachaCardInitData implements ApplicationRunner {

  private static final String SERIES_CODE = "SEASON_01";

  private final TradingCardRepository tradingCardRepository;
  private final GachaMasterValidator masterValidator;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    for (CardSeed seed : seeds()) {
      TradingCard card =
          tradingCardRepository
              .findByCode(seed.code())
              .orElseGet(
                  () ->
                      TradingCard.builder()
                          .seriesCode(SERIES_CODE)
                          .code(seed.code())
                          .name(seed.name())
                          .rarity(seed.rarity())
                          .description(seed.description())
                          .imageKey(seed.imageKey())
                          .drawWeight(seed.weight())
                          .displayOrder(seed.order())
                          .status(TradingCardStatus.ACTIVE)
                          .build());
      card.updateSeed(
          seed.name(),
          seed.rarity(),
          seed.description(),
          seed.imageKey(),
          seed.weight(),
          seed.order());
      TradingCard saved = tradingCardRepository.saveAndFlush(card);
      validateImageKeyUsesDatabaseId(saved, seed.imageKey());
    }
    masterValidator.validate(
        tradingCardRepository.findAllByStatusOrderByDisplayOrderAsc(TradingCardStatus.ACTIVE));
  }

  private void validateImageKeyUsesDatabaseId(TradingCard card, String imageKey) {
    String expectedPrefix = "cards/" + card.getId() + "/";
    if (imageKey == null || !imageKey.startsWith(expectedPrefix)) {
      throw new IllegalStateException(
          "Gacha card image path must use its database id. cardId="
              + card.getId()
              + ", imageKey="
              + imageKey);
    }
  }

  private List<CardSeed> seeds() {
    return List.of(
        seed(
            1,
            "common_cabbage",
            "양배추",
            TradingCardRarity.COMMON,
            98_000,
            "cards/1/8d09cd4e-6956-539c-a858-2f146d206fb3.png"),
        seed(
            2,
            "common_carrot",
            "당근",
            TradingCardRarity.COMMON,
            98_000,
            "cards/2/73a2d109-2d8f-58fd-8172-0a93f2e0cf1b.png"),
        seed(
            3,
            "common_cherry_tomato",
            "방울토마토",
            TradingCardRarity.COMMON,
            98_000,
            "cards/3/f909b85a-5be6-537d-af72-8dfd3d019ad5.png"),
        seed(
            4,
            "common_chili_pepper",
            "고추",
            TradingCardRarity.COMMON,
            98_000,
            "cards/4/63108783-1e70-56eb-b7ed-519b2b29afbb.png"),
        seed(
            5,
            "common_corn",
            "옥수수",
            TradingCardRarity.COMMON,
            98_000,
            "cards/5/6a79ae47-df92-5105-926b-3323c0804d2f.png"),
        seed(
            6,
            "common_cucumber",
            "오이",
            TradingCardRarity.COMMON,
            98_000,
            "cards/6/f686745a-8d4c-5464-8f5d-234d49bbdf3b.png"),
        seed(
            7,
            "common_green_onion",
            "대파",
            TradingCardRarity.COMMON,
            98_000,
            "cards/7/2f372b19-276f-5966-a655-6b1191792506.png"),
        seed(
            8,
            "common_lettuce",
            "상추",
            TradingCardRarity.COMMON,
            98_000,
            "cards/8/399f7998-3e24-5d1d-b69c-68da63b839ef.png"),
        seed(
            9,
            "common_pea",
            "완두콩",
            TradingCardRarity.COMMON,
            98_000,
            "cards/9/725d9440-6a96-5329-be45-e5d62be2c3c1.png"),
        seed(
            10,
            "common_perilla",
            "깻잎",
            TradingCardRarity.COMMON,
            98_000,
            "cards/10/75f95dea-065e-5e74-ad88-a0c94d3f9854.png"),
        seed(
            11,
            "common_potato",
            "감자",
            TradingCardRarity.COMMON,
            98_000,
            "cards/11/2680e6db-335d-5e37-a128-d984497537b3.png"),
        seed(
            12,
            "common_radish",
            "무",
            TradingCardRarity.COMMON,
            98_000,
            "cards/12/62466e97-406c-513a-9861-1ce1c715cd04.png"),
        seed(
            13,
            "common_spinach",
            "시금치",
            TradingCardRarity.COMMON,
            98_000,
            "cards/13/b7232c3d-5791-5b6e-8d59-99711d8f474e.png"),
        seed(
            14,
            "common_sweet_potato",
            "고구마",
            TradingCardRarity.COMMON,
            98_000,
            "cards/14/f09e0d5f-9ff2-539b-8f1f-60ee096dc2bc.png"),
        seed(
            15,
            "common_zucchini",
            "애호박",
            TradingCardRarity.COMMON,
            98_000,
            "cards/15/3cfafc95-bf72-5ff0-b0bd-6b155d8ac977.png"),
        seed(
            16,
            "rare_asparagus",
            "아스파라거스",
            TradingCardRarity.RARE,
            30_000,
            "cards/16/d39de513-9715-57af-b5de-d6d4c93a745a.png"),
        seed(
            17,
            "rare_basil",
            "바질",
            TradingCardRarity.RARE,
            30_000,
            "cards/17/acdf6f51-4216-5a52-b949-25a989279fde.png"),
        seed(
            18,
            "rare_blueberry",
            "블루베리",
            TradingCardRarity.RARE,
            30_000,
            "cards/18/7686007b-53b5-5155-b828-b040f2e68cd0.png"),
        seed(
            19,
            "rare_broccoli",
            "브로콜리",
            TradingCardRarity.RARE,
            30_000,
            "cards/19/78f6e3d1-9c4c-50be-9912-0f1404ea155c.png"),
        seed(
            20,
            "rare_cauliflower",
            "콜리플라워",
            TradingCardRarity.RARE,
            30_000,
            "cards/20/d5293f7e-c7c7-55bd-a1c6-f24db75c8602.png"),
        seed(
            21,
            "rare_eggplant",
            "가지",
            TradingCardRarity.RARE,
            30_000,
            "cards/21/e9669b23-fba3-5431-8644-74f3f015d7c2.png"),
        seed(
            22,
            "rare_fig",
            "무화과",
            TradingCardRarity.RARE,
            30_000,
            "cards/22/83e7f166-97e8-5ecb-9eda-00383bc54a12.png"),
        seed(
            23,
            "rare_ginger",
            "생강",
            TradingCardRarity.RARE,
            30_000,
            "cards/23/bc0cb20f-beed-549d-8ad8-a055ba3564f9.png"),
        seed(
            24,
            "rare_paprika",
            "파프리카",
            TradingCardRarity.RARE,
            30_000,
            "cards/24/c6cf9e95-a165-52c8-bf83-046ba0f8f742.png"),
        seed(
            25,
            "rare_peanut",
            "땅콩",
            TradingCardRarity.RARE,
            30_000,
            "cards/25/f53a18d7-c1f6-55d4-8c43-956f82ed74fa.png"),
        seed(
            26,
            "rare_pumpkin",
            "호박",
            TradingCardRarity.RARE,
            30_000,
            "cards/26/26be55ab-c750-5286-9f5f-e81a3152b3a5.png"),
        seed(
            27,
            "rare_rosemary",
            "로즈메리",
            TradingCardRarity.RARE,
            30_000,
            "cards/27/60b2d60d-8a59-53f9-ab01-238cec084867.png"),
        seed(
            28,
            "rare_strawberry",
            "딸기",
            TradingCardRarity.RARE,
            30_000,
            "cards/28/67887ea5-a7ca-5b0e-a843-234556e9c162.png"),
        seed(
            29,
            "rare_watermelon",
            "수박",
            TradingCardRarity.RARE,
            30_000,
            "cards/29/03e0a40e-1cc1-56d5-ab5f-d29af7bea2db.png"),
        seed(
            30,
            "super_rare_artichoke",
            "아티초크",
            TradingCardRarity.SUPER_RARE,
            23_625,
            "cards/30/fcf5dfde-2da6-5b42-b67e-66af139a5da3.png"),
        seed(
            31,
            "super_rare_cacao",
            "카카오",
            TradingCardRarity.SUPER_RARE,
            23_625,
            "cards/31/8c26cc7a-16d6-5388-b557-45a225393a00.png"),
        seed(
            32,
            "super_rare_coffee_cherry",
            "커피 체리",
            TradingCardRarity.SUPER_RARE,
            23_625,
            "cards/32/65761d37-e88c-54e7-ba8a-f1741be9a6ea.png"),
        seed(
            33,
            "super_rare_dragon_fruit",
            "용과",
            TradingCardRarity.SUPER_RARE,
            23_625,
            "cards/33/12f05e35-b2d0-5493-89ec-bca670e3f569.png"),
        seed(
            34,
            "super_rare_passion_fruit",
            "패션프루트",
            TradingCardRarity.SUPER_RARE,
            23_625,
            "cards/34/5ea74c98-fe6e-5569-8cab-35c689b7da37.png"),
        seed(
            35,
            "super_rare_saffron_crocus",
            "사프란 크로커스",
            TradingCardRarity.SUPER_RARE,
            23_625,
            "cards/35/dcfb9ba0-b951-548b-8dce-ab00131b07b2.png"),
        seed(
            36,
            "super_rare_vanilla_orchid",
            "바닐라 난초",
            TradingCardRarity.SUPER_RARE,
            23_625,
            "cards/36/f2a736d1-e444-58c1-bf40-b26a4332761c.png"),
        seed(
            37,
            "super_rare_wasabi",
            "와사비",
            TradingCardRarity.SUPER_RARE,
            23_625,
            "cards/37/c7b0d87c-541b-5cf3-ade6-84eb8cb1819f.png"),
        seed(
            38,
            "hyper_rare_apple_mango",
            "애플망고",
            TradingCardRarity.HYPER_RARE,
            6_993,
            "cards/38/cf045f5f-da60-5b20-b6b1-498f7655f336.png"),
        seed(
            39,
            "hyper_rare_shine_muscat",
            "샤인머스켓",
            TradingCardRarity.HYPER_RARE,
            6_993,
            "cards/39/4a9576a7-9e0a-54f4-b004-13fa13f45387.png"),
        seed(
            40,
            "hyper_rare_white_strawberry",
            "화이트 스트로베리",
            TradingCardRarity.HYPER_RARE,
            6_993,
            "cards/40/e2e0430e-1731-55cc-9194-c5dcce51c1e3.png"),
        seed(
            41,
            "golden_rare_golden_sun_corn",
            "황금 태양 옥수수",
            TradingCardRarity.GOLDEN_RARE,
            7,
            "cards/41/f6442e98-d414-576d-bd12-6ced74a9c475.png"),
        seed(
            42,
            "golden_rare_moonlight_tomato",
            "월광 토마토",
            TradingCardRarity.GOLDEN_RARE,
            7,
            "cards/42/4eb1d007-ec5e-5c14-8f81-2f251b923e82.png"),
        seed(
            43,
            "golden_rare_stardust_strawberry",
            "별가루 딸기",
            TradingCardRarity.GOLDEN_RARE,
            7,
            "cards/43/be7b328b-f538-5d38-8a62-5952d2bc5745.png"));
  }

  private CardSeed seed(
      int order, String code, String name, TradingCardRarity rarity, int weight, String imageKey) {
    return new CardSeed(
        order, code, name, rarity, name + "을 모티브로 한 시즌 1 트레이딩 카드입니다.", imageKey, weight);
  }

  private record CardSeed(
      int order,
      String code,
      String name,
      TradingCardRarity rarity,
      String description,
      String imageKey,
      int weight) {}
}
