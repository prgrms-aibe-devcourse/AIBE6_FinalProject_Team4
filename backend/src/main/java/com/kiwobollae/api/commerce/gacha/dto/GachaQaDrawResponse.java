package com.kiwobollae.api.commerce.gacha.dto;

import com.kiwobollae.api.commerce.gacha.entity.enums.GachaDrawStatus;

public record GachaQaDrawResponse(Long drawId, GachaDrawStatus status) {}
