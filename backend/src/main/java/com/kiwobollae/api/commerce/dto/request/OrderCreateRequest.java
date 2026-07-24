package com.kiwobollae.api.commerce.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record OrderCreateRequest(
		@NotEmpty List<Long> cartItemIds,
		@NotBlank @Size(max = 50) String receiverName,
		@NotBlank @Size(max = 20) String receiverPhone,
		@NotBlank @Size(max = 200) String address,
		@Size(max = 100) String addressDetail
) {
}
