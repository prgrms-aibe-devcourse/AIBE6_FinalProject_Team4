package com.kiwobollae.api.commerce.gacha.service;

import com.kiwobollae.api.commerce.gacha.entity.enums.GachaCosmeticType;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GachaCosmeticCatalog {

  private static final List<CosmeticDefinition> DEFINITIONS =
      List.of(
          new CosmeticDefinition(
              "TITLE_SPROUT_COLLECTOR",
              "새싹 수집가",
              GachaCosmeticType.TITLE,
              30,
              "title-sprout-collector"),
          new CosmeticDefinition(
              "TITLE_GARDEN_KEEPER", "정원의 수호자", GachaCosmeticType.TITLE, 50, "title-garden-keeper"),
          new CosmeticDefinition(
              "TITLE_CARD_MASTER", "카드 마스터", GachaCosmeticType.TITLE, 80, "title-card-master"),
          new CosmeticDefinition(
              "BORDER_SPROUT_VINE", "풀잎의 숨결", GachaCosmeticType.BORDER, 150, "border-sprout-vine"),
          new CosmeticDefinition(
              "BORDER_BLOOM_GARDEN",
              "벚꽃의 축복",
              GachaCosmeticType.BORDER,
              220,
              "border-bloom-garden"),
          new CosmeticDefinition(
              "BORDER_GOLDEN_HARVEST",
              "황금 사과",
              GachaCosmeticType.BORDER,
              300,
              "border-golden-harvest"));

  public List<CosmeticDefinition> all() {
    return DEFINITIONS;
  }

  public CosmeticDefinition get(String code) {
    return DEFINITIONS.stream()
        .filter(definition -> definition.code().equals(code))
        .findFirst()
        .orElseThrow(() -> new BusinessException(ErrorCode.GACHA_COSMETIC_NOT_FOUND));
  }

  public record CosmeticDefinition(
      String code, String name, GachaCosmeticType type, long price, String styleKey) {}
}
