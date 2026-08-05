package com.kiwobollae.api.commerce.gacha.dto;

import com.kiwobollae.api.commerce.gacha.entity.GachaDraw;
import java.util.List;
import org.springframework.data.domain.Page;

public record AdminGachaDrawPageResponse(
    List<AdminGachaDrawResponse> content, int page, int size, long totalElements, int totalPages) {

  public static AdminGachaDrawPageResponse from(Page<GachaDraw> draws) {
    return new AdminGachaDrawPageResponse(
        draws.getContent().stream().map(AdminGachaDrawResponse::from).toList(),
        draws.getNumber(),
        draws.getSize(),
        draws.getTotalElements(),
        draws.getTotalPages());
  }
}
