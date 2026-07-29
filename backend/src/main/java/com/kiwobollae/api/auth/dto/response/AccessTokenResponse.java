package com.kiwobollae.api.auth.dto.response;

public record AccessTokenResponse(
		String accessToken,
		String tokenType,
		UserResponse user
) {
}
