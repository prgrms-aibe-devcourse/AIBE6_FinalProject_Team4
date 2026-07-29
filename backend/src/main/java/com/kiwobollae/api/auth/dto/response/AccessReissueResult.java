package com.kiwobollae.api.auth.dto.response;

/**
 * Result of reissuing just the access token from an existing, still-valid
 * refresh token. Unlike {@link TokenIssueResult} (used at login), no new
 * refresh token is minted or persisted here — the refresh_token row and its
 * cookie are left untouched and reused until their own expiry or logout.
 */
public record AccessReissueResult(
		String accessToken,
		String tokenType,
		UserResponse user
) {
}
