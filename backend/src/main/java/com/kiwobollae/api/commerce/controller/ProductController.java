package com.kiwobollae.api.commerce.controller;

import com.kiwobollae.api.commerce.dto.response.ProductDetailResponse;
import com.kiwobollae.api.commerce.dto.response.ProductPageResponse;
import com.kiwobollae.api.commerce.service.ProductService;
import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "상품", description = "상점 상품 조회 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/product")
public class ProductController {

	private final ProductService productService;

	@Operation(summary = "상품 목록 조회", description = "활성 상태인 키트와 모종 상품을 페이지 단위로 조회합니다.")
	@GetMapping
	public ResponseEntity<ApiResponse<ProductPageResponse>> getProducts(
			@Parameter(description = "상품 카테고리: KIT 또는 SEEDLING")
			@RequestParam(required = false) String category,
			@Parameter(description = "정렬: LATEST, PRICE_ASC, PRICE_DESC")
			@RequestParam(defaultValue = "LATEST") String sort,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		return ResponseEntity.ok(ApiResponse.success(productService.getProducts(category, sort, page, size)));
	}

	@Operation(summary = "상품 상세 조회", description = "활성 상품의 상세 정보와 모종의 식물 가이드를 조회합니다.")
	@GetMapping("/{productId}")
	public ResponseEntity<ApiResponse<ProductDetailResponse>> getProduct(
			@PathVariable Long productId
	) {
		return ResponseEntity.ok(ApiResponse.success(productService.getProduct(productId)));
	}
}
