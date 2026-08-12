package com.kiwobollae.api.commerce.cardmarket.dto;

import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;
import java.util.List;

public record CardMarketSellableCardResponse(
    Long cardId,
    String cardName,
    TradingCardRarity rarity,
    String imageUrl,
    int ownedCount,
    int sellableCount,
    List<GoldenInstance> goldenInstances) {

  public record GoldenInstance(Long id, Long originRank, boolean listed) {}
}
