package com.kiwobollae.api.commerce.gacha.dto;

import com.kiwobollae.api.commerce.gacha.entity.GachaDraw;
import com.kiwobollae.api.commerce.gacha.entity.enums.GachaDrawStatus;
import com.kiwobollae.api.commerce.gacha.entity.enums.GachaSourceType;
import java.time.LocalDateTime;

public record GachaDrawSummaryResponse(
    Long drawId,
    GachaDrawStatus status,
    GachaSourceType sourceType,
    Integer drawCount,
    LocalDateTime createdAt,
    LocalDateTime completedAt,
    LocalDateTime resultViewedAt) {
  public static GachaDrawSummaryResponse from(GachaDraw draw) {
    return new GachaDrawSummaryResponse(
        draw.getId(),
        draw.getStatus(),
        draw.getSourceType(),
        draw.getDrawCount(),
        draw.getCreatedAt(),
        draw.getCompletedAt(),
        draw.getResultViewedAt());
  }
}
