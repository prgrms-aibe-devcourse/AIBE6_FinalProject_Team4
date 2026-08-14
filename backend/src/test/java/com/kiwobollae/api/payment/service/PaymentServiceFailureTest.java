package com.kiwobollae.api.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.infra.entity.IdempotencyKey;
import com.kiwobollae.api.infra.service.IdempotencyExecution;
import com.kiwobollae.api.infra.service.IdempotencyService;
import com.kiwobollae.api.payment.dto.request.PaymentFailureRequest;
import com.kiwobollae.api.payment.dto.response.PaymentResponse;
import com.kiwobollae.api.payment.entity.ChargeProduct;
import com.kiwobollae.api.payment.entity.Payment;
import com.kiwobollae.api.payment.entity.enums.PaymentProviderType;
import com.kiwobollae.api.payment.entity.enums.PaymentStatus;
import com.kiwobollae.api.payment.provider.PaymentProvider;
import com.kiwobollae.api.payment.repository.ChargeProductRepository;
import com.kiwobollae.api.payment.repository.PaymentRefundRepository;
import com.kiwobollae.api.payment.repository.PaymentRepository;
import com.kiwobollae.api.point.service.PointCreditService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class PaymentServiceFailureTest {

	@Mock private ChargeProductRepository chargeProductRepository;
	@Mock private PaymentRepository paymentRepository;
	@Mock private PaymentRefundRepository paymentRefundRepository;
	@Mock private UserRepository userRepository;
	@Mock private PaymentProvider paymentProvider;
	@Mock private PointCreditService pointCreditService;
	@Mock private IdempotencyService idempotencyService;
	@Mock private PaymentStateService paymentStateService;
	@Mock private ObjectMapper objectMapper;

	private PaymentService paymentService;

	@BeforeEach
	void setUp() {
		paymentService = new PaymentService(
				chargeProductRepository,
				paymentRepository,
				paymentRefundRepository,
				userRepository,
				paymentProvider,
				pointCreditService,
				idempotencyService,
				paymentStateService,
				objectMapper
		);
	}

	@Test
	void userCanceledCallbackChangesPendingPaymentToFailed() throws Exception {
		Payment pendingPayment = org.mockito.Mockito.mock(Payment.class);
		Payment failedPayment = payment(PaymentStatus.FAILED);
		IdempotencyKey idempotencyKey = org.mockito.Mockito.mock(IdempotencyKey.class);
		PaymentFailureRequest request = new PaymentFailureRequest(
				"KWB-order-21",
				"PAY_PROCESS_CANCELED"
		);
		given(idempotencyService.start(
				org.mockito.ArgumentMatchers.eq(7L),
				org.mockito.ArgumentMatchers.eq("PAYMENT_FAILURE"),
				org.mockito.ArgumentMatchers.eq("failure-KWB-order-21"),
				org.mockito.ArgumentMatchers.anyString()
		)).willReturn(new IdempotencyExecution(idempotencyKey, false));
		given(paymentRepository.findDetailsByProviderOrderIdAndUserId("KWB-order-21", 7L))
				.willReturn(Optional.of(pendingPayment));
		given(pendingPayment.getId()).willReturn(21L);
		given(pendingPayment.getStatus()).willReturn(PaymentStatus.PENDING);
		given(paymentRepository.updateStatusIfCurrent(
				21L,
				PaymentStatus.PENDING,
				PaymentStatus.FAILED,
				null,
				null
		)).willReturn(1);
		given(paymentRepository.findDetailsById(21L)).willReturn(Optional.of(failedPayment));
		given(objectMapper.writeValueAsString(org.mockito.ArgumentMatchers.any(PaymentResponse.class)))
				.willReturn("{}");

		PaymentResponse response = paymentService.failPayment(
				7L,
				"failure-KWB-order-21",
				request
		);

		assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
		assertThat(response.message()).isEqualTo("결제를 취소했어요.");
		verify(paymentRepository).updateStatusIfCurrent(
				21L,
				PaymentStatus.PENDING,
				PaymentStatus.FAILED,
				null,
				null
		);
	}

	private Payment payment(PaymentStatus status) {
		Payment payment = org.mockito.Mockito.mock(Payment.class);
		User user = org.mockito.Mockito.mock(User.class);
		ChargeProduct chargeProduct = org.mockito.Mockito.mock(ChargeProduct.class);
		given(payment.getId()).willReturn(21L);
		given(payment.getUser()).willReturn(user);
		given(user.getId()).willReturn(7L);
		given(payment.getChargeProduct()).willReturn(chargeProduct);
		given(chargeProduct.getId()).willReturn(3L);
		given(payment.getChargeProductName()).willReturn("1,000P 충전");
		given(payment.getCashAmount()).willReturn(1_000L);
		given(payment.getPointAmount()).willReturn(1_000L);
		given(payment.getStatus()).willReturn(status);
		given(payment.getProvider()).willReturn(PaymentProviderType.TOSS);
		given(payment.getProviderOrderId()).willReturn("KWB-order-21");
		given(payment.getCreatedAt()).willReturn(LocalDateTime.of(2026, 8, 3, 14, 0));
		return payment;
	}
}
