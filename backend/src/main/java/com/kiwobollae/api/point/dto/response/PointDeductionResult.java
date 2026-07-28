package com.kiwobollae.api.point.dto.response;

public record PointDeductionResult(
		Long usedFreePoint,
		Long usedPaidPoint,
		Long remainingBalance
) {
}
