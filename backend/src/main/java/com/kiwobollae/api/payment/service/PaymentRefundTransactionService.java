package com.kiwobollae.api.payment.service;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.infra.service.IdempotencyExecution;
import com.kiwobollae.api.infra.service.IdempotencyService;
import com.kiwobollae.api.payment.dto.response.PaymentRefundResponse;
import com.kiwobollae.api.payment.entity.Payment;
import com.kiwobollae.api.payment.entity.PaymentRefund;
import com.kiwobollae.api.payment.entity.PaymentRefundAttempt;
import com.kiwobollae.api.payment.entity.enums.PaymentProviderType;
import com.kiwobollae.api.payment.entity.enums.PaymentRefundAttemptStatus;
import com.kiwobollae.api.payment.entity.enums.PaymentRefundStatus;
import com.kiwobollae.api.payment.entity.enums.PaymentStatus;
import com.kiwobollae.api.payment.provider.PaymentProvider;
import com.kiwobollae.api.payment.provider.PaymentRefundCommand;
import com.kiwobollae.api.payment.provider.PaymentRefundResult;
import com.kiwobollae.api.payment.repository.PaymentRefundAttemptRepository;
import com.kiwobollae.api.payment.repository.PaymentRefundRepository;
import com.kiwobollae.api.payment.repository.PaymentRepository;
import com.kiwobollae.api.point.service.WalletService;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class PaymentRefundTransactionService {

	private static final String REFUND_API_TYPE = "PAYMENT_REFUND";

	private final PaymentRepository paymentRepository;
	private final PaymentRefundRepository paymentRefundRepository;
	private final PaymentRefundAttemptRepository paymentRefundAttemptRepository;
	private final WalletService walletService;
	private final PaymentProvider paymentProvider;
	private final IdempotencyService idempotencyService;
	private final ObjectMapper objectMapper;
	private final Clock seoulClock;

	@Transactional
	public PaymentRefundPreparation prepare(
			Long userId,
			String idempotencyKey,
			String requestHash,
			Long paymentId,
			String reason
	) {
		IdempotencyExecution idempotency = idempotencyService.start(
				userId,
				REFUND_API_TYPE,
				idempotencyKey,
				requestHash
		);
		if (idempotency.replay()) {
			return PaymentRefundPreparation.replay(
					readSnapshot(idempotency.key().getResponseSnapshot())
			);
		}

		Payment payment = paymentRepository.findDetailsByIdAndUserIdForUpdate(paymentId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
		validateRefundable(payment);

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

		PaymentRefundAttempt attempt = paymentRefundAttemptRepository.saveAndFlush(
				PaymentRefundAttempt.builder()
						.paymentId(paymentId)
						.userId(userId)
						.cashAmount(payment.getCashAmount())
						.pointAmount(payment.getPointAmount())
						.status(PaymentRefundAttemptStatus.STARTED)
						.reason(reason)
						.startedAt(LocalDateTime.now(seoulClock))
						.build()
		);

		return PaymentRefundPreparation.pending(
				userId,
				idempotencyKey,
				requestHash,
				paymentId,
				refund.getId(),
				attempt.getId(),
				payment.getPointAmount(),
				new PaymentRefundCommand(
						payment.getProviderOrderId(),
						payment.getProviderPaymentKey(),
						payment.getCashAmount(),
						reason
				)
		);
	}

	@Transactional
	public PaymentRefundResponse complete(
			PaymentRefundPreparation preparation,
			PaymentRefundResult providerResult
	) {
		IdempotencyExecution idempotency = lockIdempotency(preparation);
		if (idempotency.replay()) {
			return readSnapshot(idempotency.key().getResponseSnapshot());
		}

		Payment payment = paymentRepository
				.findDetailsByIdAndUserIdForUpdate(preparation.paymentId(), preparation.userId())
				.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
		if (payment.getStatus() != PaymentStatus.COMPLETED) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATE);
		}

		int paymentUpdated = paymentRepository.updateStatusOnlyIfCurrent(
				payment.getId(),
				PaymentStatus.COMPLETED,
				PaymentStatus.REFUNDED
		);
		if (paymentUpdated == 0) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATE);
		}

		int refundUpdated = paymentRefundRepository.completeIfCurrent(
				preparation.refundId(),
				PaymentRefundStatus.REQUESTED,
				PaymentRefundStatus.COMPLETED,
				providerResult.refundKey(),
				LocalDateTime.now(seoulClock)
		);
		if (refundUpdated == 0) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATE);
		}

		settleAttempt(preparation.attemptId());
		PaymentRefundResponse response = getRefundResponse(preparation.refundId());
		idempotencyService.succeed(
				idempotency.key(),
				200,
				writeSnapshot(response),
				"PAYMENT_REFUND",
				preparation.refundId()
		);
		return response;
	}

	@Transactional
	public void failDefinitively(PaymentRefundPreparation preparation) {
		IdempotencyExecution idempotency = lockIdempotency(preparation);
		if (idempotency.replay()) {
			return;
		}

		int refundUpdated = paymentRefundRepository.failIfCurrent(
				preparation.refundId(),
				PaymentRefundStatus.REQUESTED,
				PaymentRefundStatus.FAILED,
				LocalDateTime.now(seoulClock)
		);
		if (refundUpdated == 0) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATE);
		}

		walletService.restorePaidPointForFailedPaymentRefund(
				preparation.userId(),
				preparation.pointAmount(),
				preparation.refundId()
		);
		settleAttempt(preparation.attemptId());
		idempotencyService.fail(idempotency.key());
	}

	private void validateRefundable(Payment payment) {
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
	}

	private IdempotencyExecution lockIdempotency(PaymentRefundPreparation preparation) {
		return idempotencyService.lockForCompletion(
				preparation.userId(),
				REFUND_API_TYPE,
				preparation.idempotencyKey(),
				preparation.requestHash()
		);
	}

	private void settleAttempt(Long attemptId) {
		int attemptSettled = paymentRefundAttemptRepository.settleIfCurrent(
				attemptId,
				PaymentRefundAttemptStatus.STARTED,
				PaymentRefundAttemptStatus.SETTLED,
				LocalDateTime.now(seoulClock)
		);
		if (attemptSettled == 0) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATE);
		}
	}

	private PaymentRefundResponse getRefundResponse(Long refundId) {
		return PaymentRefundResponse.from(paymentRefundRepository.findDetailsById(refundId)
				.orElseThrow(() -> new BusinessException(ErrorCode.COMMON_DATA_CONFLICT)));
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
