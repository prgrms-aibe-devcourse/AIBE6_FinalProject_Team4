package com.kiwobollae.api.commerce.dto.response;

import java.util.List;

public record OrderDetailResponse(
		OrderResponse order,
		List<OrderItemResponse> items
) {
}
