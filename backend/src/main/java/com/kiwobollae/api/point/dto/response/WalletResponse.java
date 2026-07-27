package com.kiwobollae.api.point.dto.response;

import com.kiwobollae.api.point.entity.Wallet;
import java.time.LocalDateTime;

/** 잔액 조회 응답. 정책 #2에 따라 유상/무상을 분리 노출하지 않고 합산 잔액(balance)만 반환. */
public record WalletResponse(
		Long userId,
		Long balance,
		LocalDateTime updatedAt
) {
	public static WalletResponse from(Wallet wallet) {
		return new WalletResponse(
				wallet.getUser().getId(),
				wallet.totalBalance(),
				wallet.getUpdatedAt()
		);
	}
}
