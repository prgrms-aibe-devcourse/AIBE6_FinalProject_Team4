package com.kiwobollae.api.commerce.cardmarket.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CardMarketListingCreateRequest(
    @NotNull @Min(1) Long cardId,
    @Min(1) Long goldenInstanceId,
    @NotNull @Min(100) @Max(99_999_999) Long askingPrice) {}
