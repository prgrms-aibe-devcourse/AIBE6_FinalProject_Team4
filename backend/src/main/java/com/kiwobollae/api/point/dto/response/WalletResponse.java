package com.kiwobollae.api.point.dto.response;

import com.kiwobollae.api.point.entity.Wallet;
import java.time.LocalDateTime;

/** 잔액 조회 응답. 합산 잔액과 유상/무상 잔액을 함께 반환한다. */
public record WalletResponse(
		Long userId,
		Long balance,
		Long paidPoint,
		Long freePoint,
		LocalDateTime updatedAt
) {
	public static WalletResponse from(Wallet wallet) {
		return new WalletResponse(
				wallet.getUser().getId(),
				wallet.totalBalance(),
				wallet.getPaidPoint(),
				wallet.getFreePoint(),
				wallet.getUpdatedAt()
		);
	}
}
