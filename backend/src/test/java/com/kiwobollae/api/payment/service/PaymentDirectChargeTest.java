package com.kiwobollae.api.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.infra.entity.IdempotencyKey;
import com.kiwobollae.api.infra.service.IdempotencyExecution;
import com.kiwobollae.api.infra.service.IdempotencyService;
import com.kiwobollae.api.payment.dto.request.PaymentRequest;
import com.kiwobollae.api.payment.dto.response.PaymentResponse;
import com.kiwobollae.api.payment.entity.Payment;
import com.kiwobollae.api.payment.entity.enums.PaymentProviderType;
import com.kiwobollae.api.payment.entity.enums.PaymentStatus;
import com.kiwobollae.api.payment.provider.PaymentProvider;
import com.kiwobollae.api.payment.repository.PaymentRefundRepository;
import com.kiwobollae.api.payment.repository.PaymentRepository;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class PaymentDirectChargeTest {

	@Mock private PaymentRepository paymentRepository;
	@Mock private PaymentRefundRepository paymentRefundRepository;
	@Mock private UserRepository userRepository;
	@Mock private PaymentProvider paymentProvider;
	@Mock private IdempotencyService idempotencyService;
	@Mock private PaymentConfirmationTransactionService paymentConfirmationTransactionService;
	@Mock private ObjectMapper objectMapper;

	private PaymentService paymentService;

	@BeforeEach
	void setUp() {
		paymentService = new PaymentService(
				paymentRepository,
				paymentRefundRepository,
				userRepository,
				paymentProvider,
				idempotencyService,
				paymentConfirmationTransactionService,
				objectMapper
		);
	}

	@Test
	void requestChargeCreatesOneWonPerPointPayment() throws Exception {
		User user = org.mockito.Mockito.mock(User.class);
		IdempotencyKey idempotencyKey = org.mockito.Mockito.mock(IdempotencyKey.class);
		ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);

		given(user.getId()).willReturn(7L);
		given(userRepository.getReferenceById(7L)).willReturn(user);
		given(idempotencyService.start(
				org.mockito.ArgumentMatchers.eq(7L),
				org.mockito.ArgumentMatchers.eq("PAYMENT_CHARGE"),
				org.mockito.ArgumentMatchers.eq("charge-key"),
				org.mockito.ArgumentMatchers.anyString()
		)).willReturn(new IdempotencyExecution(idempotencyKey, false));
		given(paymentProvider.getType()).willReturn(PaymentProviderType.TOSS);
		given(paymentRepository.save(paymentCaptor.capture()))
				.willAnswer(invocation -> invocation.getArgument(0));
		given(objectMapper.writeValueAsString(org.mockito.ArgumentMatchers.any(PaymentResponse.class)))
				.willReturn("{}");

		PaymentResponse response = paymentService.requestCharge(
				7L,
				"charge-key",
				new PaymentRequest(12_340L)
		);

		Payment payment = paymentCaptor.getValue();
		assertThat(payment.getCashAmount()).isEqualTo(12_340L);
		assertThat(payment.getPointAmount()).isEqualTo(12_340L);
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
		assertThat(response.cashAmount()).isEqualTo(response.pointAmount());
	}

	@ParameterizedTest
	@MethodSource("invalidChargeAmounts")
	void requestChargeRejectsAmountOutsidePolicy(Long pointAmount) {
		assertThatThrownBy(() -> paymentService.requestCharge(
				7L,
				"charge-key",
				new PaymentRequest(pointAmount)
		)).isInstanceOfSatisfying(BusinessException.class,
				exception -> assertThat(exception.getErrorCode())
						.isEqualTo(ErrorCode.PAYMENT_CHARGE_AMOUNT_INVALID));
	}

	private static Stream<Long> invalidChargeAmounts() {
		return Stream.of(null, 999L, 2_801L, 300_001L);
	}
}
