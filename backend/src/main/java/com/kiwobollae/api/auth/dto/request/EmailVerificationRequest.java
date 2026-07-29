package com.kiwobollae.api.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailVerificationRequest(
		@NotBlank @Email String email
) {
}
