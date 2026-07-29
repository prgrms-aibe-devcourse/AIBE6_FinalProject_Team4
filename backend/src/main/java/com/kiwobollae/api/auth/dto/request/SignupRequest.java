package com.kiwobollae.api.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
		@NotBlank @Email String email,
		@NotBlank @Size(min = 8, max = 100) String password,
		@NotBlank @Size(max = 12) String nickname,
		@NotBlank @Size(max = 10) String name,
		@Pattern(regexp = "^(010|011)\\d{7,8}$", message = "전화번호는 010 또는 011로 시작하는 숫자 10~11자리여야 합니다.")
		String phoneNumber
) {
}
