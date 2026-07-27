package com.kiwobollae.api.payment.controller;

import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import com.kiwobollae.api.payment.dto.request.PaymentConfirmRequest;
import com.kiwobollae.api.payment.dto.request.PaymentRequest;
import com.kiwobollae.api.payment.dto.response.ChargeProductResponse;
import com.kiwobollae.api.payment.dto.response.PaymentHistoryResponse;
import com.kiwobollae.api.payment.dto.response.PaymentResponse;
import com.kiwobollae.api.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "결제", description = "포인트 충전/결제 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/payments")
public class PaymentController {

	private final PaymentService paymentService;

	@Operation(summary = "충전 상품 목록 조회", description = "판매 중인 정액 충전 상품을 조회합니다. [PAY-01]")
	@GetMapping("/products")
	public ResponseEntity<ApiResponse<List<ChargeProductResponse>>> getChargeProducts() {
		return ResponseEntity.ok(ApiResponse.success(paymentService.getChargeProducts()));
	}

	@Operation(summary = "포인트 충전 요청",
			description = "상품 가격과 지급 포인트를 서버에서 확정하고 PENDING 결제를 생성합니다. [PAY-02]")
	@PostMapping("/charge")
	public ResponseEntity<ApiResponse<PaymentResponse>> requestCharge(
			@AuthenticationPrincipal Long userId,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@Valid @RequestBody PaymentRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(paymentService.requestCharge(userId, idempotencyKey, request)));
	}

	@Operation(summary = "결제 승인 확정",
			description = "Mock 승인 결과와 금액을 확인하고 성공 시 유상 포인트를 적립합니다. [PAY-03/POINT-05]")
	@PostMapping("/confirm")
	public ResponseEntity<ApiResponse<PaymentResponse>> confirmPayment(
			@AuthenticationPrincipal Long userId,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@Valid @RequestBody PaymentConfirmRequest request
	) {
		return ResponseEntity.ok(ApiResponse.success(
				paymentService.confirmPayment(userId, idempotencyKey, request)
		));
	}

	@Operation(summary = "결제·환불 내역 조회", description = "내 결제와 연결된 환불 내역을 조회합니다. [PAY-05]")
	@GetMapping
	public ResponseEntity<ApiResponse<List<PaymentHistoryResponse>>> getPaymentHistory(
			@AuthenticationPrincipal Long userId
	) {
		return ResponseEntity.ok(ApiResponse.success(paymentService.getPaymentHistory(userId)));
	}
}
