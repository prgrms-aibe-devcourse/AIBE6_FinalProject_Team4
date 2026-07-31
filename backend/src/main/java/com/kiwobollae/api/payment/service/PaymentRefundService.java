package com.kiwobollae.api.payment.service;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.infra.service.IdempotencyExecution;
import com.kiwobollae.api.infra.service.IdempotencyService;
import com.kiwobollae.api.payment.dto.request.PaymentRefundRequest;
import com.kiwobollae.api.payment.dto.response.PaymentRefundResponse;
import com.kiwobollae.api.payment.entity.Payment;
import com.kiwobollae.api.payment.entity.PaymentRefund;
import com.kiwobollae.api.payment.entity.enums.PaymentRefundStatus;
import com.kiwobollae.api.payment.entity.enums.PaymentStatus;
import com.kiwobollae.api.payment.provider.PaymentProvider;
import com.kiwobollae.api.payment.provider.PaymentRefundCommand;
import com.kiwobollae.api.payment.provider.PaymentRefundResult;
import com.kiwobollae.api.payment.repository.PaymentRefundRepository;
import com.kiwobollae.api.payment.repository.PaymentRepository;
import com.kiwobollae.api.point.service.WalletService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentRefundService {

	private static final String REFUND_API_TYPE = "PAYMENT_REFUND";

	private final PaymentRepository paymentRepository;
	private final PaymentRefundRepository paymentRefundRepository;
	private final WalletService walletService;
	private final PaymentProvider paymentProvider;
	private final IdempotencyService idempotencyService;
	private final ObjectMapper objectMapper;
	private final Clock seoulClock;

	@Transactional
	public PaymentRefundResponse refund(
			Long userId,
			String idempotencyKey,
			Long paymentId,
			PaymentRefundRequest request
	) {
		validateRequest(userId, paymentId, idempotencyKey);
		String reason = request.reason().trim();
		Payment payment = paymentRepository.findDetailsByIdAndUserIdForUpdate(paymentId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

		IdempotencyExecution idempotency = idempotencyService.start(
				userId,
				REFUND_API_TYPE,
				idempotencyKey,
				sha256(normalizedRequest(paymentId, reason))
		);
		if (idempotency.replay()) {
			return readSnapshot(idempotency.key().getResponseSnapshot());
		}

		if (payment.getStatus() != PaymentStatus.PAID) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATE);
		}
		if (payment.getProviderPaymentKey() == null || payment.getProviderPaymentKey().isBlank()) {
			throw new BusinessException(
					ErrorCode.COMMON_DATA_CONFLICT,
					"환불할 결제의 승인 정보가 올바르지 않습니다."
			);
		}

		PaymentRefund refund = paymentRefundRepository.saveAndFlush(PaymentRefund.builder()
				.payment(payment)
				.cashAmount(payment.getCashAmount())
				.pointAmount(payment.getPointAmount())
				.status(PaymentRefundStatus.REQUESTED)
				.reason(reason)
				.build());

		walletService.deductPaidPointForPaymentRefund(
				userId,
				payment.getPointAmount(),
				refund.getId()
		);

		PaymentRefundResult providerResult = paymentProvider.refund(new PaymentRefundCommand(
				payment.getProviderOrderId(),
				payment.getProviderPaymentKey(),
				payment.getCashAmount(),
				reason
		));
		validateProviderResult(providerResult);

		int paymentUpdated = paymentRepository.updateStatusOnlyIfCurrent(
				payment.getId(),
				PaymentStatus.PAID,
				PaymentStatus.REFUNDED
		);
		if (paymentUpdated == 0) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATE);
		}

		int refundUpdated = paymentRefundRepository.completeIfCurrent(
				refund.getId(),
				PaymentRefundStatus.REQUESTED,
				PaymentRefundStatus.COMPLETED,
				providerResult.refundKey(),
				LocalDateTime.now(seoulClock)
		);
		if (refundUpdated == 0) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATE);
		}

		PaymentRefundResponse response = PaymentRefundResponse.from(
				paymentRefundRepository.findDetailsById(refund.getId())
						.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_DATA_CONFLICT))
		);
		idempotencyService.succeed(
				idempotency.key(),
				200,
				writeSnapshot(response),
				"PAYMENT_REFUND",
				refund.getId()
		);
		return response;
	}

	private void validateRequest(Long userId, Long paymentId, String idempotencyKey) {
		if (userId == null || userId < 1 || paymentId == null || paymentId < 1
				|| idempotencyKey == null || idempotencyKey.isBlank()
				|| idempotencyKey.length() > 64) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}
	}

	private void validateProviderResult(PaymentRefundResult result) {
		if (result == null) {
			throw new BusinessException(ErrorCode.PAYMENT_PROVIDER_INVALID_RESPONSE);
		}
		if (!result.successful()) {
			throw new BusinessException(
					ErrorCode.PAYMENT_DECLINED,
					result.message() == null || result.message().isBlank()
							? "결제 대행사가 환불을 거절했습니다."
							: result.message()
			);
		}
		if (result.refundKey() == null || result.refundKey().isBlank()
				|| result.refundKey().length() > 200) {
			throw new BusinessException(ErrorCode.PAYMENT_PROVIDER_INVALID_RESPONSE);
		}
	}

	private String normalizedRequest(Long paymentId, String reason) {
		return "paymentId=" + paymentId + "&reason=" + reason;
	}

	private String sha256(String value) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256")
							.digest(value.getBytes(StandardCharsets.UTF_8))
			);
		} catch (NoSuchAlgorithmException exception) {
			throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
		}
	}

	private String writeSnapshot(PaymentRefundResponse response) {
		try {
			return objectMapper.writeValueAsString(response);
		} catch (JacksonException exception) {
			throw new IllegalStateException("환불 멱등성 응답 저장에 실패했습니다.", exception);
		}
	}

	private PaymentRefundResponse readSnapshot(String snapshot) {
		try {
			return objectMapper.readValue(snapshot, PaymentRefundResponse.class);
		} catch (JacksonException exception) {
			throw new IllegalStateException("환불 멱등성 응답 복원에 실패했습니다.", exception);
		}
	}
}
