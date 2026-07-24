package com.kiwobollae.api.commerce.controller;

import com.kiwobollae.api.commerce.service.CartService;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "장바구니", description = "장바구니 담기/조회/수정 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/cart")
public class CartController {

	private final CartService cartService;
}
