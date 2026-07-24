package com.kiwobollae.api.commerce.controller;

import com.kiwobollae.api.commerce.service.CardService;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "카드", description = "카드 조회/보유 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/cards")
public class CardController {

	private final CardService cardService;
}
