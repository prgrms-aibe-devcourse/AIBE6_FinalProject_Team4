package com.kiwobollae.api.commerce.dto.response;

import com.kiwobollae.api.commerce.entity.Product;
import com.kiwobollae.api.commerce.entity.enums.ProductCategory;
import java.time.LocalDateTime;

public record ProductDetailResponse(
		Long id,
		String name,
		ProductCategory category,
		Long pointPrice,
		Integer stock,
		boolean soldOut,
		String description,
		String imageUrl,
		PlantGuideResponse plantGuide,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {
	public static ProductDetailResponse from(Product product, String imageUrl) {
		PlantGuideResponse plantGuide = product.getCategory() == ProductCategory.SEEDLING
				? PlantGuideResponse.from(product.getSpeciesName())
				: null;

		return new ProductDetailResponse(
				product.getId(),
				product.getName(),
				product.getCategory(),
				product.getPointPrice(),
				product.getStock(),
				product.getCategory() != ProductCategory.GACHA_PACK && product.getStock() <= 0,
				product.getDescription(),
				imageUrl,
				plantGuide,
				product.getCreatedAt(),
				product.getUpdatedAt()
		);
	}

	// category/careGuide 본문은 더 이상 여기서 내려주지 않는다. 프론트가 speciesName으로
	// /api/v1/ai/plants/care-guide를 별도 호출해 AI 재배 가이드를 받아온다.
	public record PlantGuideResponse(String speciesName) {
		public static PlantGuideResponse from(String speciesName) {
			if (speciesName == null) {
				return null;
			}
			return new PlantGuideResponse(speciesName);
		}
	}
}
