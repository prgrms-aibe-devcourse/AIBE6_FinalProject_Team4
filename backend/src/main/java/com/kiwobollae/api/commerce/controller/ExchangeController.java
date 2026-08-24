package com.kiwobollae.api.commerce.controller;

import com.kiwobollae.api.commerce.dto.request.ExchangeCancelRequest;
import com.kiwobollae.api.commerce.dto.request.ExchangeOrderRequest;
import com.kiwobollae.api.commerce.dto.response.ExchangeOrderResponse;
import com.kiwobollae.api.commerce.service.ExchangeService;
import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "교환", description = "쿠폰을 실물 상품으로 교환하는 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/exchanges")
public class ExchangeController {

	private final ExchangeService exchangeService;

	@Operation(summary = "교환 신청", description = "보유한 쿠폰을 실물 상품으로 교환 신청합니다.")
	@PostMapping
	public ResponseEntity<ApiResponse<ExchangeOrderResponse>> requestExchange(
			@AuthenticationPrincipal Long userId,
			@Valid @RequestBody ExchangeOrderRequest request) {
		return ResponseEntity.ok(ApiResponse.success(exchangeService.requestExchange(userId, request)));
	}

	@Operation(summary = "내 교환 신청 목록 조회", description = "내가 신청한 교환 내역을 페이징 조회합니다.")
	@GetMapping
	public ResponseEntity<ApiResponse<Page<ExchangeOrderResponse>>> getMyExchanges(
			@AuthenticationPrincipal Long userId,
			@ParameterObject @PageableDefault(size = 20, sort = "requestedAt", direction = Sort.Direction.DESC)
			Pageable pageable) {
		return ResponseEntity.ok(ApiResponse.success(exchangeService.getMyExchanges(userId, pageable)));
	}

	@Operation(summary = "내 교환 신청 상세 조회", description = "내가 신청한 교환 건 하나를 조회합니다.")
	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<ExchangeOrderResponse>> getMyExchange(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(exchangeService.getMyExchange(userId, id)));
	}

	@Operation(summary = "교환 신청 취소", description = "배송 준비 중인 내 교환 신청을 취소합니다. 쿠폰·재고가 환급됩니다.")
	@PatchMapping("/{id}/cancel")
	public ResponseEntity<Void> cancelExchange(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long id,
			@Valid @RequestBody ExchangeCancelRequest request) {
		exchangeService.cancelExchange(userId, id, request.reason());
		return ResponseEntity.noContent().build();
	}
}
