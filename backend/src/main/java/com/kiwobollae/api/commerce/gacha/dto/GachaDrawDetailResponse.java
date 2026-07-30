package com.kiwobollae.api.commerce.gacha.dto;

import com.kiwobollae.api.commerce.gacha.entity.GachaDraw;
import com.kiwobollae.api.commerce.gacha.entity.enums.GachaDrawStatus;
import com.kiwobollae.api.commerce.gacha.entity.enums.GachaSourceType;
import java.time.LocalDateTime;
import java.util.List;

public record GachaDrawDetailResponse(
    Long drawId,
    GachaDrawStatus status,
    GachaSourceType sourceType,
    Integer rateVersion,
    LocalDateTime createdAt,
    LocalDateTime completedAt,
    LocalDateTime resultViewedAt,
    List<GachaDrawItemResponse> items) {
  public static GachaDrawDetailResponse from(GachaDraw draw, List<GachaDrawItemResponse> items) {
    return new GachaDrawDetailResponse(
        draw.getId(),
        draw.getStatus(),
        draw.getSourceType(),
        draw.getRateVersion(),
        draw.getCreatedAt(),
        draw.getCompletedAt(),
        draw.getResultViewedAt(),
        items);
  }
}
