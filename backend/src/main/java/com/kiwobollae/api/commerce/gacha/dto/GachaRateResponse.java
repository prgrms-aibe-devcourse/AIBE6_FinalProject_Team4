package com.kiwobollae.api.commerce.gacha.dto;

import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;
import java.math.BigDecimal;
import java.util.List;

public record GachaRateResponse(
    int rateVersion,
    int drawCount,
    int totalWeight,
    List<RarityRate> rarities,
    List<String> notices) {
  public record RarityRate(TradingCardRarity rarity, int weight, BigDecimal percent) {}
}
