package com.kiwobollae.api.commerce.gacha.dto;

import java.util.List;

public record GachaPackPurchaseResponse(
    Long purchaseId,
    Long productId,
    String productName,
    int quantity,
    long unitPoint,
    long totalPoint,
    long usedFreePoint,
    long usedPaidPoint,
    long remainingBalance,
    List<Long> drawIds) {
  public GachaPackPurchaseResponse {
    drawIds = List.copyOf(drawIds);
  }
}
