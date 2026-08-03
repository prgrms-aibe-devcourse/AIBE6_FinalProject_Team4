package com.kiwobollae.api.commerce.gacha.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record GachaDrawPageResponse(
    List<GachaDrawSummaryResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages) {
  public static GachaDrawPageResponse from(Page<GachaDrawSummaryResponse> result) {
    return new GachaDrawPageResponse(
        result.getContent(),
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }
}
