package com.kiwobollae.api.commerce.gacha.dto;

import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;

public record GachaTestCardGrantResponse(
    Long cardId,
    String cardName,
    TradingCardRarity rarity,
    int grantedQuantity,
    int ownedCountAfter) {}
