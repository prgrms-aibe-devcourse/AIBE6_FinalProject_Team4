package com.kiwobollae.api.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.entity.enums.AuthProvider;
import com.kiwobollae.api.auth.entity.enums.UserRole;
import com.kiwobollae.api.auth.entity.enums.UserStatus;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.infra.repository.IdempotencyKeyRepository;
import com.kiwobollae.api.payment.dto.request.PaymentRefundRequest;
import com.kiwobollae.api.payment.dto.response.PaymentRefundResponse;
import com.kiwobollae.api.payment.entity.Payment;
import com.kiwobollae.api.payment.entity.enums.PaymentProviderType;
import com.kiwobollae.api.payment.entity.enums.PaymentRefundAttemptStatus;
import com.kiwobollae.api.payment.entity.enums.PaymentRefundStatus;
import com.kiwobollae.api.payment.entity.enums.PaymentStatus;
import com.kiwobollae.api.payment.provider.PaymentProvider;
import com.kiwobollae.api.payment.provider.PaymentRefundResult;
import com.kiwobollae.api.payment.repository.PaymentRefundAttemptRepository;
import com.kiwobollae.api.payment.repository.PaymentRefundRepository;
import com.kiwobollae.api.payment.repository.PaymentRepository;
import com.kiwobollae.api.point.entity.PointTransaction;
import com.kiwobollae.api.point.entity.Wallet;
import com.kiwobollae.api.point.entity.enums.CurrencyType;
import com.kiwobollae.api.point.entity.enums.PointRefType;
import com.kiwobollae.api.point.entity.enums.PointTxType;
import com.kiwobollae.api.point.repository.PointTransactionRepository;
import com.kiwobollae.api.point.repository.WalletRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ActiveProfiles("test")
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = {
				"spring.datasource.url=jdbc:mysql://localhost:3306/kiwobollae_point_test"
						+ "?createDatabaseIfNotExist=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8",
				"spring.jpa.hibernate.ddl-auto=create-drop"
		}
)
class PaymentRefundServiceMySqlIntegrationTest {

	private static final long WAIT_SECONDS = 10L;

	@Autowired
	private PaymentRefundService paymentRefundService;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private PaymentRefundRepository paymentRefundRepository;

	@Autowired
	private PaymentRefundAttemptRepository paymentRefundAttemptRepository;

	@Autowired
	private WalletRepository walletRepository;

	@Autowired
	private PointTransactionRepository pointTransactionRepository;

	@Autowired
	private IdempotencyKeyRepository idempotencyKeyRepository;

	@Autowired
	private UserRepository userRepository;

	@MockitoBean
	private PaymentProvider paymentProvider;

	private Long userId;
	private Long paymentId;

	@BeforeEach
	void setUp() {
		clearData();
		given(paymentProvider.getType()).willReturn(PaymentProviderType.TOSS);
		given(paymentProvider.refund(org.mockito.ArgumentMatchers.any())).willAnswer(invocation -> {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			return PaymentRefundResult.success("integration-refund-key");
		});

		User user = userRepository.saveAndFlush(User.builder()
				.email("payment-refund-integration@example.test")
				.password("encoded-password")
				.nickname("payment-refund-integration")
				.name("환불통합테스트")
				.provider(AuthProvider.LOCAL)
				.role(UserRole.USER)
				.level(1)
				.status(UserStatus.ACTIVE)
				.build());
		userId = user.getId();

		Payment payment = paymentRepository.saveAndFlush(Payment.builder()
				.user(user)
				.cashAmount(5_000L)
				.pointAmount(5_000L)
				.status(PaymentStatus.COMPLETED)
				.provider(PaymentProviderType.TOSS)
				.providerOrderId("refund-integration-order")
				.providerPaymentKey("refund-integration-payment")
				.approvedAt(LocalDateTime.now())
				.build());
		paymentId = payment.getId();

		walletRepository.saveAndFlush(Wallet.builder()
				.user(user)
				.paidPoint(5_000L)
				.freePoint(2_000L)
				.build());
	}

	@AfterEach
	void tearDown() {
		clearData();
	}

	private void clearData() {
		idempotencyKeyRepository.deleteAllInBatch();
		pointTransactionRepository.deleteAllInBatch();
		paymentRefundAttemptRepository.deleteAllInBatch();
		paymentRefundRepository.deleteAllInBatch();
		paymentRepository.deleteAllInBatch();
		walletRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
	}

	@Test
	void definitiveProviderDeclineRestoresReservedPointAndSettlesAttempt() {
		given(paymentProvider.refund(org.mockito.ArgumentMatchers.any()))
				.willReturn(PaymentRefundResult.failure("환불이 거절되었습니다."));

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> paymentRefundService.refund(
				userId,
				"refund-declined-key",
				paymentId,
				new PaymentRefundRequest("거절 복구 테스트")
		)).isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_DECLINED));

		assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
				.isEqualTo(PaymentStatus.COMPLETED);
		assertThat(walletRepository.findByUserId(userId).orElseThrow().getPaidPoint()).isEqualTo(5_000L);
		assertThat(paymentRefundRepository.findAll())
				.singleElement()
				.satisfies(refund -> assertThat(refund.getStatus()).isEqualTo(PaymentRefundStatus.FAILED));
		assertThat(paymentRefundAttemptRepository.findAll())
				.singleElement()
				.satisfies(attempt -> assertThat(attempt.getStatus())
						.isEqualTo(PaymentRefundAttemptStatus.SETTLED));
		assertThat(pointTransactionRepository.findAll())
				.extracting(PointTransaction::getType, PointTransaction::getAmount)
				.containsExactlyInAnyOrder(
						org.assertj.core.groups.Tuple.tuple(PointTxType.REFUND, -5_000L),
						org.assertj.core.groups.Tuple.tuple(PointTxType.RESTORE, 5_000L)
				);
	}

	void concurrentFullRefundsCompleteOnlyOnce() throws Exception {
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		List<AttemptResult> results = runConcurrently(
				refundAttempt("refund-concurrent-key-1", ready, start),
				refundAttempt("refund-concurrent-key-2", ready, start),
				ready,
				start
		);

		assertThat(results).containsExactlyInAnyOrder(
				AttemptResult.SUCCESS,
				AttemptResult.INVALID_STATE
		);

		Payment payment = paymentRepository.findById(paymentId).orElseThrow();
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);

		Wallet wallet = walletRepository.findByUserId(userId).orElseThrow();
		assertThat(wallet.getPaidPoint()).isZero();
		assertThat(wallet.getFreePoint()).isEqualTo(2_000L);

		assertThat(paymentRefundRepository.findAll())
				.singleElement()
				.satisfies(refund -> {
					assertThat(refund.getStatus()).isEqualTo(PaymentRefundStatus.COMPLETED);
					assertThat(refund.getCashAmount()).isEqualTo(5_000L);
					assertThat(refund.getPointAmount()).isEqualTo(5_000L);
				});

		assertThat(pointTransactionRepository.findAll())
				.singleElement()
				.satisfies(this::assertRefundTransaction);
	}

	@Test
	void concurrentSameIdempotencyKeyReturnsInProgressThenReplaysCompletedResponse() throws Exception {
		CountDownLatch providerStarted = new CountDownLatch(1);
		CountDownLatch releaseProvider = new CountDownLatch(1);
		given(paymentProvider.refund(org.mockito.ArgumentMatchers.any())).willAnswer(invocation -> {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			providerStarted.countDown();
			if (!releaseProvider.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Provider test release timed out");
			}
			return PaymentRefundResult.success("integration-refund-key");
		});

		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<PaymentRefundResponse> firstFuture = executor.submit(() -> paymentRefundService.refund(
					userId,
					"refund-same-key",
					paymentId,
					new PaymentRefundRequest("동시 환불 멱등성 테스트")
			));
			assertThat(providerStarted.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

			org.assertj.core.api.Assertions.assertThatThrownBy(() -> paymentRefundService.refund(
					userId,
					"refund-same-key",
					paymentId,
					new PaymentRefundRequest("동시 환불 멱등성 테스트")
			)).isInstanceOfSatisfying(BusinessException.class, exception ->
					assertThat(exception.getErrorCode())
							.isEqualTo(ErrorCode.COMMON_IDEMPOTENCY_IN_PROGRESS));

			releaseProvider.countDown();
			PaymentRefundResponse first = firstFuture.get(WAIT_SECONDS, TimeUnit.SECONDS);
			PaymentRefundResponse replay = paymentRefundService.refund(
					userId,
					"refund-same-key",
					paymentId,
					new PaymentRefundRequest("동시 환불 멱등성 테스트")
			);

			assertThat(replay.id()).isEqualTo(first.id());
			assertThat(replay.status()).isEqualTo(PaymentRefundStatus.COMPLETED);
		} finally {
			releaseProvider.countDown();
			executor.shutdownNow();
		}
		assertThat(idempotencyKeyRepository.count()).isEqualTo(1L);
		assertThat(paymentRefundRepository.count()).isEqualTo(1L);
		assertThat(pointTransactionRepository.count()).isEqualTo(1L);

		Wallet wallet = walletRepository.findByUserId(userId).orElseThrow();
		assertThat(wallet.getPaidPoint()).isZero();
		assertThat(wallet.getFreePoint()).isEqualTo(2_000L);
	}

	private Callable<AttemptResult> refundAttempt(
			String idempotencyKey,
			CountDownLatch ready,
			CountDownLatch start
	) {
		return () -> {
			awaitStart(ready, start);
			try {
				paymentRefundService.refund(
						userId,
						idempotencyKey,
						paymentId,
						new PaymentRefundRequest("동시 환불 테스트")
				);
				return AttemptResult.SUCCESS;
			} catch (BusinessException exception) {
				if (exception.getErrorCode() == ErrorCode.PAYMENT_INVALID_STATE) {
					return AttemptResult.INVALID_STATE;
				}
				throw exception;
			}
		};
	}

	private Callable<PaymentRefundResponse> refundResponseAttempt(
			String idempotencyKey,
			CountDownLatch ready,
			CountDownLatch start
	) {
		return () -> {
			awaitStart(ready, start);
			return paymentRefundService.refund(
					userId,
					idempotencyKey,
					paymentId,
					new PaymentRefundRequest("동시 환불 멱등성 테스트")
			);
		};
	}

	private <T> List<T> runConcurrently(
			Callable<T> first,
			Callable<T> second,
			CountDownLatch ready,
			CountDownLatch start
	) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<T> firstFuture = executor.submit(first);
			Future<T> secondFuture = executor.submit(second);
			assertThat(ready.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			return List.of(
					firstFuture.get(WAIT_SECONDS, TimeUnit.SECONDS),
					secondFuture.get(WAIT_SECONDS, TimeUnit.SECONDS)
			);
		} finally {
			start.countDown();
			executor.shutdownNow();
		}
	}

	private void awaitStart(CountDownLatch ready, CountDownLatch start)
			throws InterruptedException {
		ready.countDown();
		if (!start.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
			throw new IllegalStateException("Concurrent refund test start timed out");
		}
	}

	private void assertRefundTransaction(PointTransaction transaction) {
		assertThat(transaction.getType()).isEqualTo(PointTxType.REFUND);
		assertThat(transaction.getCurrencyType()).isEqualTo(CurrencyType.PAID);
		assertThat(transaction.getAmount()).isEqualTo(-5_000L);
		assertThat(transaction.getBalanceAfter()).isZero();
		assertThat(transaction.getRefType()).isEqualTo(PointRefType.PAYMENT_REFUND);
		assertThat(transaction.getRefId()).isNotNull();
	}

	private enum AttemptResult {
		SUCCESS,
		INVALID_STATE
	}
}
