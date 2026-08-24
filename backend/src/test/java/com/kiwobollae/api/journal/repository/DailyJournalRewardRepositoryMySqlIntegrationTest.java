package com.kiwobollae.api.journal.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.entity.enums.AuthProvider;
import com.kiwobollae.api.auth.entity.enums.UserRole;
import com.kiwobollae.api.auth.entity.enums.UserStatus;
import com.kiwobollae.api.auth.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:mysql://localhost:3306/kiwobollae_daily_journal_reward_test"
				+ "?createDatabaseIfNotExist=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
class DailyJournalRewardRepositoryMySqlIntegrationTest {

	private static final long WAIT_SECONDS = 10L;
	private static final String TEST_EMAIL = "daily-journal-reward@example.test";

	@Autowired private DailyJournalRewardRepository dailyJournalRewardRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private PlatformTransactionManager transactionManager;

	private Long userId;

	@BeforeEach
	void setUp() {
		dailyJournalRewardRepository.deleteAllInBatch();
		userRepository.findByEmail(TEST_EMAIL).ifPresent(userRepository::delete);
		userId = userRepository.saveAndFlush(User.builder()
				.email(TEST_EMAIL)
				.password("encoded-password")
				.nickname("일일보상")
				.name("일일보상")
				.provider(AuthProvider.LOCAL)
				.role(UserRole.USER)
				.status(UserStatus.ACTIVE)
				.build()).getId();
	}

	@AfterEach
	void tearDown() {
		dailyJournalRewardRepository.deleteAllInBatch();
		userRepository.findByEmail(TEST_EMAIL).ifPresent(userRepository::delete);
	}

	@Test
	void concurrentClaimsForDifferentJournalsGrantOnlyOneAccountDailyReward() throws Exception {
		LocalDate rewardDate = LocalDate.of(2026, 8, 13);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Callable<Void> first = claimAttempt(101L, rewardDate, ready, start);
			Callable<Void> second = claimAttempt(202L, rewardDate, ready, start);
			Future<Void> firstResult = executor.submit(first);
			Future<Void> secondResult = executor.submit(second);
			assertThat(ready.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			firstResult.get(WAIT_SECONDS, TimeUnit.SECONDS);
			secondResult.get(WAIT_SECONDS, TimeUnit.SECONDS);
		} finally {
			start.countDown();
			executor.shutdownNow();
		}

		assertThat(dailyJournalRewardRepository.findAll()).singleElement().satisfies(reward -> {
			assertThat(reward.getUser().getId()).isEqualTo(userId);
			assertThat(reward.getRewardDate()).isEqualTo(rewardDate);
			assertThat(reward.getJournalId()).isIn(101L, 202L);
			assertThat(reward.getRewardAmount()).isEqualTo(100L);
		});
	}

	private Callable<Void> claimAttempt(
			Long journalId,
			LocalDate rewardDate,
			CountDownLatch ready,
			CountDownLatch start
	) {
		return () -> {
			ready.countDown();
			if (!start.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Concurrent daily reward claim timed out");
			}
			new TransactionTemplate(transactionManager).executeWithoutResult(status ->
					dailyJournalRewardRepository.claim(
							userId,
							rewardDate,
							journalId,
							100L,
							LocalDateTime.of(2026, 8, 13, 10, 0)
					)
			);
			return null;
		};
	}
}
