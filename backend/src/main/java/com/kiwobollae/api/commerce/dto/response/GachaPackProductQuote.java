package com.kiwobollae.api.commerce.dto.response;

public record GachaPackProductQuote(
		Long productId,
		String name,
		Long unitPoint,
		Integer maxQuantity
) {
}
