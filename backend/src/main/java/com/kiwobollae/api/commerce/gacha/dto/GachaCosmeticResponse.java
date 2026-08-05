package com.kiwobollae.api.commerce.gacha.dto;

import com.kiwobollae.api.commerce.gacha.entity.UserCardCosmetic;
import com.kiwobollae.api.commerce.gacha.entity.enums.GachaCosmeticType;
import com.kiwobollae.api.commerce.gacha.service.GachaCosmeticCatalog.CosmeticDefinition;
import java.time.LocalDateTime;

public record GachaCosmeticResponse(
    String code,
    String name,
    GachaCosmeticType type,
    long price,
    String styleKey,
    boolean owned,
    boolean equipped,
    LocalDateTime unlockedAt) {
  public static GachaCosmeticResponse from(
      CosmeticDefinition definition, UserCardCosmetic ownedCosmetic) {
    return new GachaCosmeticResponse(
        definition.code(),
        definition.name(),
        definition.type(),
        definition.price(),
        definition.styleKey(),
        ownedCosmetic != null,
        ownedCosmetic != null && ownedCosmetic.getEquippedAt() != null,
        ownedCosmetic == null ? null : ownedCosmetic.getUnlockedAt());
  }
}
