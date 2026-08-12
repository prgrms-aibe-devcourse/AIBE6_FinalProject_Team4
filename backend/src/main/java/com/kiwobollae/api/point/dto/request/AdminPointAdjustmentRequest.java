package com.kiwobollae.api.point.dto.request;

import com.kiwobollae.api.point.entity.enums.AdminPointAdjustmentReason;
import com.kiwobollae.api.point.entity.enums.CurrencyType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AdminPointAdjustmentRequest(
		@NotNull @Positive Long userId,
		@NotNull CurrencyType currencyType,
		@NotNull Long amount,
		@Schema(
				description = "조정 사유. 지급은 SPECIAL_EVENT/OUTSTANDING_MEMBER, 차감은 FRAUD_PENALTY",
				requiredMode = Schema.RequiredMode.REQUIRED
		)
		AdminPointAdjustmentReason adjustmentReason
) {
}
