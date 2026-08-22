package com.kiwobollae.api.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.infra.service.IdempotencyService;
import com.kiwobollae.api.payment.dto.request.PaymentConfirmRequest;
import com.kiwobollae.api.payment.dto.response.PaymentResponse;
import com.kiwobollae.api.payment.provider.PaymentConfirmCommand;
import com.kiwobollae.api.payment.provider.PaymentConfirmResult;
import com.kiwobollae.api.payment.provider.PaymentProvider;
import com.kiwobollae.api.payment.provider.PaymentProviderBusyException;
import com.kiwobollae.api.payment.repository.PaymentRefundRepository;
import com.kiwobollae.api.payment.repository.PaymentRepository;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

class PaymentConfirmationTransactionBoundaryTest {

	@Test
	void providerCallIsPlacedBetweenShortTransactionalPhases() {
		PaymentRepository paymentRepository = mock(PaymentRepository.class);
		PaymentRefundRepository paymentRefundRepository = mock(PaymentRefundRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		PaymentProvider paymentProvider = mock(PaymentProvider.class);
		IdempotencyService idempotencyService = mock(IdempotencyService.class);
		PaymentConfirmationTransactionService transactionService =
				mock(PaymentConfirmationTransactionService.class);
		ObjectMapper objectMapper = mock(ObjectMapper.class);
		PaymentService paymentService = new PaymentService(
				paymentRepository,
				paymentRefundRepository,
				userRepository,
				paymentProvider,
				idempotencyService,
				transactionService,
				objectMapper
		);
		PaymentConfirmRequest request = new PaymentConfirmRequest("order-1", "payment-key-1", 5_000L);
		PaymentConfirmCommand command = new PaymentConfirmCommand("order-1", "payment-key-1", 5_000L);
		PaymentConfirmationPreparation preparation = PaymentConfirmationPreparation.pending(
				7L,
				"confirm-key",
				"request-hash",
				21L,
				command
		);
		PaymentConfirmResult providerResult = PaymentConfirmResult.success();
		PaymentResponse expectedResponse = mock(PaymentResponse.class);

		given(transactionService.prepare(
				org.mockito.ArgumentMatchers.eq(7L),
				org.mockito.ArgumentMatchers.eq("confirm-key"),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.eq(request)
		)).willReturn(preparation);
		given(paymentProvider.confirm(command)).willReturn(providerResult);
		given(transactionService.complete(preparation, providerResult)).willReturn(expectedResponse);

		PaymentResponse response = paymentService.confirmPayment(7L, "confirm-key", request);

		assertThat(response).isSameAs(expectedResponse);
		InOrder order = inOrder(transactionService, paymentProvider);
		order.verify(transactionService).prepare(
				org.mockito.ArgumentMatchers.eq(7L),
				org.mockito.ArgumentMatchers.eq("confirm-key"),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.eq(request)
		);
		order.verify(paymentProvider).confirm(command);
		order.verify(transactionService).complete(preparation, providerResult);
	}

	@Test
	void confirmationOrchestratorSuspendsTransactionsAroundProviderCall() throws Exception {
		Method confirmMethod = PaymentService.class.getMethod(
				"confirmPayment",
				Long.class,
				String.class,
				PaymentConfirmRequest.class
		);
		Transactional annotation = confirmMethod.getAnnotation(Transactional.class);

		assertThat(annotation).isNotNull();
		assertThat(annotation.propagation()).isEqualTo(Propagation.NOT_SUPPORTED);
		assertTransactional(PaymentConfirmationTransactionService.class.getMethod(
				"prepare",
				Long.class,
				String.class,
				String.class,
				PaymentConfirmRequest.class
		));
		assertTransactional(PaymentConfirmationTransactionService.class.getMethod(
				"complete",
				PaymentConfirmationPreparation.class,
				PaymentConfirmResult.class
		));
		assertTransactional(PaymentConfirmationTransactionService.class.getMethod(
				"failBeforeProvider",
				PaymentConfirmationPreparation.class
		));
	}

	@Test
	void bulkheadRejectionFailsPreparationBeforeReturningUnavailable() {
		PaymentRepository paymentRepository = mock(PaymentRepository.class);
		PaymentRefundRepository paymentRefundRepository = mock(PaymentRefundRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		PaymentProvider paymentProvider = mock(PaymentProvider.class);
		IdempotencyService idempotencyService = mock(IdempotencyService.class);
		PaymentConfirmationTransactionService transactionService =
				mock(PaymentConfirmationTransactionService.class);
		PaymentService paymentService = new PaymentService(
				paymentRepository,
				paymentRefundRepository,
				userRepository,
				paymentProvider,
				idempotencyService,
				transactionService,
				mock(ObjectMapper.class)
		);
		PaymentConfirmRequest request = new PaymentConfirmRequest("order-1", "payment-key-1", 5_000L);
		PaymentConfirmCommand command = new PaymentConfirmCommand("order-1", "payment-key-1", 5_000L);
		PaymentConfirmationPreparation preparation = PaymentConfirmationPreparation.pending(
				7L, "confirm-key", "request-hash", 21L, command);
		given(transactionService.prepare(
				org.mockito.ArgumentMatchers.eq(7L),
				org.mockito.ArgumentMatchers.eq("confirm-key"),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.eq(request)
		)).willReturn(preparation);
		given(paymentProvider.confirm(command)).willThrow(new PaymentProviderBusyException());

		org.assertj.core.api.Assertions.assertThatThrownBy(() ->
				paymentService.confirmPayment(7L, "confirm-key", request))
				.isInstanceOf(PaymentProviderBusyException.class);

		org.mockito.Mockito.verify(transactionService).failBeforeProvider(preparation);
	}

	private void assertTransactional(Method method) {
		Transactional annotation = method.getAnnotation(Transactional.class);
		assertThat(annotation).isNotNull();
		assertThat(annotation.propagation()).isEqualTo(Propagation.REQUIRED);
		assertThat(annotation.readOnly()).isFalse();
	}
}
