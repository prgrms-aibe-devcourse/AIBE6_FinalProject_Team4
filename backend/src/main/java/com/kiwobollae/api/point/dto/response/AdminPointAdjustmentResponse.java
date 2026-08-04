package com.kiwobollae.api.point.dto.response;

import com.kiwobollae.api.point.entity.PointTransaction;
import com.kiwobollae.api.point.entity.Wallet;
import com.kiwobollae.api.point.entity.enums.CurrencyType;

public record AdminPointAdjustmentResponse(
		Long transactionId,
		Long userId,
		CurrencyType currencyType,
		Long amount,
		Long balanceAfter,
		Long paidPoint,
		Long freePoint,
		Long balance
) {
	public static AdminPointAdjustmentResponse from(
			Long userId,
			PointTransaction transaction,
			Wallet wallet
	) {
		return new AdminPointAdjustmentResponse(
				transaction.getId(),
				userId,
				transaction.getCurrencyType(),
				transaction.getAmount(),
				transaction.getBalanceAfter(),
				wallet.getPaidPoint(),
				wallet.getFreePoint(),
				wallet.totalBalance()
		);
	}
}
