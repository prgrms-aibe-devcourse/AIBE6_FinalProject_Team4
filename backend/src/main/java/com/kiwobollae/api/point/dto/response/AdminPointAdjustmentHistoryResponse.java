package com.kiwobollae.api.point.dto.response;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.point.entity.PointTransaction;
import com.kiwobollae.api.point.entity.enums.CurrencyType;
import java.time.LocalDateTime;

public record AdminPointAdjustmentHistoryResponse(
		Long transactionId,
		Long targetUserId,
		String targetEmail,
		String targetNickname,
		CurrencyType currencyType,
		Long amount,
		Long balanceAfter,
		Long adminUserId,
		LocalDateTime createdAt
) {
	public static AdminPointAdjustmentHistoryResponse from(PointTransaction transaction) {
		User targetUser = transaction.getWallet().getUser();
		return new AdminPointAdjustmentHistoryResponse(
				transaction.getId(),
				targetUser.getId(),
				targetUser.getEmail(),
				targetUser.getNickname(),
				transaction.getCurrencyType(),
				transaction.getAmount(),
				transaction.getBalanceAfter(),
				transaction.getRefId(),
				transaction.getCreatedAt()
		);
	}
}
