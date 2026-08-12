package com.kiwobollae.api.commerce.gacha.dto;

import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record GachaTestCardGrantRequest(
    @NotNull TradingCardRarity rarity, @Min(1) @Max(2) int quantity) {}
