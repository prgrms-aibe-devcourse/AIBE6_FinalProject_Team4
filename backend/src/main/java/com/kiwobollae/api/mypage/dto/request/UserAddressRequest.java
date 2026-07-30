package com.kiwobollae.api.mypage.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserAddressRequest(
		@NotBlank @Size(max = 50) String receiverName,
		@NotBlank @Pattern(regexp = "^(010|011)\\d{7,8}$", message = "연락처는 010 또는 011로 시작하는 숫자 9~11자리여야 합니다.") String receiverPhone,
		@NotBlank @Size(max = 10) String zipCode,
		@NotBlank @Size(max = 200) String address,
		@Size(max = 100) String addressDetail,
		@NotNull Boolean isDefault
) {
}
