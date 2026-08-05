package com.kiwobollae.api.commerce.dto.response;

import com.kiwobollae.api.commerce.entity.Card;
import com.kiwobollae.api.commerce.entity.enums.ActiveStatus;
import java.time.LocalDateTime;

public record AdminCardResponse(
    Long id,
    String name,
    Long pointPrice,
    Long exchangeProductId,
    String exchangeProductName,
    Integer requiredCountForExchange,
    String description,
    String imageKey,
    String imageUrl,
    ActiveStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static AdminCardResponse from(Card card, String imageUrl) {
    return new AdminCardResponse(
        card.getId(),
        card.getName(),
        card.getPointPrice(),
        card.getExchangeProduct().getId(),
        card.getExchangeProduct().getName(),
        card.getRequiredCountForExchange(),
        card.getDescription(),
        card.getImageUrl(),
        imageUrl,
        card.getStatus(),
        card.getCreatedAt(),
        card.getUpdatedAt());
  }
}
