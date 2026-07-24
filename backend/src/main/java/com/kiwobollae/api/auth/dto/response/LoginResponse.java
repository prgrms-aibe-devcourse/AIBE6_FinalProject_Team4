package com.kiwobollae.api.auth.dto.response;

public record LoginResponse(
		String accessToken,
		String tokenType,
		UserResponse user
) {
}
