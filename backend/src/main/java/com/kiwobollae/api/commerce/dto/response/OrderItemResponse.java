package com.kiwobollae.api.commerce.dto.response;

import com.kiwobollae.api.commerce.entity.OrderItem;

public record OrderItemResponse(
		Long id,
		Long orderId,
		Long productId,
		String productName,
		String imageUrl,
		Integer quantity,
		Long unitPoint
) {
	public static OrderItemResponse from(OrderItem orderItem, String imageUrl) {
		return new OrderItemResponse(
				orderItem.getId(),
				orderItem.getOrder().getId(),
				orderItem.getProduct().getId(),
				orderItem.getProductName(),
				imageUrl,
				orderItem.getQuantity(),
				orderItem.getUnitPoint()
		);
	}
}
