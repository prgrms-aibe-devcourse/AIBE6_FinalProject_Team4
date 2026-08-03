package com.kiwobollae.api.commerce.gacha.dto;

import com.kiwobollae.api.commerce.gacha.entity.TradingCard;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;

public record GachaCardResponse(
    Long id,
    String code,
    String name,
    TradingCardRarity rarity,
    String description,
    String imageUrl,
    Integer displayOrder) {
  public static GachaCardResponse from(TradingCard card, String imageUrl) {
    return new GachaCardResponse(
        card.getId(),
        card.getCode(),
        card.getName(),
        card.getRarity(),
        card.getDescription(),
        imageUrl,
        card.getDisplayOrder());
  }
}
