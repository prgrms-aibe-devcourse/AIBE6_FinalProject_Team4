package com.kiwobollae.api.commerce.dto.request;

import com.kiwobollae.api.commerce.entity.enums.ActiveStatus;
import com.kiwobollae.api.commerce.entity.enums.ProductCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductRequest(
		@NotBlank @Size(max = 100) String name,
		@NotNull ProductCategory category,
		@NotNull Long pointPrice,
		@NotNull Integer stock,
		Long plantId,
		@Size(max = 2000) String description,
		@Size(max = 500) String imageUrl,
		@NotNull ActiveStatus status
) {
}
