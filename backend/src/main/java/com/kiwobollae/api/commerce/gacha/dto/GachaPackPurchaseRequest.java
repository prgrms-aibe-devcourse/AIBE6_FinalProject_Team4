package com.kiwobollae.api.commerce.gacha.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record GachaPackPurchaseRequest(
    @NotNull Long productId,
    @NotNull @Min(1) @Max(1) Integer quantity,
    @PositiveOrZero Long expectedUnitPoint) {}
