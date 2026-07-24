package com.kiwobollae.api.commerce.dto.response;

import com.kiwobollae.api.commerce.entity.Product;
import java.util.List;
import org.springframework.data.domain.Page;

public record ProductPageResponse(
		List<ProductListItemResponse> content,
		int page,
		int size,
		long totalElements,
		int totalPages,
		boolean first,
		boolean last
) {
	public static ProductPageResponse from(Page<Product> products) {
		return new ProductPageResponse(
				products.getContent().stream()
						.map(ProductListItemResponse::from)
						.toList(),
				products.getNumber(),
				products.getSize(),
				products.getTotalElements(),
				products.getTotalPages(),
				products.isFirst(),
				products.isLast()
		);
	}
}
