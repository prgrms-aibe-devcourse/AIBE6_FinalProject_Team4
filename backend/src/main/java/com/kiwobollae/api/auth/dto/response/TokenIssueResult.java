package com.kiwobollae.api.auth.dto.response;

/**
 * Internal service→controller carrier for the raw refresh token, which must
 * never be serialized into a JSON body — the controller writes it into an
 * httpOnly cookie instead.
 */
public record TokenIssueResult(
		String accessToken,
		String tokenType,
		UserResponse user,
		String rawRefreshToken
) {
}
