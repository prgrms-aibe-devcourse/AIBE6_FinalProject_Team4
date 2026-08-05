package com.kiwobollae.api.commerce.gacha.dto;

import java.util.List;

public record GachaDismantleResponse(
    long earnedShards, long balance, long lifetimeEarned, List<Item> items) {
  public record Item(
      Long cardId,
      String cardName,
      int quantity,
      int shardPerCard,
      long earnedShards,
      int ownedCountAfter) {}
}
