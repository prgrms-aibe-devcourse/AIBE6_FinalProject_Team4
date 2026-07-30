package com.kiwobollae.api.commerce.gacha.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kiwobollae.api.commerce.gacha.entity.GachaDrawItem;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;

public record GachaDrawItemResponse(
    Integer sequence,
    Long cardId,
    String code,
    String name,
    String imageUrl,
    TradingCardRarity rolledRarity,
    TradingCardRarity finalRarity,
    boolean downgraded,
    @JsonProperty("new") boolean newCard,
    Integer ownedCountAfter,
    Integer nextMilestone,
    Long goldenOriginRank) {
  public static GachaDrawItemResponse from(
      GachaDrawItem item, String imageUrl, Integer nextMilestone) {
    return new GachaDrawItemResponse(
        item.getDrawSeq(),
        item.getCard().getId(),
        item.getCard().getCode(),
        item.getCard().getName(),
        imageUrl,
        item.getRolledRarity(),
        item.getFinalRarity(),
        item.getRolledRarity() != item.getFinalRarity(),
        item.getOwnedCountAfter() == 1,
        item.getOwnedCountAfter(),
        nextMilestone,
        item.getGoldenInstance() == null ? null : item.getGoldenInstance().getGoldenOriginRank());
  }
}
