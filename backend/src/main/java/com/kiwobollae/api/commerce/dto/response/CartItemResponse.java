package com.kiwobollae.api.commerce.dto.response;

import com.kiwobollae.api.commerce.entity.CartItem;

public record CartItemResponse(
		Long id,
		Long userId,
		Long productId,
		String productName,
		Integer quantity
) {
	public static CartItemResponse from(CartItem cartItem) {
		return new CartItemResponse(
				cartItem.getId(),
				cartItem.getUser().getId(),
				cartItem.getProduct().getId(),
				cartItem.getProduct().getName(),
				cartItem.getQuantity()
		);
	}
}
