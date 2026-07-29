package com.kiwobollae.api.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PasswordVerifyRequest(
		@NotBlank String password
) {
}
