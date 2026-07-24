package com.kiwobollae.api.commerce.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kiwobollae.api.commerce.dto.response.ProductDetailResponse;
import com.kiwobollae.api.commerce.dto.response.ProductPageResponse;
import com.kiwobollae.api.commerce.entity.enums.ProductCategory;
import com.kiwobollae.api.commerce.service.ProductService;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.global.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

	@Mock
	private ProductService productService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		ProductController productController = new ProductController(productService);
		mockMvc = MockMvcBuilders.standaloneSetup(productController)
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	void getProductsUsesApiResponseWrapper() throws Exception {
		ProductPageResponse response = new ProductPageResponse(
				List.of(),
				0,
				20,
				0,
				0,
				true,
				true
		);
		given(productService.getProducts("KIT", "LATEST", 0, 20)).willReturn(response);

		mockMvc.perform(get("/api/v1/product")
						.param("category", "KIT")
						.param("sort", "LATEST"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.content").isArray())
				.andExpect(jsonPath("$.data.page").value(0))
				.andExpect(jsonPath("$.data.size").value(20));
	}

	@Test
	void getProductReturnsDetail() throws Exception {
		ProductDetailResponse response = new ProductDetailResponse(
				1L,
				"새싹 재배 키트",
				ProductCategory.KIT,
				800L,
				0,
				true,
				"처음 키우기 좋은 키트",
				null,
				null,
				null,
				null
		);
		given(productService.getProduct(1L)).willReturn(response);

		mockMvc.perform(get("/api/v1/product/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(1))
				.andExpect(jsonPath("$.data.name").value("새싹 재배 키트"))
				.andExpect(jsonPath("$.data.soldOut").value(true));
	}

	@Test
	void getProductReturnsProductNotFoundError() throws Exception {
		given(productService.getProduct(404L))
				.willThrow(new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

		mockMvc.perform(get("/api/v1/product/404"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("상품을 찾을 수 없습니다."));
	}
}
