package com.kiwobollae.api.point.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.entity.enums.AuthProvider;
import com.kiwobollae.api.auth.entity.enums.UserRole;
import com.kiwobollae.api.auth.entity.enums.UserStatus;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.point.entity.PointTransaction;
import com.kiwobollae.api.point.entity.Wallet;
import com.kiwobollae.api.point.dto.request.AdminPointAdjustmentDirection;
import com.kiwobollae.api.point.dto.response.AdminPointAdjustmentHistoryResponse;
import com.kiwobollae.api.point.dto.response.PointActivityResponse;
import com.kiwobollae.api.point.entity.enums.CurrencyType;
import com.kiwobollae.api.point.entity.enums.PointRefType;
import com.kiwobollae.api.point.entity.enums.PointTxType;
import com.kiwobollae.api.point.repository.PointTransactionRepository;
import com.kiwobollae.api.point.repository.WalletRepository;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@ActiveProfiles("test")
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = {
				"spring.datasource.url=jdbc:mysql://localhost:3306/kiwobollae_point_test"
						+ "?createDatabaseIfNotExist=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8",
				"spring.jpa.hibernate.ddl-auto=create-drop"
		}
)
class WalletServiceMySqlIntegrationTest {

	private static final long WAIT_SECONDS = 10L;

	@Autowired
	private WalletService walletService;

	@Autowired
	private AdminPointAdjustmentHistoryService adminPointAdjustmentHistoryService;

	@Autowired
	private PointTransactionService pointTransactionService;

	@Autowired
	private WalletRepository walletRepository;

	@Autowired
	private PointTransactionRepository pointTransactionRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	private Long userId;

	@BeforeEach
	void setUp() {
		pointTransactionRepository.deleteAllInBatch();
		walletRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
		userId = createUserWithWallet(500L, 1_000L);
	}

	@Test
	void concurrentOrderDeductionsDoNotOverspendPaidPoint() throws Exception {
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		List<AttemptResult> results = runConcurrently(
				orderDeductionAttempt(ready, start, 101L),
				orderDeductionAttempt(ready, start, 102L),
				ready,
				start
		);

		assertThat(results).containsExactlyInAnyOrder(
				AttemptResult.SUCCESS,
				AttemptResult.INSUFFICIENT_BALANCE
		);

		Wallet wallet = walletRepository.findByUserId(userId).orElseThrow();
		assertThat(wallet.getFreePoint()).isEqualTo(500L);
		assertThat(wallet.getPaidPoint()).isEqualTo(200L);

		List<PointTransaction> transactions = pointTransactionRepository.findAll();
		assertThat(transactions).hasSize(1);
		assertThat(transactions.getFirst().getType()).isEqualTo(PointTxType.PURCHASE);
		assertThat(transactions.getFirst().getAmount()).isEqualTo(-800L);
		assertThat(transactions.getFirst().getRefType()).isEqualTo(PointRefType.ORDER);
	}

	@Test
	void concurrentCardDeductionsDoNotOverspendCombinedPoint() throws Exception {
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		List<AttemptResult> results = runConcurrently(
				cardDeductionAttempt(ready, start, 111L),
				cardDeductionAttempt(ready, start, 112L),
				ready,
				start
		);

		assertThat(results).containsExactlyInAnyOrder(
				AttemptResult.SUCCESS,
				AttemptResult.INSUFFICIENT_BALANCE
		);

		Wallet wallet = walletRepository.findByUserId(userId).orElseThrow();
		assertThat(wallet.getFreePoint()).isZero();
		assertThat(wallet.getPaidPoint()).isEqualTo(700L);

		List<PointTransaction> transactions = pointTransactionRepository.findAll();
		assertThat(transactions).hasSize(2);
		assertThat(transactions)
				.extracting(PointTransaction::getType)
				.containsOnly(PointTxType.PURCHASE);
		assertThat(transactions)
				.extracting(PointTransaction::getRefType)
				.containsOnly(PointRefType.CARD_PURCHASE);
		assertThat(transactions)
				.extracting(PointTransaction::getAmount)
				.containsExactlyInAnyOrder(-500L, -300L);
	}

	@Test
	void concurrentJournalRewardsAreAppliedOnlyOnce() throws Exception {
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		List<AttemptResult> results = runConcurrently(
				journalRewardAttempt(ready, start, 121L),
				journalRewardAttempt(ready, start, 121L),
				ready,
				start
		);

		assertThat(results).containsExactlyInAnyOrder(
				AttemptResult.SUCCESS,
				AttemptResult.DUPLICATE_TRANSACTION
		);

		Wallet wallet = walletRepository.findByUserId(userId).orElseThrow();
		assertThat(wallet.getFreePoint()).isEqualTo(600L);
		assertThat(wallet.getPaidPoint()).isEqualTo(1_000L);

		List<PointTransaction> transactions = pointTransactionRepository.findAll();
		assertThat(transactions).hasSize(1);
		assertThat(transactions.getFirst().getType()).isEqualTo(PointTxType.JOURNAL_REWARD);
		assertThat(transactions.getFirst().getRefType()).isEqualTo(PointRefType.JOURNAL_COMPLETION);
		assertThat(transactions.getFirst().getRefId()).isEqualTo(121L);
		assertThat(transactions.getFirst().getAmount()).isEqualTo(100L);
	}

	@Test
	void outerOrderTransactionRollbackRestoresWalletAndRemovesLedger() {
		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
			walletService.deductForOrderPurchase(userId, 600L, 200L, 201L);
			throw new ForcedOrderFailure();
		})).isInstanceOf(ForcedOrderFailure.class);

		Wallet wallet = walletRepository.findByUserId(userId).orElseThrow();
		assertThat(wallet.getFreePoint()).isEqualTo(500L);
		assertThat(wallet.getPaidPoint()).isEqualTo(1_000L);
		assertThat(pointTransactionRepository.findAll()).isEmpty();
	}

	@Test
	void concurrentDuplicateRestoreIsAppliedOnlyOnce() throws Exception {
		walletService.deductForOrderPurchase(userId, 500L, 300L, 301L);

		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		List<AttemptResult> results = runConcurrently(
				restoreAttempt(ready, start, 301L),
				restoreAttempt(ready, start, 301L),
				ready,
				start
		);

		assertThat(results).containsExactlyInAnyOrder(
				AttemptResult.SUCCESS,
				AttemptResult.DUPLICATE_TRANSACTION
		);

		Wallet wallet = walletRepository.findByUserId(userId).orElseThrow();
		assertThat(wallet.getFreePoint()).isEqualTo(500L);
		assertThat(wallet.getPaidPoint()).isEqualTo(1_000L);

		List<PointTransaction> transactions = pointTransactionRepository.findAll();
		assertThat(transactions).hasSize(4);
		assertThat(transactions)
				.filteredOn(transaction -> transaction.getType() == PointTxType.RESTORE)
				.extracting(PointTransaction::getType)
				.containsOnly(PointTxType.RESTORE);
		assertThat(transactions)
				.filteredOn(transaction -> transaction.getType() == PointTxType.RESTORE)
				.extracting(PointTransaction::getAmount)
				.containsExactlyInAnyOrder(300L, 200L);
	}

	@Test
	void concurrentAdminDeductionsDoNotMakePaidPointNegative() throws Exception {
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		List<AttemptResult> results = runConcurrently(
				adminAdjustmentAttempt(ready, start),
				adminAdjustmentAttempt(ready, start),
				ready,
				start
		);

		assertThat(results).containsExactlyInAnyOrder(
				AttemptResult.SUCCESS,
				AttemptResult.INSUFFICIENT_BALANCE
		);

		Wallet wallet = walletRepository.findByUserId(userId).orElseThrow();
		assertThat(wallet.getFreePoint()).isEqualTo(500L);
		assertThat(wallet.getPaidPoint()).isEqualTo(200L);

		List<PointTransaction> transactions = pointTransactionRepository.findAll();
		assertThat(transactions).hasSize(1);
		assertThat(transactions.getFirst().getType()).isEqualTo(PointTxType.ADMIN_ADJUST);
		assertThat(transactions.getFirst().getCurrencyType()).isEqualTo(CurrencyType.PAID);
		assertThat(transactions.getFirst().getAmount()).isEqualTo(-800L);
		assertThat(transactions.getFirst().getBalanceAfter()).isEqualTo(200L);
		assertThat(transactions.getFirst().getRefType()).isEqualTo(PointRefType.ADMIN);
		assertThat(transactions.getFirst().getRefId()).isEqualTo(99L);
	}

	@Test
	void adminAdjustmentHistoryReturnsTargetAndActorWithFilters() {
		walletService.adjustByAdmin(99L, userId, CurrencyType.FREE, 100L);
		walletService.adjustByAdmin(99L, userId, CurrencyType.FREE, -50L);

		Page<AdminPointAdjustmentHistoryResponse> history =
				adminPointAdjustmentHistoryService.getAdjustments(
						userId,
						CurrencyType.FREE,
						null,
						null,
						null,
						PageRequest.of(0, 20)
				);

		assertThat(history.getTotalElements()).isEqualTo(2);
		assertThat(history.getContent())
				.extracting(AdminPointAdjustmentHistoryResponse::amount)
				.containsExactly(-50L, 100L);
		assertThat(history.getContent().getFirst().targetUserId()).isEqualTo(userId);
		assertThat(history.getContent().getFirst().targetEmail())
				.isEqualTo("wallet-integration@example.test");
		assertThat(history.getContent().getFirst().targetNickname())
				.isEqualTo("wallet-integration");
		assertThat(history.getContent().getFirst().balanceAfter()).isEqualTo(550L);
		assertThat(history.getContent().getFirst().adminUserId()).isEqualTo(99L);

		Page<AdminPointAdjustmentHistoryResponse> grants =
				adminPointAdjustmentHistoryService.getAdjustments(
						userId,
						CurrencyType.FREE,
						AdminPointAdjustmentDirection.GRANT,
						null,
						null,
						PageRequest.of(0, 20)
				);

		assertThat(grants.getTotalElements()).isEqualTo(1);
		assertThat(grants.getContent().getFirst().amount()).isEqualTo(100L);
	}

	@Test
	void pointActivitiesGroupMixedCurrencyLedgersAndFilterBySource() {
		walletService.deductForOrderPurchase(userId, 800L, 200L, 501L);

		Page<PointActivityResponse> orderActivities = pointTransactionService.getActivities(
				userId,
				null,
				PointRefType.ORDER,
				null,
				null,
				PageRequest.of(0, 20)
		);

		assertThat(orderActivities.getTotalElements()).isEqualTo(1);
		PointActivityResponse purchase = orderActivities.getContent().getFirst();
		assertThat(purchase.type()).isEqualTo(PointTxType.PURCHASE);
		assertThat(purchase.refType()).isEqualTo(PointRefType.ORDER);
		assertThat(purchase.refId()).isEqualTo(501L);
		assertThat(purchase.amount()).isEqualTo(-800L);
		assertThat(purchase.paidAmount()).isEqualTo(-600L);
		assertThat(purchase.freeAmount()).isEqualTo(-200L);
		assertThat(purchase.paidBalanceAfter()).isEqualTo(400L);
		assertThat(purchase.freeBalanceAfter()).isEqualTo(300L);

		walletService.restorePurchasePoints(
				userId,
				200L,
				600L,
				PointRefType.ORDER,
				501L
		);

		Page<PointActivityResponse> allOrderActivities = pointTransactionService.getActivities(
				userId,
				null,
				PointRefType.ORDER,
				null,
				null,
				PageRequest.of(0, 1)
		);
		assertThat(allOrderActivities.getTotalElements()).isEqualTo(2);
		assertThat(allOrderActivities.getTotalPages()).isEqualTo(2);
		assertThat(allOrderActivities.getContent())
				.extracting(PointActivityResponse::type)
				.containsExactly(PointTxType.RESTORE);

		Page<PointActivityResponse> secondPage = pointTransactionService.getActivities(
				userId,
				null,
				PointRefType.ORDER,
				null,
				null,
				PageRequest.of(1, 1)
		);
		assertThat(secondPage.getContent())
				.extracting(PointActivityResponse::type)
				.containsExactly(PointTxType.PURCHASE);
	}

	@Test
	void pointActivitiesKeepAdminAdjustmentsAsSeparateEvents() {
		walletService.adjustByAdmin(99L, userId, CurrencyType.FREE, 100L);
		walletService.adjustByAdmin(99L, userId, CurrencyType.FREE, 200L);

		Page<PointActivityResponse> activities = pointTransactionService.getActivities(
				userId,
				PointTxType.ADMIN_ADJUST,
				null,
				null,
				null,
				PageRequest.of(0, 20)
		);

		assertThat(activities.getTotalElements()).isEqualTo(2);
		assertThat(activities.getContent())
				.extracting(PointActivityResponse::amount)
				.containsExactly(200L, 100L);
	}

	private Callable<AttemptResult> orderDeductionAttempt(
			CountDownLatch ready,
			CountDownLatch start,
			long orderId
	) {
		return () -> {
			awaitStart(ready, start);
			try {
				walletService.deductForOrderPurchase(userId, 800L, 0L, orderId);
				return AttemptResult.SUCCESS;
			} catch (BusinessException exception) {
				if (exception.getErrorCode() == ErrorCode.POINT_INSUFFICIENT_BALANCE) {
					return AttemptResult.INSUFFICIENT_BALANCE;
				}
				throw exception;
			}
		};
	}

	private Callable<AttemptResult> cardDeductionAttempt(
			CountDownLatch ready,
			CountDownLatch start,
			long cardPurchaseId
	) {
		return () -> {
			awaitStart(ready, start);
			try {
				walletService.deductForCardPurchase(userId, 800L, cardPurchaseId);
				return AttemptResult.SUCCESS;
			} catch (BusinessException exception) {
				if (exception.getErrorCode() == ErrorCode.POINT_INSUFFICIENT_BALANCE) {
					return AttemptResult.INSUFFICIENT_BALANCE;
				}
				throw exception;
			}
		};
	}

	private Callable<AttemptResult> restoreAttempt(
			CountDownLatch ready,
			CountDownLatch start,
			long orderId
	) {
		return () -> {
			awaitStart(ready, start);
			try {
				walletService.restorePurchasePoints(
						userId,
						300L,
						200L,
						PointRefType.ORDER,
						orderId
				);
				return AttemptResult.SUCCESS;
			} catch (BusinessException exception) {
				if (exception.getErrorCode() == ErrorCode.POINT_DUPLICATE_TRANSACTION) {
					return AttemptResult.DUPLICATE_TRANSACTION;
				}
				throw exception;
			}
		};
	}

	private Callable<AttemptResult> journalRewardAttempt(
			CountDownLatch ready,
			CountDownLatch start,
			long journalId
	) {
		return () -> {
			awaitStart(ready, start);
			try {
				walletService.rewardJournal(userId, journalId);
				return AttemptResult.SUCCESS;
			} catch (BusinessException exception) {
				if (exception.getErrorCode() == ErrorCode.POINT_DUPLICATE_TRANSACTION) {
					return AttemptResult.DUPLICATE_TRANSACTION;
				}
				throw exception;
			}
		};
	}

	private Callable<AttemptResult> adminAdjustmentAttempt(
			CountDownLatch ready,
			CountDownLatch start
	) {
		return () -> {
			awaitStart(ready, start);
			try {
				walletService.adjustByAdmin(99L, userId, CurrencyType.PAID, -800L);
				return AttemptResult.SUCCESS;
			} catch (BusinessException exception) {
				if (exception.getErrorCode() == ErrorCode.POINT_INSUFFICIENT_BALANCE) {
					return AttemptResult.INSUFFICIENT_BALANCE;
				}
				throw exception;
			}
		};
	}

	private List<AttemptResult> runConcurrently(
			Callable<AttemptResult> first,
			Callable<AttemptResult> second,
			CountDownLatch ready,
			CountDownLatch start
	) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<AttemptResult> firstFuture = executor.submit(first);
			Future<AttemptResult> secondFuture = executor.submit(second);
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
			throw new IllegalStateException("Concurrent test start timed out");
		}
	}

	private Long createUserWithWallet(long freePoint, long paidPoint) {
		User user = userRepository.saveAndFlush(User.builder()
				.email("wallet-integration@example.test")
				.password("encoded-password")
				.nickname("wallet-integration")
				.name("통합테스트")
				.provider(AuthProvider.LOCAL)
				.role(UserRole.USER)
				.level(1)
				.status(UserStatus.ACTIVE)
				.build());

		walletRepository.saveAndFlush(Wallet.builder()
				.user(user)
				.freePoint(freePoint)
				.paidPoint(paidPoint)
				.build());
		return user.getId();
	}

	private enum AttemptResult {
		SUCCESS,
		INSUFFICIENT_BALANCE,
		DUPLICATE_TRANSACTION
	}

	private static final class ForcedOrderFailure extends RuntimeException {
	}
}
