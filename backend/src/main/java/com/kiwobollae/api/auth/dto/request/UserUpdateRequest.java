package com.kiwobollae.api.auth.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update — every field is optional; a null field is left unchanged.
 * A present-but-blank field is rejected via the non-blank pattern rather than
 * silently clearing it (email/role/status/etc. are not editable here at all).
 */
public record UserUpdateRequest(
		@Pattern(regexp = ".*\\S.*", message = "닉네임은 공백만으로 이루어질 수 없습니다.")
		@Size(max = 12) String nickname,
		@Pattern(regexp = ".*\\S.*", message = "이름은 공백만으로 이루어질 수 없습니다.")
		@Size(max = 10) String name,
		@Pattern(regexp = "^(010|011)\\d{7,8}$", message = "전화번호는 010 또는 011로 시작하는 숫자 10~11자리여야 합니다.")
		String phoneNumber
) {
}
