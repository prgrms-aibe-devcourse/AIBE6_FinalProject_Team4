package com.kiwobollae.api.payment.controller;

import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import com.kiwobollae.api.payment.dto.request.ChargeProductCreateRequest;
import com.kiwobollae.api.payment.dto.request.ChargeProductUpdateRequest;
import com.kiwobollae.api.payment.dto.response.ChargeProductResponse;
import com.kiwobollae.api.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 결제", description = "관리자 충전 상품 관리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/admin/payments/products")
public class AdminPaymentController {

	private final PaymentService paymentService;

	@Operation(
			summary = "충전 상품 목록 조회",
			description = "활성·비활성 충전 상품 전체를 가격 및 ID 오름차순으로 조회합니다."
	)
	@GetMapping
	public ResponseEntity<ApiResponse<List<ChargeProductResponse>>> getChargeProducts() {
		return ResponseEntity.ok(ApiResponse.success(paymentService.getAdminChargeProducts()));
	}

	@Operation(
			summary = "충전 상품 등록",
			description = "결제 금액의 100~150% 지급 포인트로 정액 충전 상품을 등록합니다. [PAY-06]"
	)
	@PostMapping
	public ResponseEntity<ApiResponse<ChargeProductResponse>> createChargeProduct(
			@AuthenticationPrincipal Long adminUserId,
			@RequestHeader("Idempotency-Key") String idempotencyKey,
			@Valid @RequestBody ChargeProductCreateRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(paymentService.createChargeProduct(
						adminUserId,
						idempotencyKey,
						request
				)));
	}

	@Operation(
			summary = "충전 상품 수정",
			description = "결제 금액의 100~150% 지급 포인트와 조회한 version을 함께 전송해 상품을 수정합니다. [PAY-07]"
	)
	@PatchMapping("/{productId}")
	public ResponseEntity<ApiResponse<ChargeProductResponse>> updateChargeProduct(
			@PathVariable Long productId,
			@Valid @RequestBody ChargeProductUpdateRequest request
	) {
		return ResponseEntity.ok(ApiResponse.success(
				paymentService.updateChargeProduct(productId, request)
		));
	}

	@Operation(summary = "충전 상품 비활성화", description = "구매 이력을 보존하고 상품을 판매 중지합니다. [PAY-08]")
	@DeleteMapping("/{productId}")
	public ResponseEntity<Void> deactivateChargeProduct(@PathVariable Long productId) {
		paymentService.deactivateChargeProduct(productId);
		return ResponseEntity.noContent().build();
	}
}
