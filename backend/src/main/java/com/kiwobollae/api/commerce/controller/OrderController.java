package com.kiwobollae.api.commerce.controller;

import com.kiwobollae.api.commerce.service.OrderService;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "주문", description = "상품 주문 및 주문 내역 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/orders")
public class OrderController {

	private final OrderService orderService;
}
