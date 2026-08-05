package com.kiwobollae.api.commerce.gacha.dto;

import java.util.List;

public record GachaDismantleRequest(List<Item> items) {
  public record Item(Long cardId, Integer quantity) {}
}
