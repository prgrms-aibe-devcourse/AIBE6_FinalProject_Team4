package com.kiwobollae.api.payment.service;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.infra.service.IdempotencyExecution;
import com.kiwobollae.api.infra.service.IdempotencyService;
import com.kiwobollae.api.payment.dto.request.ChargeProductRequest;
import com.kiwobollae.api.payment.dto.request.PaymentConfirmRequest;
import com.kiwobollae.api.payment.dto.request.PaymentRequest;
import com.kiwobollae.api.payment.dto.response.ChargeProductResponse;
import com.kiwobollae.api.payment.dto.response.PaymentHistoryResponse;
import com.kiwobollae.api.payment.dto.response.PaymentRefundResponse;
import com.kiwobollae.api.payment.dto.response.PaymentResponse;
import com.kiwobollae.api.payment.entity.ChargeProduct;
import com.kiwobollae.api.payment.entity.Payment;
import com.kiwobollae.api.payment.entity.enums.PaymentStatus;
import com.kiwobollae.api.payment.provider.PaymentConfirmCommand;
import com.kiwobollae.api.payment.provider.PaymentConfirmResult;
import com.kiwobollae.api.payment.provider.PaymentProvider;
import com.kiwobollae.api.payment.provider.PaymentScenario;
import com.kiwobollae.api.payment.repository.ChargeProductRepository;
import com.kiwobollae.api.payment.repository.PaymentRefundRepository;
import com.kiwobollae.api.payment.repository.PaymentRepository;
import com.kiwobollae.api.point.service.PointCreditService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

	private static final String CHARGE_API_TYPE = "PAYMENT_CHARGE";
	private static final String CONFIRM_API_TYPE = "PAYMENT_CONFIRM";

	private final ChargeProductRepository chargeProductRepository;
	private final PaymentRepository paymentRepository;
	private final PaymentRefundRepository paymentRefundRepository;
	private final UserRepository userRepository;
	private final PaymentProvider paymentProvider;
	private final PointCreditService pointCreditService;
	private final IdempotencyService idempotencyService;
	private final PaymentStateService paymentStateService;
	private final ObjectMapper objectMapper;

	public List<ChargeProductResponse> getChargeProducts() {
		return chargeProductRepository.findAllByIsActiveTrueOrderByPriceAsc().stream()
				.map(ChargeProductResponse::from)
				.toList();
	}

	@Transactional
	public PaymentResponse requestCharge(Long userId, String idempotencyKey, PaymentRequest request) {
		User user = userRepository.getReferenceById(userId);
		validateIdempotencyKey(idempotencyKey);
		IdempotencyExecution idempotency = idempotencyService.start(
				userId,
				CHARGE_API_TYPE,
				idempotencyKey,
				sha256("chargeProductId=" + request.chargeProductId())
		);
		if (idempotency.replay()) {
			return readSnapshot(idempotency.key().getResponseSnapshot());
		}

		ChargeProduct chargeProduct = getAvailableChargeProduct(request.chargeProductId());
		Payment payment = Payment.builder()
				.user(user)
				.chargeProduct(chargeProduct)
				.cashAmount(chargeProduct.getPrice())
				.pointAmount(chargeProduct.getPointAmount())
				.status(PaymentStatus.PENDING)
				.provider(paymentProvider.getType())
				.providerOrderId(createProviderOrderId())
				.build();
		Payment savedPayment = paymentRepository.save(payment);
		PaymentResponse response = PaymentResponse.from(savedPayment, "결제 요청이 생성되었습니다.");
		completeIdempotency(idempotency, 201, response, savedPayment.getId());
		return response;
	}

	@Transactional
	public PaymentResponse confirmPayment(Long userId, String idempotencyKey, PaymentConfirmRequest request) {
		User user = userRepository.getReferenceById(userId);
		validateIdempotencyKey(idempotencyKey);
		IdempotencyExecution idempotency = idempotencyService.start(
				userId,
				CONFIRM_API_TYPE,
				idempotencyKey,
				sha256(normalizedConfirmRequest(request))
		);
		if (idempotency.replay()) {
			return readSnapshot(idempotency.key().getResponseSnapshot());
		}

		Payment payment = paymentRepository
				.findDetailsByProviderOrderIdAndUserId(request.providerOrderId(), userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
		if (payment.getStatus() != PaymentStatus.PENDING) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATE);
		}

		if (!payment.getCashAmount().equals(request.amount())) {
			paymentStateService.failPendingPayment(payment.getId());
			throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
		}

		PaymentConfirmResult confirmResult = paymentProvider.confirm(new PaymentConfirmCommand(
				request.providerOrderId(),
				request.paymentKey(),
				request.amount(),
				request.scenario()
		));

		if (confirmResult.result() == PaymentScenario.FAILURE) {
			return finishWithoutCredit(
					payment,
					PaymentStatus.FAILED,
					null,
					confirmResult.message(),
					idempotency
			);
		}
		if (confirmResult.result() == PaymentScenario.CANCEL) {
			return finishWithoutCredit(
					payment,
					PaymentStatus.CANCELED,
					null,
					confirmResult.message(),
					idempotency
			);
		}

		LocalDateTime approvedAt = LocalDateTime.now();
		changePendingStatus(payment.getId(), PaymentStatus.PAID, request.paymentKey(), approvedAt);
		pointCreditService.creditPaidPoint(userId, payment.getPointAmount(), payment.getId());

		PaymentResponse response = PaymentResponse.from(
				getPaymentDetails(payment.getId()),
				confirmResult.message()
		);
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

	@Transactional
	public ChargeProductResponse createChargeProduct(ChargeProductRequest request) {
		ChargeProduct chargeProduct = ChargeProduct.builder()
				.name(request.name())
				.price(request.price())
				.pointAmount(request.pointAmount())
				.isActive(request.isActive())
				.build();
		return ChargeProductResponse.from(chargeProductRepository.save(chargeProduct));
	}

	@Transactional
	public ChargeProductResponse updateChargeProduct(Long productId, ChargeProductRequest request) {
		ChargeProduct chargeProduct = getChargeProduct(productId);
		chargeProduct.update(
				request.name(),
				request.price(),
				request.pointAmount(),
				request.isActive()
		);
		return ChargeProductResponse.from(chargeProduct);
	}

	@Transactional
	public void deactivateChargeProduct(Long productId) {
		getChargeProduct(productId).deactivate();
	}

	private PaymentResponse finishWithoutCredit(Payment payment, PaymentStatus status, String paymentKey,
			String message, IdempotencyExecution idempotency) {
		changePendingStatus(payment.getId(), status, paymentKey, null);
		PaymentResponse response = PaymentResponse.from(getPaymentDetails(payment.getId()), message);
		completeIdempotency(idempotency, 200, response, payment.getId());
		return response;
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

	private ChargeProduct getAvailableChargeProduct(Long productId) {
		ChargeProduct chargeProduct = getChargeProduct(productId);
		if (!chargeProduct.getIsActive()) {
			throw new BusinessException(ErrorCode.PAYMENT_CHARGE_PRODUCT_NOT_AVAILABLE);
		}
		return chargeProduct;
	}

	private ChargeProduct getChargeProduct(Long productId) {
		return chargeProductRepository.findById(productId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_CHARGE_PRODUCT_NOT_FOUND));
	}

	private String createProviderOrderId() {
		return "KWB-" + UUID.randomUUID();
	}

	private String normalizedConfirmRequest(PaymentConfirmRequest request) {
		return "providerOrderId=" + request.providerOrderId()
				+ "&paymentKey=" + request.paymentKey()
				+ "&amount=" + request.amount()
				+ "&scenario=" + request.scenario();
	}

	private void validateIdempotencyKey(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
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
