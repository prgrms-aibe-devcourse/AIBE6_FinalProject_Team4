package com.kiwobollae.api.commerce.dto.response;

import com.kiwobollae.api.commerce.entity.Product;
import com.kiwobollae.api.commerce.entity.enums.ActiveStatus;
import com.kiwobollae.api.commerce.entity.enums.ProductCategory;
import java.time.LocalDateTime;

public record ProductResponse(
		Long id,
		String name,
		ProductCategory category,
		Long pointPrice,
		Integer stock,
		Long plantId,
		String plantName,
		String description,
		String imageUrl,
		ActiveStatus status,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {
	public static ProductResponse from(Product product) {
		return new ProductResponse(
				product.getId(),
				product.getName(),
				product.getCategory(),
				product.getPointPrice(),
				product.getStock(),
				product.getPlant() != null ? product.getPlant().getId() : null,
				product.getPlant() != null ? product.getPlant().getName() : null,
				product.getDescription(),
				product.getImageUrl(),
				product.getStatus(),
				product.getCreatedAt(),
				product.getUpdatedAt()
		);
	}
}
