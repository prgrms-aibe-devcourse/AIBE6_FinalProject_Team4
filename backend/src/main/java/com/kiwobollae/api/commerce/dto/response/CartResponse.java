package com.kiwobollae.api.commerce.dto.response;

import java.util.List;

public record CartResponse(
		List<CartItemResponse> items,
		Long expectedTotal,
		Long walletBalance
) {
}
