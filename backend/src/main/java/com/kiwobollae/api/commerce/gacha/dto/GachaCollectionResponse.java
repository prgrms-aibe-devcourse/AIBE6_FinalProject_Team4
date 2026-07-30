package com.kiwobollae.api.commerce.gacha.dto;

import com.kiwobollae.api.commerce.gacha.entity.TradingCard;
import com.kiwobollae.api.commerce.gacha.entity.UserCardCollection;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;

public record GachaCollectionResponse(
    Long id,
    String code,
    String name,
    TradingCardRarity rarity,
    String description,
    String imageUrl,
    Integer displayOrder,
    Integer ownedCount,
    boolean owned,
    boolean unlocked,
    boolean goldenGachaAcquired) {
  public static GachaCollectionResponse from(
      TradingCard card, UserCardCollection collection, String imageUrl) {
    int ownedCount = collection == null ? 0 : collection.getOwnedCount();
    return new GachaCollectionResponse(
        card.getId(),
        card.getCode(),
        card.getName(),
        card.getRarity(),
        card.getDescription(),
        imageUrl,
        card.getDisplayOrder(),
        ownedCount,
        ownedCount > 0,
        collection != null,
        collection != null && collection.getGoldenGachaAcquiredAt() != null);
  }
}
