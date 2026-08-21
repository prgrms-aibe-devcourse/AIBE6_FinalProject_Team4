package com.kiwobollae.api.payment.controller;

import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.payment.dto.request.PaymentConfirmRequest;
import com.kiwobollae.api.payment.dto.request.PaymentFailureRequest;
import com.kiwobollae.api.payment.dto.request.PaymentRefundRequest;
import com.kiwobollae.api.payment.dto.request.PaymentRequest;
import com.kiwobollae.api.payment.dto.response.PaymentHistoryResponse;
import com.kiwobollae.api.payment.dto.response.PaymentRefundResponse;
import com.kiwobollae.api.payment.dto.response.PaymentResponse;
import com.kiwobollae.api.payment.service.PaymentRefundService;
import com.kiwobollae.api.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
	private final PaymentRefundService paymentRefundService;

	@Operation(summary = "포인트 충전 요청",
			description = "1원=1P로 1,000~300,000P 범위의 10P 단위 PENDING 결제를 생성합니다. [PAY-02]")
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
			description = "결제 대행사의 승인 결과와 금액을 확인하고 성공 시 유상 포인트를 적립합니다. [PAY-03/POINT-05]")
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

	@Operation(
			summary = "결제 인증 실패·취소 반영",
			description = "Toss 실패 콜백을 소유한 PENDING 결제에 반영합니다."
	)
	@PostMapping("/fail")
	public ResponseEntity<ApiResponse<PaymentResponse>> failPayment(
			@AuthenticationPrincipal Long userId,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@Valid @RequestBody PaymentFailureRequest request
	) {
		return ResponseEntity.ok(ApiResponse.success(
				paymentService.failPayment(userId, idempotencyKey, request)
		));
	}

	@Operation(
			summary = "충전 결제 전액 환불",
			description = "원결제 금액 전부를 환불하고 동일한 유상 포인트를 회수합니다. [PAY-04/POINT-06]"
	)
	@PostMapping("/{paymentId}/refund")
	public ResponseEntity<ApiResponse<PaymentRefundResponse>> refundPayment(
			@AuthenticationPrincipal Long userId,
			@PathVariable String paymentId,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@Valid @RequestBody PaymentRefundRequest request
	) {
		return ResponseEntity.ok(ApiResponse.success(
				paymentRefundService.refund(
						userId,
						idempotencyKey,
						parsePaymentId(paymentId),
						request
				)
		));
	}

	@Operation(summary = "결제·환불 내역 조회", description = "내 결제와 연결된 환불 내역을 조회합니다. [PAY-05]")
	@GetMapping
	public ResponseEntity<ApiResponse<List<PaymentHistoryResponse>>> getPaymentHistory(
			@AuthenticationPrincipal Long userId
	) {
		return ResponseEntity.ok(ApiResponse.success(paymentService.getPaymentHistory(userId)));
	}

	private Long parsePaymentId(String paymentId) {
		try {
			long parsedPaymentId = Long.parseLong(paymentId);
			if (parsedPaymentId < 1) {
				throw invalidPaymentId(paymentId);
			}
			return parsedPaymentId;
		} catch (NumberFormatException exception) {
			throw invalidPaymentId(paymentId);
		}
	}

	private BusinessException invalidPaymentId(String paymentId) {
		return new BusinessException(
				ErrorCode.COMMON_VALIDATION_FAILED,
				Map.of(
						"field", "paymentId",
						"rejectedValue", paymentId,
						"reason", "1 이상의 숫자여야 합니다."
				)
		);
	}
}
