package com.kiwobollae.api.auth.dto.response;

import com.kiwobollae.api.auth.entity.RefreshToken;
import java.time.LocalDateTime;

public record RefreshTokenResponse(
		Long id,
		Long userId,
		String tokenHash,
		LocalDateTime expiresAt,
		LocalDateTime createdAt,
		LocalDateTime revokedAt
) {
	public static RefreshTokenResponse from(RefreshToken refreshToken) {
		return new RefreshTokenResponse(
				refreshToken.getId(),
				refreshToken.getUser().getId(),
				refreshToken.getTokenHash(),
				refreshToken.getExpiresAt(),
				refreshToken.getCreatedAt(),
				refreshToken.getRevokedAt()
		);
	}
}
