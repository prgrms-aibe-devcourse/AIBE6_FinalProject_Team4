package com.kiwobollae.api.point.dto.response;

import com.kiwobollae.api.point.entity.PointTransaction;
import com.kiwobollae.api.point.entity.enums.CurrencyType;
import com.kiwobollae.api.point.entity.enums.PointRefType;
import com.kiwobollae.api.point.entity.enums.PointTxType;
import java.time.LocalDateTime;

public record PointTransactionResponse(
		Long id,
		Long userId,
		Long walletId,
		PointTxType type,
		CurrencyType currencyType,
		Long amount,
		Long balanceBefore,
		Long balanceAfter,
		PointRefType refType,
		Long refId,
		Long actorUserId,
		String reason,
		String idempotencyKey,
		LocalDateTime createdAt
) {
	public static PointTransactionResponse from(PointTransaction pointTransaction) {
		return new PointTransactionResponse(
				pointTransaction.getId(),
				pointTransaction.getUser().getId(),
				pointTransaction.getWallet().getId(),
				pointTransaction.getType(),
				pointTransaction.getCurrencyType(),
				pointTransaction.getAmount(),
				pointTransaction.getBalanceBefore(),
				pointTransaction.getBalanceAfter(),
				pointTransaction.getRefType(),
				pointTransaction.getRefId(),
				pointTransaction.getActorUser() != null ? pointTransaction.getActorUser().getId() : null,
				pointTransaction.getReason(),
				pointTransaction.getIdempotencyKey(),
				pointTransaction.getCreatedAt()
		);
	}
}
