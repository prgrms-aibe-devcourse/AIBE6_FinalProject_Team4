package com.kiwobollae.api.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.payment.dto.request.PaymentRefundRequest;
import com.kiwobollae.api.payment.dto.response.PaymentRefundResponse;
import com.kiwobollae.api.payment.provider.PaymentProvider;
import com.kiwobollae.api.payment.provider.PaymentProviderBusyException;
import com.kiwobollae.api.payment.provider.PaymentRefundCommand;
import com.kiwobollae.api.payment.provider.PaymentRefundResult;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class PaymentRefundServiceTest {

	@Mock private PaymentProvider paymentProvider;
	@Mock private PaymentRefundTransactionService transactionService;

	private PaymentRefundService paymentRefundService;

	@BeforeEach
	void setUp() {
		paymentRefundService = new PaymentRefundService(paymentProvider, transactionService);
	}

	@Test
	void callsProviderBetweenPreparationAndCompletion() {
		PaymentRefundCommand command = new PaymentRefundCommand(
				"order-21",
				"payment-key-21",
				5_000L,
				"사용자 요청"
		);
		PaymentRefundPreparation preparation = preparation(command);
		PaymentRefundResult providerResult = PaymentRefundResult.success("refund-key-31");
		PaymentRefundResponse expectedResponse = mock(PaymentRefundResponse.class);
		given(transactionService.prepare(
				org.mockito.ArgumentMatchers.eq(7L),
				org.mockito.ArgumentMatchers.eq("refund-request-key"),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.eq(21L),
				org.mockito.ArgumentMatchers.eq("사용자 요청")
		)).willReturn(preparation);
		given(paymentProvider.refund(command)).willReturn(providerResult);
		given(transactionService.complete(preparation, providerResult)).willReturn(expectedResponse);

		PaymentRefundResponse response = paymentRefundService.refund(
				7L,
				"refund-request-key",
				21L,
				new PaymentRefundRequest("  사용자 요청  ")
		);

		assertThat(response).isSameAs(expectedResponse);
		InOrder order = inOrder(transactionService, paymentProvider);
		order.verify(transactionService).prepare(
				org.mockito.ArgumentMatchers.eq(7L),
				org.mockito.ArgumentMatchers.eq("refund-request-key"),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.eq(21L),
				org.mockito.ArgumentMatchers.eq("사용자 요청")
		);
		order.verify(paymentProvider).refund(command);
		order.verify(transactionService).complete(preparation, providerResult);
	}

	@Test
	void definitiveProviderDeclineRestoresPreparedRefundBeforeReturningError() {
		PaymentRefundCommand command = new PaymentRefundCommand(
				"order-21",
				"payment-key-21",
				5_000L,
				"사용자 요청"
		);
		PaymentRefundPreparation preparation = preparation(command);
		given(transactionService.prepare(
				org.mockito.ArgumentMatchers.eq(7L),
				org.mockito.ArgumentMatchers.eq("refund-request-key"),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.eq(21L),
				org.mockito.ArgumentMatchers.eq("사용자 요청")
		)).willReturn(preparation);
		given(paymentProvider.refund(command))
				.willReturn(PaymentRefundResult.failure("환불이 거절되었습니다."));

		assertThatThrownBy(() -> paymentRefundService.refund(
				7L,
				"refund-request-key",
				21L,
				new PaymentRefundRequest("사용자 요청")
		)).isInstanceOfSatisfying(BusinessException.class, exception -> {
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_DECLINED);
			assertThat(exception.getMessage()).isEqualTo("환불이 거절되었습니다.");
		});

		verify(transactionService).failDefinitively(preparation);
	}

	@Test
	void refundOrchestratorSuspendsTransactionsAroundProviderCall() throws Exception {
		Method refundMethod = PaymentRefundService.class.getMethod(
				"refund",
				Long.class,
				String.class,
				Long.class,
				PaymentRefundRequest.class
		);
		Transactional annotation = refundMethod.getAnnotation(Transactional.class);

		assertThat(annotation).isNotNull();
		assertThat(annotation.propagation()).isEqualTo(Propagation.NOT_SUPPORTED);
		assertTransactional(PaymentRefundTransactionService.class.getMethod(
				"prepare",
				Long.class,
				String.class,
				String.class,
				Long.class,
				String.class
		));
		assertTransactional(PaymentRefundTransactionService.class.getMethod(
				"complete",
				PaymentRefundPreparation.class,
				PaymentRefundResult.class
		));
		assertTransactional(PaymentRefundTransactionService.class.getMethod(
				"failDefinitively",
				PaymentRefundPreparation.class
		));
	}

	@Test
	void bulkheadRejectionRestoresPreparedRefundBeforeReturningUnavailable() {
		PaymentRefundCommand command = new PaymentRefundCommand(
				"order-21", "payment-key-21", 5_000L, "사용자 요청");
		PaymentRefundPreparation preparation = preparation(command);
		given(transactionService.prepare(
				org.mockito.ArgumentMatchers.eq(7L),
				org.mockito.ArgumentMatchers.eq("refund-request-key"),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.eq(21L),
				org.mockito.ArgumentMatchers.eq("사용자 요청")
		)).willReturn(preparation);
		given(paymentProvider.refund(command)).willThrow(new PaymentProviderBusyException());

		assertThatThrownBy(() -> paymentRefundService.refund(
				7L,
				"refund-request-key",
				21L,
				new PaymentRefundRequest("사용자 요청")
		)).isInstanceOf(PaymentProviderBusyException.class);

		verify(transactionService).failDefinitively(preparation);
	}

	private PaymentRefundPreparation preparation(PaymentRefundCommand command) {
		return PaymentRefundPreparation.pending(
				7L,
				"refund-request-key",
				"request-hash",
				21L,
				31L,
				41L,
				5_000L,
				command
		);
	}

	private void assertTransactional(Method method) {
		Transactional annotation = method.getAnnotation(Transactional.class);
		assertThat(annotation).isNotNull();
		assertThat(annotation.propagation()).isEqualTo(Propagation.REQUIRED);
		assertThat(annotation.readOnly()).isFalse();
	}
}
