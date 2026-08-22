package com.kiwobollae.api.payment.service;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.infra.service.IdempotencyExecution;
import com.kiwobollae.api.infra.service.IdempotencyService;
import com.kiwobollae.api.payment.dto.request.PaymentConfirmRequest;
import com.kiwobollae.api.payment.dto.request.PaymentFailureRequest;
import com.kiwobollae.api.payment.dto.request.PaymentRequest;
import com.kiwobollae.api.payment.dto.response.PaymentHistoryResponse;
import com.kiwobollae.api.payment.dto.response.PaymentRefundResponse;
import com.kiwobollae.api.payment.dto.response.PaymentResponse;
import com.kiwobollae.api.payment.entity.Payment;
import com.kiwobollae.api.payment.entity.enums.PaymentStatus;
import com.kiwobollae.api.payment.provider.PaymentConfirmResult;
import com.kiwobollae.api.payment.provider.PaymentProvider;
import com.kiwobollae.api.payment.provider.PaymentProviderBusyException;
import com.kiwobollae.api.payment.repository.PaymentRefundRepository;
import com.kiwobollae.api.payment.repository.PaymentRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

	private static final String CHARGE_API_TYPE = "PAYMENT_CHARGE";
	private static final String FAILURE_API_TYPE = "PAYMENT_FAILURE";
	private static final String USER_CANCELED_CODE = "PAY_PROCESS_CANCELED";
	public static final long MIN_CHARGE_AMOUNT = 1_000L;
	public static final long MAX_CHARGE_AMOUNT = 300_000L;
	public static final long CHARGE_AMOUNT_UNIT = 10L;

	private final PaymentRepository paymentRepository;
	private final PaymentRefundRepository paymentRefundRepository;
	private final UserRepository userRepository;
	private final PaymentProvider paymentProvider;
	private final IdempotencyService idempotencyService;
	private final PaymentConfirmationTransactionService paymentConfirmationTransactionService;
	private final ObjectMapper objectMapper;

	@Transactional
	public PaymentResponse requestCharge(Long userId, String idempotencyKey, PaymentRequest request) {
		validateChargeAmount(request.pointAmount());
		User user = userRepository.getReferenceById(userId);
		validateIdempotencyKey(idempotencyKey);
		IdempotencyExecution idempotency = idempotencyService.start(
				userId,
				CHARGE_API_TYPE,
				idempotencyKey,
				sha256("pointAmount=" + request.pointAmount())
		);
		if (idempotency.replay()) {
			return readSnapshot(idempotency.key().getResponseSnapshot());
		}

		Payment payment = Payment.builder()
				.user(user)
				.cashAmount(request.pointAmount())
				.pointAmount(request.pointAmount())
				.status(PaymentStatus.PENDING)
				.provider(paymentProvider.getType())
				.providerOrderId(createProviderOrderId())
				.build();
		Payment savedPayment = paymentRepository.save(payment);
		PaymentResponse response = PaymentResponse.from(savedPayment, "결제 요청이 생성되었습니다.");
		completeIdempotency(idempotency, 201, response, savedPayment.getId());
		return response;
	}

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public PaymentResponse confirmPayment(Long userId, String idempotencyKey, PaymentConfirmRequest request) {
		validateIdempotencyKey(idempotencyKey);
		String requestHash = sha256(normalizedConfirmRequest(request));
		PaymentConfirmationPreparation preparation = paymentConfirmationTransactionService.prepare(
				userId,
				idempotencyKey,
				requestHash,
				request
		);
		if (preparation.replay()) {
			return preparation.replayResponse();
		}

		PaymentConfirmResult confirmResult;
		try {
			confirmResult = paymentProvider.confirm(preparation.command());
		} catch (PaymentProviderBusyException exception) {
			paymentConfirmationTransactionService.failBeforeProvider(preparation);
			throw exception;
		}
		return paymentConfirmationTransactionService.complete(preparation, confirmResult);
	}

	@Transactional
	public PaymentResponse failPayment(
			Long userId,
			String idempotencyKey,
			PaymentFailureRequest request
	) {
		validateIdempotencyKey(idempotencyKey);
		IdempotencyExecution idempotency = idempotencyService.start(
				userId,
				FAILURE_API_TYPE,
				idempotencyKey,
				sha256("providerOrderId=" + request.providerOrderId() + "&code=" + request.code())
		);
		if (idempotency.replay()) {
			return readSnapshot(idempotency.key().getResponseSnapshot());
		}

		Payment payment = paymentRepository
				.findDetailsByProviderOrderIdAndUserId(request.providerOrderId(), userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
		if (payment.getStatus() == PaymentStatus.PENDING) {
			changePendingStatus(payment.getId(), PaymentStatus.FAILED, null, null);
		} else if (payment.getStatus() != PaymentStatus.FAILED) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATE);
		}

		String message = USER_CANCELED_CODE.equals(request.code())
				? "결제를 취소했어요."
				: "결제를 완료하지 못했어요.";
		PaymentResponse response = PaymentResponse.from(getPaymentDetails(payment.getId()), message);
		completeIdempotency(idempotency, 200, response, payment.getId());
		return response;
	}

	public List<PaymentHistoryResponse> getPaymentHistory(Long userId) {
		return paymentRepository.findAllByUser_IdOrderByCreatedAtDesc(userId).stream()
				.map(payment -> new PaymentHistoryResponse(
						PaymentResponse.from(payment),
						paymentRefundRepository.findAllByPayment_IdOrderByCreatedAtDesc(payment.getId()).stream()
								.map(PaymentRefundResponse::from)
								.toList()
				))
				.toList();
	}

	private void changePendingStatus(Long paymentId, PaymentStatus targetStatus,
			String paymentKey, LocalDateTime approvedAt) {
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

	private String createProviderOrderId() {
		return "KWB-" + UUID.randomUUID();
	}

	private String normalizedConfirmRequest(PaymentConfirmRequest request) {
		return "providerOrderId=" + request.providerOrderId()
				+ "&paymentKey=" + request.paymentKey()
				+ "&amount=" + request.amount();
	}

	private void validateIdempotencyKey(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}
	}

	private void validateChargeAmount(Long pointAmount) {
		if (pointAmount == null
				|| pointAmount < MIN_CHARGE_AMOUNT
				|| pointAmount > MAX_CHARGE_AMOUNT
				|| pointAmount % CHARGE_AMOUNT_UNIT != 0) {
			throw new BusinessException(ErrorCode.PAYMENT_CHARGE_AMOUNT_INVALID);
		}
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

	private void completeIdempotency(IdempotencyExecution idempotency, int httpStatus,
			PaymentResponse response, Long paymentId) {
		idempotencyService.succeed(
				idempotency.key(),
				httpStatus,
				writeSnapshot(response),
				"PAYMENT",
				paymentId
		);
	}

	private String writeSnapshot(Object response) {
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
