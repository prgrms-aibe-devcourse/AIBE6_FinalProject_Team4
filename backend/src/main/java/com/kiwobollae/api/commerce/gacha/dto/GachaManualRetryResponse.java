package com.kiwobollae.api.commerce.gacha.dto;

import com.kiwobollae.api.commerce.gacha.entity.enums.GachaDrawStatus;

public record GachaManualRetryResponse(Long drawId, GachaDrawStatus status) {}
