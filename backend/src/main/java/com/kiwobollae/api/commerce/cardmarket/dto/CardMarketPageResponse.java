package com.kiwobollae.api.commerce.cardmarket.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record CardMarketPageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages) {

  public static <T> CardMarketPageResponse<T> from(Page<T> page) {
    return new CardMarketPageResponse<>(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }
}
