package com.kiwobollae.api.commerce.dto.response;

import com.kiwobollae.api.commerce.entity.Product;
import java.util.List;
import java.util.function.Function;
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
	public static ProductPageResponse from(Page<Product> products, Function<String, String> imageUrlResolver) {
		return new ProductPageResponse(
				products.getContent().stream()
						.map(product -> ProductListItemResponse.from(
								product,
								imageUrlResolver.apply(product.getImageUrl())
						))
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
