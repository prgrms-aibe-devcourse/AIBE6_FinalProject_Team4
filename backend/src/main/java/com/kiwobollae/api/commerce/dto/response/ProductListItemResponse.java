package com.kiwobollae.api.commerce.dto.response;

import com.kiwobollae.api.commerce.entity.Product;
import com.kiwobollae.api.commerce.entity.enums.ProductCategory;

public record ProductListItemResponse(
		Long id,
		String name,
		ProductCategory category,
		Long pointPrice,
		Integer stock,
		boolean soldOut,
		String imageUrl
) {
	public static ProductListItemResponse from(Product product) {
		return new ProductListItemResponse(
				product.getId(),
				product.getName(),
				product.getCategory(),
				product.getPointPrice(),
				product.getStock(),
				product.getCategory() != ProductCategory.GACHA_PACK && product.getStock() <= 0,
				product.getImageUrl()
		);
	}
}
