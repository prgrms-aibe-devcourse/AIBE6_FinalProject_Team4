package com.kiwobollae.api.commerce.service;

import com.kiwobollae.api.commerce.dto.response.ProductDetailResponse;
import com.kiwobollae.api.commerce.dto.response.ProductPageResponse;
import com.kiwobollae.api.commerce.entity.enums.ProductCategory;
import com.kiwobollae.api.commerce.entity.enums.ProductSort;
import com.kiwobollae.api.commerce.entity.enums.ProductStatus;
import com.kiwobollae.api.commerce.repository.ProductRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

	private static final int MAX_PAGE_SIZE = 100;
	private static final List<ProductCategory> SHOP_CATEGORIES = List.of(
			ProductCategory.KIT,
			ProductCategory.SEEDLING
	);

	private final ProductRepository productRepository;

	public ProductPageResponse getProducts(String categoryValue, String sortValue, int page, int size) {
		validatePage(page, size);

		List<ProductCategory> categories = categoryValue == null || categoryValue.isBlank()
				? SHOP_CATEGORIES
				: List.of(parseShopCategory(categoryValue));
		ProductSort sort = parseSort(sortValue);
		Pageable pageable = PageRequest.of(page, size, sort.toSort());

		return ProductPageResponse.from(
				productRepository.findAllByStatusAndCategoryIn(ProductStatus.ACTIVE, categories, pageable)
		);
	}

	public ProductDetailResponse getProduct(Long productId) {
		return productRepository.findByIdAndStatus(productId, ProductStatus.ACTIVE)
				.map(ProductDetailResponse::from)
				.orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
	}

	private ProductCategory parseShopCategory(String value) {
		try {
			ProductCategory category = ProductCategory.valueOf(value.trim().toUpperCase(Locale.ROOT));
			if (!SHOP_CATEGORIES.contains(category)) {
				throw invalidParameter("category", value, "KIT 또는 SEEDLING만 사용할 수 있습니다.");
			}
			return category;
		} catch (IllegalArgumentException exception) {
			throw invalidParameter("category", value, "KIT 또는 SEEDLING만 사용할 수 있습니다.");
		}
	}

	private ProductSort parseSort(String value) {
		String normalized = value == null || value.isBlank() ? ProductSort.LATEST.name() : value.trim();
		try {
			return ProductSort.valueOf(normalized.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			throw invalidParameter(
					"sort",
					value,
					"LATEST, PRICE_ASC, PRICE_DESC 중 하나만 사용할 수 있습니다."
			);
		}
	}

	private void validatePage(int page, int size) {
		if (page < 0) {
			throw invalidParameter("page", page, "0 이상이어야 합니다.");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw invalidParameter("size", size, "1 이상 100 이하여야 합니다.");
		}
	}

	private BusinessException invalidParameter(String field, Object rejectedValue, String reason) {
		return new BusinessException(
				ErrorCode.COMMON_VALIDATION_FAILED,
				Map.of("field", field, "rejectedValue", rejectedValue, "reason", reason)
		);
	}
}
