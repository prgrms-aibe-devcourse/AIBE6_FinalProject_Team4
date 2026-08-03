package com.kiwobollae.api.commerce.dto.response;

public record GachaPackProductQuote(
		Long productId,
		String name,
		Long unitPoint,
		Integer maxQuantity
) {
	public static final int MAX_PURCHASE_QUANTITY = 1;
}
