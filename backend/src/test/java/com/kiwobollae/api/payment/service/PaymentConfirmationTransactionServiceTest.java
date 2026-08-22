package com.kiwobollae.api.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.infra.entity.IdempotencyKey;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class PaymentConfirmationTransactionServiceTest {

	@Mock private PaymentRepository paymentRepository;
	@Mock private PaymentProvider paymentProvider;
	@Mock private PointCreditService pointCreditService;
	@Mock private IdempotencyService idempotencyService;
	@Mock private PaymentStateService paymentStateService;
	@Mock private ObjectMapper objectMapper;

	private PaymentConfirmationTransactionService transactionService;

	@BeforeEach
	void setUp() {
		transactionService = new PaymentConfirmationTransactionService(
				paymentRepository,
				paymentProvider,
				pointCreditService,
				idempotencyService,
				paymentStateService,
				objectMapper
		);
	}

	@Test
	void preparesValidatedPaymentThenCompletesStatusAndPointCredit() throws Exception {
		IdempotencyKey idempotencyKey = org.mockito.Mockito.mock(IdempotencyKey.class);
		Payment payment = payment(PaymentStatus.PENDING);
		PaymentConfirmRequest request = new PaymentConfirmRequest("order-21", "payment-key-21", 5_000L);

		given(idempotencyService.start(7L, "PAYMENT_CONFIRM", "confirm-key", "request-hash"))
				.willReturn(new IdempotencyExecution(idempotencyKey, false));
		given(paymentRepository.findDetailsByProviderOrderIdAndUserId("order-21", 7L))
				.willReturn(Optional.of(payment));
		given(paymentProvider.getType()).willReturn(PaymentProviderType.TOSS);

		PaymentConfirmationPreparation preparation = transactionService.prepare(
				7L,
				"confirm-key",
				"request-hash",
				request
		);

		assertThat(preparation.replay()).isFalse();
		assertThat(preparation.command()).isEqualTo(
				new PaymentConfirmCommand("order-21", "payment-key-21", 5_000L)
		);

		given(idempotencyService.lockForCompletion(
				7L,
				"PAYMENT_CONFIRM",
				"confirm-key",
				"request-hash"
		)).willReturn(new IdempotencyExecution(idempotencyKey, false));
		given(paymentRepository.findDetailsById(21L)).willReturn(Optional.of(payment));
		given(paymentRepository.updateStatusIfCurrent(
				org.mockito.ArgumentMatchers.eq(21L),
				org.mockito.ArgumentMatchers.eq(PaymentStatus.PENDING),
				org.mockito.ArgumentMatchers.eq(PaymentStatus.COMPLETED),
				org.mockito.ArgumentMatchers.eq("payment-key-21"),
				org.mockito.ArgumentMatchers.any(LocalDateTime.class)
		)).willReturn(1);
		given(objectMapper.writeValueAsString(org.mockito.ArgumentMatchers.any(PaymentResponse.class)))
				.willReturn("{}");

		PaymentResponse response = transactionService.complete(
				preparation,
				PaymentConfirmResult.success()
		);

		assertThat(response.id()).isEqualTo(21L);
		verify(pointCreditService).creditPaidPoint(7L, 5_000L, 21L);
		verify(idempotencyService).succeed(
				org.mockito.ArgumentMatchers.same(idempotencyKey),
				org.mockito.ArgumentMatchers.eq(200),
				org.mockito.ArgumentMatchers.eq("{}"),
				org.mockito.ArgumentMatchers.eq("PAYMENT"),
				org.mockito.ArgumentMatchers.eq(21L)
		);
	}

	private Payment payment(PaymentStatus status) {
		Payment payment = org.mockito.Mockito.mock(Payment.class);
		User user = org.mockito.Mockito.mock(User.class);
		given(payment.getId()).willReturn(21L);
		given(payment.getUser()).willReturn(user);
		given(user.getId()).willReturn(7L);
		given(payment.getCashAmount()).willReturn(5_000L);
		given(payment.getPointAmount()).willReturn(5_000L);
		given(payment.getStatus()).willReturn(status);
		given(payment.getProvider()).willReturn(PaymentProviderType.TOSS);
		given(payment.getProviderOrderId()).willReturn("order-21");
		given(payment.getProviderPaymentKey()).willReturn("payment-key-21");
		given(payment.getCreatedAt()).willReturn(LocalDateTime.of(2026, 8, 21, 18, 0));
		return payment;
	}
}
