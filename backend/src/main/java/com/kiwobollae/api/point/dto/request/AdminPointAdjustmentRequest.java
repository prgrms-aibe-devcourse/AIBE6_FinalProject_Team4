package com.kiwobollae.api.point.dto.request;

import com.kiwobollae.api.point.entity.enums.CurrencyType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AdminPointAdjustmentRequest(
		@NotNull @Positive Long userId,
		@NotNull CurrencyType currencyType,
		@NotNull Long amount
) {
}
