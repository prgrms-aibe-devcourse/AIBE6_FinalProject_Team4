package com.kiwobollae.api.point.dto.response;

import com.kiwobollae.api.point.entity.PointTransaction;
import com.kiwobollae.api.point.entity.enums.CurrencyType;
import com.kiwobollae.api.point.entity.enums.PointRefType;
import com.kiwobollae.api.point.entity.enums.PointTxType;
import java.time.LocalDateTime;

public record PointTransactionResponse(
		Long id,
		Long walletId,
		PointTxType type,
		CurrencyType currencyType,
		Long amount,
		Long balanceAfter,
		PointRefType refType,
		Long refId,
		LocalDateTime createdAt
) {
	public static PointTransactionResponse from(PointTransaction pointTransaction) {
		return new PointTransactionResponse(
				pointTransaction.getId(),
				pointTransaction.getWallet().getId(),
				pointTransaction.getType(),
				pointTransaction.getCurrencyType(),
				pointTransaction.getAmount(),
				pointTransaction.getBalanceAfter(),
				pointTransaction.getRefType(),
				pointTransaction.getRefId(),
				pointTransaction.getCreatedAt()
		);
	}
}
