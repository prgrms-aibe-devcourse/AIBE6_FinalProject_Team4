package com.kiwobollae.api.payment.service;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.infra.service.IdempotencyExecution;
import com.kiwobollae.api.infra.service.IdempotencyService;
import com.kiwobollae.api.payment.dto.request.PaymentRefundRequest;
import com.kiwobollae.api.payment.dto.response.PaymentRefundResponse;
import com.kiwobollae.api.payment.entity.Payment;
import com.kiwobollae.api.payment.entity.PaymentRefund;
import com.kiwobollae.api.payment.entity.enums.PaymentRefundAttemptStatus;
import com.kiwobollae.api.payment.entity.enums.PaymentRefundStatus;
import com.kiwobollae.api.payment.entity.enums.PaymentProviderType;
import com.kiwobollae.api.payment.entity.enums.PaymentStatus;
import com.kiwobollae.api.payment.provider.PaymentProvider;
import com.kiwobollae.api.payment.provider.PaymentRefundCommand;
import com.kiwobollae.api.payment.provider.PaymentRefundResult;
import com.kiwobollae.api.payment.repository.PaymentRefundAttemptRepository;
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
	private final PaymentRefundAttemptRepository paymentRefundAttemptRepository;
	private final PaymentRefundAttemptService paymentRefundAttemptService;
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

		if (payment.getStatus() != PaymentStatus.COMPLETED) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATE);
		}
		if (payment.getProvider() != PaymentProviderType.TOSS
				|| paymentProvider.getType() != PaymentProviderType.TOSS) {
			throw new BusinessException(
					ErrorCode.PAYMENT_INVALID_STATE,
					"Toss 결제만 환불할 수 있습니다."
			);
		}
		if (payment.getProviderPaymentKey() == null || payment.getProviderPaymentKey().isBlank()) {
			throw new BusinessException(
					ErrorCode.COMMON_DATA_CONFLICT,
					"환불할 결제의 승인 정보가 올바르지 않습니다."
			);
		}

		// 결과가 확정되지 않은 이전 시도가 남아 있으면 자동 재시도를 막는다. 멱등키 행은 본
		// 트랜잭션에서 생성되므로 롤백과 함께 사라진다 — 즉 롤백 후 재시도에서 이중 환불을 막는
		// 실질적 방어선은 멱등키가 아니라 아래에서 커밋하는 시도 기록이다. PG 처리 결과를 알 수
		// 없는 상태이므로 자동 재환불 대신 사람이 확인하도록 되돌린다.
		if (paymentRefundAttemptRepository.existsByPaymentIdAndStatus(
				paymentId,
				PaymentRefundAttemptStatus.STARTED
		)) {
			throw new BusinessException(
					ErrorCode.PAYMENT_INVALID_STATE,
					"이전 환불 시도의 처리 결과가 확정되지 않았습니다. 확인 후 다시 시도해 주세요."
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

		// PG 호출 직전에 시도 기록을 별도 트랜잭션으로 커밋한다. 이후 어디서 실패하든
		// (PG 환불 성공 후 상태 전이·커밋 실패 포함) 이 행이 STARTED로 남아 대조 근거가 되고,
		// 위 가드가 재시도를 막는다. payments FK가 없어 결제 행을 잠근 상태에서도 INSERT된다.
		Long attemptId = paymentRefundAttemptService.start(
				paymentId,
				userId,
				payment.getCashAmount(),
				payment.getPointAmount(),
				reason
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
				PaymentStatus.COMPLETED,
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

		// 성공 확정은 본 트랜잭션 안에서 한다(REQUIRES_NEW 아님). 이후 커밋이 실패하면 이 갱신도
		// 함께 롤백돼 기록이 STARTED로 남으므로, 환불 결과와 시도 기록이 항상 같이 움직인다.
		int attemptSettled = paymentRefundAttemptRepository.settleIfCurrent(
				attemptId,
				PaymentRefundAttemptStatus.STARTED,
				PaymentRefundAttemptStatus.SETTLED,
				LocalDateTime.now(seoulClock)
		);
		if (attemptSettled == 0) {
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
