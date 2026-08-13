package com.kiwobollae.api.commerce.dto.response;

import com.kiwobollae.api.commerce.entity.Product;
import com.kiwobollae.api.commerce.entity.enums.ProductCategory;
import com.kiwobollae.api.species.entity.PlantSpecies;
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
				? PlantGuideResponse.from(product.getPlant())
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

	public record PlantGuideResponse(
			Long plantSpeciesId,
			String name,
			String category,
			String careGuide
	) {
		public static PlantGuideResponse from(PlantSpecies plantSpecies) {
			if (plantSpecies == null) {
				return null;
			}
			return new PlantGuideResponse(
					plantSpecies.getId(),
					plantSpecies.getName(),
					plantSpecies.getCategory(),
					plantSpecies.getCareGuide()
			);
		}
	}
}
