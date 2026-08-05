package com.kiwobollae.api.commerce.gacha.dto;

import com.kiwobollae.api.commerce.gacha.entity.GachaDraw;
import com.kiwobollae.api.commerce.gacha.entity.enums.GachaDrawStatus;
import com.kiwobollae.api.commerce.gacha.entity.enums.GachaSourceType;
import java.time.LocalDateTime;

public record AdminGachaDrawResponse(
    Long drawId,
    Long userId,
    String userNickname,
    GachaSourceType sourceType,
    Long sourceId,
    GachaDrawStatus status,
    Integer drawCount,
    Integer attemptCount,
    String lastErrorCode,
    LocalDateTime nextRetryAt,
    LocalDateTime resultViewedAt,
    LocalDateTime completedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static AdminGachaDrawResponse from(GachaDraw draw) {
    return new AdminGachaDrawResponse(
        draw.getId(),
        draw.getUser().getId(),
        draw.getUser().getNickname(),
        draw.getSourceType(),
        draw.getSourceId(),
        draw.getStatus(),
        draw.getDrawCount(),
        draw.getAttemptCount(),
        draw.getLastErrorCode(),
        draw.getNextRetryAt(),
        draw.getResultViewedAt(),
        draw.getCompletedAt(),
        draw.getCreatedAt(),
        draw.getUpdatedAt());
  }
}
