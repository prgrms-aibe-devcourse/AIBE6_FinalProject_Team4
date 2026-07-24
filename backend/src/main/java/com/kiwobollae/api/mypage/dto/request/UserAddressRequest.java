package com.kiwobollae.api.mypage.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserAddressRequest(
		@NotBlank @Size(max = 50) String receiverName,
		@NotBlank @Size(max = 20) String receiverPhone,
		@NotBlank @Size(max = 10) String zipCode,
		@NotBlank @Size(max = 200) String address,
		@Size(max = 100) String addressDetail,
		@NotNull Boolean isDefault
) {
}
