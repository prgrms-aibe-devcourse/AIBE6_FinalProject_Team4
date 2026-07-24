package com.kiwobollae.api.commerce.controller;

import com.kiwobollae.api.commerce.service.ExchangeService;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "교환", description = "카드를 실물 상품으로 교환하는 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/exchanges")
public class ExchangeController {

	private final ExchangeService exchangeService;
}
