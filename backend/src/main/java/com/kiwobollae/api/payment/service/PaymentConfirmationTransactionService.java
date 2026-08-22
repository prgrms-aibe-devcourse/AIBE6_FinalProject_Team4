package com.kiwobollae.api.payment.service;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.infra.service.IdempotencyExecution;
import com.kiwobollae.api.infra.service.IdempotencyService;
import com.kiwobollae.api.payment.dto.request.PaymentConfirmRequest;
import com.kiwobollae.api.payment.dto.response.PaymentResponse;
import com.kiwobollae.api.payment.entity.Payment;
import com.kiwobollae.api.payment.entity.enums.PaymentProviderType;
import com.kiwobollae.api.payment.entity.enums.PaymentStatus;
import com.kiwobollae.api.payment.provider.PaymentConfirmCommand;
import com.kiwobollae.api.payment.provider.PaymentConfirmResult;
import com.kiwobollae.api.payment.provider.PaymentProvider;
import com.kiwobollae.api.payment.repository.PaymentRepository;
import com.kiwobollae.api.point.service.PointCreditService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class PaymentConfirmationTransactionService {

	private static final String CONFIRM_API_TYPE = "PAYMENT_CONFIRM";

	private final PaymentRepository paymentRepository;
	private final PaymentProvider paymentProvider;
	private final PointCreditService pointCreditService;
	private final IdempotencyService idempotencyService;
	private final PaymentStateService paymentStateService;
	private final ObjectMapper objectMapper;

	@Transactional
	public PaymentConfirmationPreparation prepare(
			Long userId,
			String idempotencyKey,
			String requestHash,
			PaymentConfirmRequest request
	) {
		IdempotencyExecution idempotency = idempotencyService.start(
				userId,
				CONFIRM_API_TYPE,
				idempotencyKey,
				requestHash
		);
		if (idempotency.replay()) {
			return PaymentConfirmationPreparation.replay(
					readSnapshot(idempotency.key().getResponseSnapshot())
			);
		}

		Payment payment = paymentRepository
				.findDetailsByProviderOrderIdAndUserId(request.providerOrderId(), userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
		if (payment.getStatus() != PaymentStatus.PENDING) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATE);
		}
		validateTossPayment(payment);

		if (!payment.getCashAmount().equals(request.amount())) {
			paymentStateService.failPendingPayment(payment.getId());
			throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
		}

		return PaymentConfirmationPreparation.pending(
				userId,
				idempotencyKey,
				requestHash,
				payment.getId(),
				new PaymentConfirmCommand(
						request.providerOrderId(),
						request.paymentKey(),
						request.amount()
				)
		);
	}

	@Transactional
	public PaymentResponse complete(
			PaymentConfirmationPreparation preparation,
			PaymentConfirmResult confirmResult
	) {
		IdempotencyExecution idempotency = idempotencyService.lockForCompletion(
				preparation.userId(),
				CONFIRM_API_TYPE,
				preparation.idempotencyKey(),
				preparation.requestHash()
		);
		if (idempotency.replay()) {
			return readSnapshot(idempotency.key().getResponseSnapshot());
		}

		Payment payment = getPaymentDetails(preparation.paymentId());
		if (payment.getStatus() != PaymentStatus.PENDING) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATE);
		}
		if (confirmResult == null) {
			throw new BusinessException(ErrorCode.PAYMENT_PROVIDER_INVALID_RESPONSE);
		}

		if (!confirmResult.successful()) {
			changePendingStatus(payment.getId(), PaymentStatus.FAILED, null, null);
			PaymentResponse response = PaymentResponse.from(
					getPaymentDetails(payment.getId()),
					confirmResult.message()
			);
			completeIdempotency(idempotency, response, payment.getId());
			return response;
		}

		LocalDateTime approvedAt = LocalDateTime.now();
		changePendingStatus(
				payment.getId(),
				PaymentStatus.COMPLETED,
				preparation.command().paymentKey(),
				approvedAt
		);
		pointCreditService.creditPaidPoint(
				preparation.userId(),
				payment.getPointAmount(),
				payment.getId()
		);

		PaymentResponse response = PaymentResponse.from(
				getPaymentDetails(payment.getId()),
				confirmResult.message()
		);
		completeIdempotency(idempotency, response, payment.getId());
		return response;
	}

	@Transactional
	public void failBeforeProvider(PaymentConfirmationPreparation preparation) {
		IdempotencyExecution idempotency = idempotencyService.lockForCompletion(
				preparation.userId(),
				CONFIRM_API_TYPE,
				preparation.idempotencyKey(),
				preparation.requestHash()
		);
		if (!idempotency.replay()) {
			idempotencyService.fail(idempotency.key());
		}
	}

	private void validateTossPayment(Payment payment) {
		if (payment.getProvider() != PaymentProviderType.TOSS
				|| paymentProvider.getType() != PaymentProviderType.TOSS) {
			throw new BusinessException(
					ErrorCode.PAYMENT_INVALID_STATE,
					"Toss 결제만 승인할 수 있습니다."
			);
		}
	}

	private void changePendingStatus(
			Long paymentId,
			PaymentStatus targetStatus,
			String paymentKey,
			LocalDateTime approvedAt
	) {
		int updated = paymentRepository.updateStatusIfCurrent(
				paymentId,
				PaymentStatus.PENDING,
				targetStatus,
				paymentKey,
				approvedAt
		);
		if (updated == 0) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATE);
		}
	}

	private Payment getPaymentDetails(Long paymentId) {
		return paymentRepository.findDetailsById(paymentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
	}

	private void completeIdempotency(
			IdempotencyExecution idempotency,
			PaymentResponse response,
			Long paymentId
	) {
		idempotencyService.succeed(
				idempotency.key(),
				200,
				writeSnapshot(response),
				"PAYMENT",
				paymentId
		);
	}

	private String writeSnapshot(PaymentResponse response) {
		try {
			return objectMapper.writeValueAsString(response);
		} catch (JacksonException exception) {
			throw new IllegalStateException("멱등성 응답 저장에 실패했습니다.", exception);
		}
	}

	private PaymentResponse readSnapshot(String snapshot) {
		try {
			return objectMapper.readValue(snapshot, PaymentResponse.class);
		} catch (JacksonException exception) {
			throw new IllegalStateException("멱등성 응답 복원에 실패했습니다.", exception);
		}
	}
}
