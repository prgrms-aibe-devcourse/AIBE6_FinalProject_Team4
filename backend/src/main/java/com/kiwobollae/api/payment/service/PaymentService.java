package com.kiwobollae.api.payment.service;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.infra.service.IdempotencyExecution;
import com.kiwobollae.api.infra.service.IdempotencyService;
import com.kiwobollae.api.payment.dto.request.ChargeProductCreateRequest;
import com.kiwobollae.api.payment.dto.request.ChargeProductUpdateRequest;
import com.kiwobollae.api.payment.dto.request.PaymentConfirmRequest;
import com.kiwobollae.api.payment.dto.request.PaymentFailureRequest;
import com.kiwobollae.api.payment.dto.request.PaymentRequest;
import com.kiwobollae.api.payment.dto.response.ChargeProductResponse;
import com.kiwobollae.api.payment.dto.response.PaymentHistoryResponse;
import com.kiwobollae.api.payment.dto.response.PaymentRefundResponse;
import com.kiwobollae.api.payment.dto.response.PaymentResponse;
import com.kiwobollae.api.payment.entity.ChargeProduct;
import com.kiwobollae.api.payment.entity.Payment;
import com.kiwobollae.api.payment.entity.enums.PaymentProviderType;
import com.kiwobollae.api.payment.entity.enums.PaymentStatus;
import com.kiwobollae.api.payment.provider.PaymentConfirmCommand;
import com.kiwobollae.api.payment.provider.PaymentConfirmResult;
import com.kiwobollae.api.payment.provider.PaymentProvider;
import com.kiwobollae.api.payment.repository.ChargeProductRepository;
import com.kiwobollae.api.payment.repository.PaymentRefundRepository;
import com.kiwobollae.api.payment.repository.PaymentRepository;
import com.kiwobollae.api.point.service.PointCreditService;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
	private static final String FAILURE_API_TYPE = "PAYMENT_FAILURE";
	private static final String ADMIN_CHARGE_PRODUCT_CREATE_API_TYPE =
			"ADMIN_CHARGE_PRODUCT_CREATE";
	private static final String USER_CANCELED_CODE = "PAY_PROCESS_CANCELED";
	private static final long CHARGE_PRODUCT_MIN_POINT_RATE_PERCENT = 100L;
	private static final long CHARGE_PRODUCT_MAX_POINT_RATE_PERCENT = 150L;

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

	public List<ChargeProductResponse> getAdminChargeProducts() {
		return chargeProductRepository.findAllByOrderByPriceAscIdAsc().stream()
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
				.chargeProductName(chargeProduct.getName())
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
		validateTossPayment(payment);

		if (!payment.getCashAmount().equals(request.amount())) {
			paymentStateService.failPendingPayment(payment.getId());
			throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
		}

		PaymentConfirmResult confirmResult = paymentProvider.confirm(new PaymentConfirmCommand(
				request.providerOrderId(),
				request.paymentKey(),
				request.amount()
		));

		if (!confirmResult.successful()) {
			return finishWithoutCredit(
					payment,
					PaymentStatus.FAILED,
					null,
					confirmResult.message(),
					idempotency
			);
		}

		LocalDateTime approvedAt = LocalDateTime.now();
		changePendingStatus(payment.getId(), PaymentStatus.COMPLETED, request.paymentKey(), approvedAt);
		pointCreditService.creditPaidPoint(userId, payment.getPointAmount(), payment.getId());

		PaymentResponse response = PaymentResponse.from(
				getPaymentDetails(payment.getId()),
				confirmResult.message()
		);
		completeIdempotency(idempotency, 200, response, payment.getId());
		return response;
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

	@Transactional
	public ChargeProductResponse createChargeProduct(
			Long adminUserId,
			String idempotencyKey,
			ChargeProductCreateRequest request
	) {
		validateIdempotencyKey(idempotencyKey);
		validateChargeProductPointRate(request.price(), request.pointAmount());
		String normalizedName = request.name().strip();
		IdempotencyExecution idempotency = idempotencyService.start(
				adminUserId,
				ADMIN_CHARGE_PRODUCT_CREATE_API_TYPE,
				idempotencyKey,
				sha256(normalizedChargeProductCreateRequest(request, normalizedName))
		);
		if (idempotency.replay()) {
			return readChargeProductSnapshot(idempotency.key().getResponseSnapshot());
		}

		ChargeProduct chargeProduct = ChargeProduct.builder()
				.name(normalizedName)
				.price(request.price())
				.pointAmount(request.pointAmount())
				.isActive(request.isActive())
				.build();
		ChargeProductResponse response = ChargeProductResponse.from(
				chargeProductRepository.saveAndFlush(chargeProduct)
		);
		idempotencyService.succeed(
				idempotency.key(),
				201,
				writeSnapshot(response),
				"CHARGE_PRODUCT",
				response.id()
		);
		return response;
	}

	@Transactional
	public ChargeProductResponse updateChargeProduct(Long productId, ChargeProductUpdateRequest request) {
		validateChargeProductPointRate(request.price(), request.pointAmount());
		ChargeProduct chargeProduct = getChargeProduct(productId);
		if (!Objects.equals(chargeProduct.getVersion(), request.version())) {
			throw new ObjectOptimisticLockingFailureException(ChargeProduct.class, productId);
		}
		chargeProduct.update(
				request.name().strip(),
				request.price(),
				request.pointAmount(),
				request.isActive()
		);
		chargeProductRepository.flush();
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
				+ "&amount=" + request.amount();
	}

	private String normalizedChargeProductCreateRequest(
			ChargeProductCreateRequest request,
			String normalizedName
	) {
		return "nameLength=" + normalizedName.length()
				+ "&name=" + normalizedName
				+ "&price=" + request.price()
				+ "&pointAmount=" + request.pointAmount()
				+ "&isActive=" + request.isActive();
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

	private void validateIdempotencyKey(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}
	}

	private void validateChargeProductPointRate(Long price, Long pointAmount) {
		if (price == null || price < 1 || pointAmount == null || pointAmount < 1) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}

		BigInteger scaledPointAmount = BigInteger.valueOf(pointAmount).multiply(
				BigInteger.valueOf(CHARGE_PRODUCT_MIN_POINT_RATE_PERCENT));
		BigInteger scaledPrice = BigInteger.valueOf(price).multiply(
				BigInteger.valueOf(CHARGE_PRODUCT_MAX_POINT_RATE_PERCENT));
		if (pointAmount < price || scaledPointAmount.compareTo(scaledPrice) > 0) {
			throw new BusinessException(ErrorCode.PAYMENT_CHARGE_PRODUCT_POINT_RATE_INVALID);
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

	private ChargeProductResponse readChargeProductSnapshot(String snapshot) {
		try {
			return objectMapper.readValue(snapshot, ChargeProductResponse.class);
		} catch (JacksonException exception) {
			throw new IllegalStateException("멱등성 충전 상품 응답 복원에 실패했습니다.", exception);
		}
	}
}
