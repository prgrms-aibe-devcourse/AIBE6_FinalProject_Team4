package com.kiwobollae.api.commerce.controller;

import com.kiwobollae.api.commerce.dto.response.CardResponse;
import com.kiwobollae.api.commerce.service.CardService;
import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "카드", description = "카드 조회/보유 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/cards")
public class CardController {

	private final CardService cardService;

	@Operation(
			summary = "카드 목록 조회",
			description = "판매 중인 카드를 조회합니다. 로그인한 경우 내 보유 수량을 함께 반환합니다."
	)
	@GetMapping
	public ResponseEntity<ApiResponse<List<CardResponse>>> getCards(
			@AuthenticationPrincipal Long userId
	) {
		return ResponseEntity.ok(ApiResponse.success(cardService.getCards(userId)));
	}

	@Operation(
			summary = "카드 상세 조회",
			description = "판매 중인 카드 상세를 조회합니다. 로그인한 경우 내 보유 수량을 함께 반환합니다."
	)
	@GetMapping("/{cardId}")
	public ResponseEntity<ApiResponse<CardResponse>> getCard(
			@PathVariable String cardId,
			@AuthenticationPrincipal Long userId
	) {
		return ResponseEntity.ok(ApiResponse.success(cardService.getCard(parseCardId(cardId), userId)));
	}

	private Long parseCardId(String cardId) {
		try {
			long parsedCardId = Long.parseLong(cardId);
			if (parsedCardId < 1) {
				throw invalidCardId(cardId);
			}
			return parsedCardId;
		} catch (NumberFormatException exception) {
			throw invalidCardId(cardId);
		}
	}

	private BusinessException invalidCardId(String cardId) {
		return new BusinessException(
				ErrorCode.COMMON_VALIDATION_FAILED,
				Map.of(
						"field", "cardId",
						"rejectedValue", cardId,
						"reason", "1 이상의 숫자여야 합니다."
				)
		);
	}
}
