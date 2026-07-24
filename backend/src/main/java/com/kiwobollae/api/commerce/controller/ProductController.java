package com.kiwobollae.api.commerce.controller;

import com.kiwobollae.api.commerce.service.ProductService;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "상품", description = "상점 상품 조회 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/products")
public class ProductController {

	private final ProductService productService;
}
