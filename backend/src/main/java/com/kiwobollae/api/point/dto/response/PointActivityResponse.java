package com.kiwobollae.api.point.dto.response;

import com.kiwobollae.api.point.entity.enums.AdminPointAdjustmentReason;
import com.kiwobollae.api.point.entity.enums.PointRefType;
import com.kiwobollae.api.point.entity.enums.PointTxType;
import com.kiwobollae.api.point.repository.projection.PointActivityProjection;
import java.time.LocalDateTime;

public record PointActivityResponse(
		Long id,
		PointTxType type,
		PointRefType refType,
		Long refId,
		AdminPointAdjustmentReason adjustmentReason,
		Long amount,
		Long paidAmount,
		Long freeAmount,
		Long paidBalanceAfter,
		Long freeBalanceAfter,
		LocalDateTime createdAt
) {
	public static PointActivityResponse from(PointActivityProjection projection) {
		return new PointActivityResponse(
				projection.getId(),
				PointTxType.valueOf(projection.getType()),
				projection.getRefType() == null
						? null
						: PointRefType.valueOf(projection.getRefType()),
				projection.getRefId(),
				projection.getAdjustmentReason() == null
						? null
						: AdminPointAdjustmentReason.valueOf(projection.getAdjustmentReason()),
				projection.getAmount(),
				projection.getPaidAmount(),
				projection.getFreeAmount(),
				projection.getPaidBalanceAfter(),
				projection.getFreeBalanceAfter(),
				projection.getCreatedAt()
		);
	}
}
