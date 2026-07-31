package com.kiwobollae.api.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.infra.entity.IdempotencyKey;
import com.kiwobollae.api.infra.service.IdempotencyExecution;
import com.kiwobollae.api.infra.service.IdempotencyService;
import com.kiwobollae.api.payment.dto.request.PaymentRefundRequest;
import com.kiwobollae.api.payment.dto.response.PaymentRefundResponse;
import com.kiwobollae.api.payment.entity.Payment;
import com.kiwobollae.api.payment.entity.PaymentRefund;
import com.kiwobollae.api.payment.entity.enums.PaymentRefundStatus;
import com.kiwobollae.api.payment.entity.enums.PaymentStatus;
import com.kiwobollae.api.payment.provider.PaymentProvider;
import com.kiwobollae.api.payment.provider.PaymentRefundCommand;
import com.kiwobollae.api.payment.provider.PaymentRefundResult;
import com.kiwobollae.api.payment.repository.PaymentRefundRepository;
import com.kiwobollae.api.payment.repository.PaymentRepository;
import com.kiwobollae.api.point.service.WalletService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class PaymentRefundServiceTest {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final Clock FIXED_KST_CLOCK =
			Clock.fixed(Instant.parse("2026-07-31T01:00:00Z"), KST);
	private static final LocalDateTime FIXED_KST_TIME =
			LocalDateTime.of(2026, 7, 31, 10, 0);

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private PaymentRefundRepository paymentRefundRepository;

	@Mock
	private WalletService walletService;

	@Mock
	private PaymentProvider paymentProvider;

	@Mock
	private IdempotencyService idempotencyService;

	@Mock
	private ObjectMapper objectMapper;

	private PaymentRefundService paymentRefundService;

	@BeforeEach
	void setUp() {
		paymentRefundService = new PaymentRefundService(
				paymentRepository,
				paymentRefundRepository,
				walletService,
				paymentProvider,
				idempotencyService,
				objectMapper,
				FIXED_KST_CLOCK
		);
	}

	@Test
	void fullRefundDeductsPaidPointAndCompletesPaymentAndRefund() throws Exception {
		Payment payment = payment(PaymentStatus.PAID);
		PaymentRefund requestedRefund = org.mockito.Mockito.mock(PaymentRefund.class);
		PaymentRefund completedRefund = completedRefund(payment);
		IdempotencyKey key = org.mockito.Mockito.mock(IdempotencyKey.class);

		given(idempotencyService.start(
				org.mockito.ArgumentMatchers.eq(7L),
				org.mockito.ArgumentMatchers.eq("PAYMENT_REFUND"),
				org.mockito.ArgumentMatchers.eq("refund-key"),
				org.mockito.ArgumentMatchers.anyString()
		)).willReturn(new IdempotencyExecution(key, false));
		given(paymentRepository.findDetailsByIdAndUserIdForUpdate(21L, 7L))
				.willReturn(Optional.of(payment));
		given(paymentRefundRepository.saveAndFlush(org.mockito.ArgumentMatchers.any()))
				.willReturn(requestedRefund);
		given(requestedRefund.getId()).willReturn(31L);
		given(paymentProvider.refund(org.mockito.ArgumentMatchers.any()))
				.willReturn(PaymentRefundResult.success("provider-refund-key"));
		given(paymentRepository.updateStatusOnlyIfCurrent(
				21L,
				PaymentStatus.PAID,
				PaymentStatus.REFUNDED
		)).willReturn(1);
		given(paymentRefundRepository.completeIfCurrent(
				org.mockito.ArgumentMatchers.eq(31L),
				org.mockito.ArgumentMatchers.eq(PaymentRefundStatus.REQUESTED),
				org.mockito.ArgumentMatchers.eq(PaymentRefundStatus.COMPLETED),
				org.mockito.ArgumentMatchers.eq("provider-refund-key"),
				org.mockito.ArgumentMatchers.eq(FIXED_KST_TIME)
		)).willReturn(1);
		given(paymentRefundRepository.findDetailsById(31L))
				.willReturn(Optional.of(completedRefund));
		given(objectMapper.writeValueAsString(org.mockito.ArgumentMatchers.any()))
				.willReturn("{\"id\":31}");

		PaymentRefundResponse response = paymentRefundService.refund(
				7L,
				"refund-key",
				21L,
				new PaymentRefundRequest("  사용자 요청  ")
		);

		assertThat(response.id()).isEqualTo(31L);
		assertThat(response.paymentId()).isEqualTo(21L);
		assertThat(response.cashAmount()).isEqualTo(5_000L);
		assertThat(response.pointAmount()).isEqualTo(5_000L);
		assertThat(response.status()).isEqualTo(PaymentRefundStatus.COMPLETED);

		ArgumentCaptor<PaymentRefund> refundCaptor = ArgumentCaptor.forClass(PaymentRefund.class);
		verify(paymentRefundRepository).saveAndFlush(refundCaptor.capture());
		assertThat(refundCaptor.getValue().getPayment()).isEqualTo(payment);
		assertThat(refundCaptor.getValue().getCashAmount()).isEqualTo(5_000L);
		assertThat(refundCaptor.getValue().getPointAmount()).isEqualTo(5_000L);
		assertThat(refundCaptor.getValue().getStatus()).isEqualTo(PaymentRefundStatus.REQUESTED);
		assertThat(refundCaptor.getValue().getReason()).isEqualTo("사용자 요청");

		verify(walletService).deductPaidPointForPaymentRefund(7L, 5_000L, 31L);
		ArgumentCaptor<PaymentRefundCommand> commandCaptor =
				ArgumentCaptor.forClass(PaymentRefundCommand.class);
		verify(paymentProvider).refund(commandCaptor.capture());
		assertThat(commandCaptor.getValue().providerOrderId()).isEqualTo("provider-order-21");
		assertThat(commandCaptor.getValue().paymentKey()).isEqualTo("provider-payment-21");
		assertThat(commandCaptor.getValue().cashAmount()).isEqualTo(5_000L);
		assertThat(commandCaptor.getValue().reason()).isEqualTo("사용자 요청");
		verify(idempotencyService).succeed(
				key,
				200,
				"{\"id\":31}",
				"PAYMENT_REFUND",
				31L
		);
	}

	@Test
	void successfulIdempotentRefundReplaysStoredResponse() throws Exception {
		Payment payment = payment(PaymentStatus.REFUNDED);
		IdempotencyKey key = org.mockito.Mockito.mock(IdempotencyKey.class);
		PaymentRefundResponse stored = new PaymentRefundResponse(
				31L,
				21L,
				5_000L,
				5_000L,
				PaymentRefundStatus.COMPLETED,
				"사용자 요청",
				"provider-refund-key",
				LocalDateTime.of(2026, 7, 31, 10, 0),
				LocalDateTime.of(2026, 7, 31, 10, 0)
		);
		given(paymentRepository.findDetailsByIdAndUserIdForUpdate(21L, 7L))
				.willReturn(Optional.of(payment));
		given(idempotencyService.start(
				org.mockito.ArgumentMatchers.eq(7L),
				org.mockito.ArgumentMatchers.eq("PAYMENT_REFUND"),
				org.mockito.ArgumentMatchers.eq("refund-key"),
				org.mockito.ArgumentMatchers.anyString()
		)).willReturn(new IdempotencyExecution(key, true));
		given(key.getResponseSnapshot()).willReturn("{\"id\":31}");
		given(objectMapper.readValue("{\"id\":31}", PaymentRefundResponse.class))
				.willReturn(stored);

		PaymentRefundResponse response = paymentRefundService.refund(
				7L,
				"refund-key",
				21L,
				new PaymentRefundRequest("사용자 요청")
		);

		assertThat(response).isEqualTo(stored);
		verify(paymentRepository).findDetailsByIdAndUserIdForUpdate(21L, 7L);
		verify(paymentProvider, never()).refund(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void refundRejectsPaymentOwnedByAnotherUserWithoutRevealingIt() {
		given(paymentRepository.findDetailsByIdAndUserIdForUpdate(21L, 7L))
				.willReturn(Optional.empty());

		assertThatThrownBy(() -> paymentRefundService.refund(
				7L,
				"refund-key",
				21L,
				new PaymentRefundRequest("사용자 요청")
		)).isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_NOT_FOUND));

		verify(paymentRefundRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
		verify(walletService, never()).deductPaidPointForPaymentRefund(
				org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.anyLong()
		);
		verify(idempotencyService, never()).start(
				org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.anyString()
		);
	}

	@Test
	void refundRejectsPaymentThatIsNotPaid() {
		Payment payment = payment(PaymentStatus.REFUNDED);
		given(idempotencyService.start(
				org.mockito.ArgumentMatchers.eq(7L),
				org.mockito.ArgumentMatchers.eq("PAYMENT_REFUND"),
				org.mockito.ArgumentMatchers.eq("refund-key"),
				org.mockito.ArgumentMatchers.anyString()
		)).willReturn(new IdempotencyExecution(
				org.mockito.Mockito.mock(IdempotencyKey.class),
				false
		));
		given(paymentRepository.findDetailsByIdAndUserIdForUpdate(21L, 7L))
				.willReturn(Optional.of(payment));

		assertThatThrownBy(() -> paymentRefundService.refund(
				7L,
				"refund-key",
				21L,
				new PaymentRefundRequest("사용자 요청")
		)).isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_INVALID_STATE));

		verify(paymentRefundRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
		verify(paymentProvider, never()).refund(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void refundRejectsInsufficientPaidPointBeforeCallingProvider() {
		Payment payment = payment(PaymentStatus.PAID);
		PaymentRefund requestedRefund = org.mockito.Mockito.mock(PaymentRefund.class);
		given(idempotencyService.start(
				org.mockito.ArgumentMatchers.eq(7L),
				org.mockito.ArgumentMatchers.eq("PAYMENT_REFUND"),
				org.mockito.ArgumentMatchers.eq("refund-key"),
				org.mockito.ArgumentMatchers.anyString()
		)).willReturn(new IdempotencyExecution(
				org.mockito.Mockito.mock(IdempotencyKey.class),
				false
		));
		given(paymentRepository.findDetailsByIdAndUserIdForUpdate(21L, 7L))
				.willReturn(Optional.of(payment));
		given(paymentRefundRepository.saveAndFlush(org.mockito.ArgumentMatchers.any()))
				.willReturn(requestedRefund);
		given(requestedRefund.getId()).willReturn(31L);
		org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.POINT_INSUFFICIENT_BALANCE))
				.when(walletService)
				.deductPaidPointForPaymentRefund(7L, 5_000L, 31L);

		assertThatThrownBy(() -> paymentRefundService.refund(
				7L,
				"refund-key",
				21L,
				new PaymentRefundRequest("사용자 요청")
		)).isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode())
						.isEqualTo(ErrorCode.POINT_INSUFFICIENT_BALANCE));

		verify(paymentProvider, never()).refund(org.mockito.ArgumentMatchers.any());
		verify(paymentRepository, never()).updateStatusOnlyIfCurrent(
				org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any()
		);
	}

	@Test
	void refundRollsBackWhenProviderDeclines() {
		Payment payment = payment(PaymentStatus.PAID);
		PaymentRefund requestedRefund = org.mockito.Mockito.mock(PaymentRefund.class);
		given(idempotencyService.start(
				org.mockito.ArgumentMatchers.eq(7L),
				org.mockito.ArgumentMatchers.eq("PAYMENT_REFUND"),
				org.mockito.ArgumentMatchers.eq("refund-key"),
				org.mockito.ArgumentMatchers.anyString()
		)).willReturn(new IdempotencyExecution(
				org.mockito.Mockito.mock(IdempotencyKey.class),
				false
		));
		given(paymentRepository.findDetailsByIdAndUserIdForUpdate(21L, 7L))
				.willReturn(Optional.of(payment));
		given(paymentRefundRepository.saveAndFlush(org.mockito.ArgumentMatchers.any()))
				.willReturn(requestedRefund);
		given(requestedRefund.getId()).willReturn(31L);
		given(paymentProvider.refund(org.mockito.ArgumentMatchers.any()))
				.willReturn(PaymentRefundResult.failure("환불이 거절되었습니다."));

		assertThatThrownBy(() -> paymentRefundService.refund(
				7L,
				"refund-key",
				21L,
				new PaymentRefundRequest("사용자 요청")
		)).isInstanceOfSatisfying(BusinessException.class, exception -> {
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_DECLINED);
			assertThat(exception.getMessage()).isEqualTo("환불이 거절되었습니다.");
		});

		verify(paymentRepository, never()).updateStatusOnlyIfCurrent(
				org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any()
		);
		verify(paymentRefundRepository, never()).completeIfCurrent(
				org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.any()
		);
	}

	private Payment payment(PaymentStatus status) {
		Payment payment = org.mockito.Mockito.mock(Payment.class);
		org.mockito.Mockito.lenient().when(payment.getId()).thenReturn(21L);
		org.mockito.Mockito.lenient().when(payment.getStatus()).thenReturn(status);
		if (status == PaymentStatus.PAID) {
			org.mockito.Mockito.lenient().when(payment.getCashAmount()).thenReturn(5_000L);
			org.mockito.Mockito.lenient().when(payment.getPointAmount()).thenReturn(5_000L);
			org.mockito.Mockito.lenient().when(payment.getProviderOrderId())
					.thenReturn("provider-order-21");
			org.mockito.Mockito.lenient().when(payment.getProviderPaymentKey())
					.thenReturn("provider-payment-21");
		}
		return payment;
	}

	private PaymentRefund completedRefund(Payment payment) {
		PaymentRefund refund = org.mockito.Mockito.mock(PaymentRefund.class);
		given(refund.getId()).willReturn(31L);
		given(refund.getPayment()).willReturn(payment);
		given(refund.getCashAmount()).willReturn(5_000L);
		given(refund.getPointAmount()).willReturn(5_000L);
		given(refund.getStatus()).willReturn(PaymentRefundStatus.COMPLETED);
		given(refund.getReason()).willReturn("사용자 요청");
		given(refund.getRefundKey()).willReturn("provider-refund-key");
		given(refund.getCreatedAt()).willReturn(LocalDateTime.of(2026, 7, 31, 10, 0));
		given(refund.getCompletedAt()).willReturn(LocalDateTime.of(2026, 7, 31, 10, 0));
		return refund;
	}
}
