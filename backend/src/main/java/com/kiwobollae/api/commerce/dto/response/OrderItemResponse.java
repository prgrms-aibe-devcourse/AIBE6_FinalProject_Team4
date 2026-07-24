package com.kiwobollae.api.commerce.dto.response;

import com.kiwobollae.api.commerce.entity.OrderItem;

public record OrderItemResponse(
		Long id,
		Long orderId,
		Long productId,
		String productName,
		Integer quantity,
		Long unitPoint
) {
	public static OrderItemResponse from(OrderItem orderItem) {
		return new OrderItemResponse(
				orderItem.getId(),
				orderItem.getOrder().getId(),
				orderItem.getProduct().getId(),
				orderItem.getProductName(),
				orderItem.getQuantity(),
				orderItem.getUnitPoint()
		);
	}
}
