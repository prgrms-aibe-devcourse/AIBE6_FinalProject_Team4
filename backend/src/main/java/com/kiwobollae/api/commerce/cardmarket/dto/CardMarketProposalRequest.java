package com.kiwobollae.api.commerce.cardmarket.dto;

import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketMessageCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CardMarketProposalRequest(
    @NotNull @Min(100) @Max(99_999_999) Long price,
    CardMarketMessageCode messageCode) {}
