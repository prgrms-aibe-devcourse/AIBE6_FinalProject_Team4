package com.kiwobollae.api.point.dto.response;

import com.kiwobollae.api.point.entity.Wallet;
import java.time.LocalDateTime;

public record WalletResponse(
		Long id,
		Long userId,
		Long paidPoint,
		Long freePoint,
		Long version,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {
	public static WalletResponse from(Wallet wallet) {
		return new WalletResponse(
				wallet.getId(),
				wallet.getUser().getId(),
				wallet.getPaidPoint(),
				wallet.getFreePoint(),
				wallet.getVersion(),
				wallet.getCreatedAt(),
				wallet.getUpdatedAt()
		);
	}
}
