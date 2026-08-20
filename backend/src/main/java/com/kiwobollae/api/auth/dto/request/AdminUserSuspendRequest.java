package com.kiwobollae.api.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminUserSuspendRequest(
		@NotBlank @Size(max = 200) String reason
) {
}
