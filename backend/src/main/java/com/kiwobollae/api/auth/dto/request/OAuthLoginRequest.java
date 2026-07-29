package com.kiwobollae.api.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record OAuthLoginRequest(
		@NotBlank String code,
		// Naver에서만 사용하는 CSRF 방지용 state — 프론트가 인가 요청 때 생성해 그대로 전달해야 한다.
		String state
) {
}
