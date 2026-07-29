package com.kiwobollae.api.commerce.dto.request;

import jakarta.validation.constraints.Size;

public record ExchangeCancelRequest(
		@Size(max = 200) String reason
) {
}
