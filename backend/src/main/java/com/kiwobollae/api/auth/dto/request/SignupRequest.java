package com.kiwobollae.api.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
		@NotBlank @Email String email,
		@NotBlank @Size(min = 8, max = 100) String password,
		@NotBlank @Size(max = 50) String nickname,
		@NotBlank @Size(max = 50) String name,
		@Size(max = 20) String phoneNumber
) {
}
