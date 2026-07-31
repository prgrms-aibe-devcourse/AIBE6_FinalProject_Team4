package com.kiwobollae.api.commerce.dto.response;

import com.kiwobollae.api.commerce.entity.CartItem;
import com.kiwobollae.api.commerce.entity.Product;
import com.kiwobollae.api.commerce.entity.enums.ProductStatus;

public record CartItemResponse(
		Long id,
		Long userId,
		Long productId,
		String productName,
		Long unitPrice,
		Integer quantity,
		Integer availableStock,
		boolean soldOut,
		boolean stockShortage
) {
	public static CartItemResponse from(CartItem cartItem) {
		Product product = cartItem.getProduct();
		// 판매 중단(HIDDEN)이거나 재고가 아예 없으면 품절, 남은 재고는 있지만 담은 수량이 이를
		// 초과하면 재고 부족으로 구분한다 — 둘 다 프론트가 결제 가능 여부를 판단하는 신호다.
		boolean soldOut = product.getStatus() != ProductStatus.ACTIVE || product.getStock() <= 0;
		boolean stockShortage = !soldOut && cartItem.getQuantity() > product.getStock();
		return new CartItemResponse(
				cartItem.getId(),
				cartItem.getUser().getId(),
				product.getId(),
				product.getName(),
				product.getPointPrice(),
				cartItem.getQuantity(),
				product.getStock(),
				soldOut,
				stockShortage
		);
	}
}
