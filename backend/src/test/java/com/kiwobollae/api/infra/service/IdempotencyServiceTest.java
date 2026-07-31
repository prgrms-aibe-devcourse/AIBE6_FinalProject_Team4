package com.kiwobollae.api.infra.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.infra.entity.IdempotencyKey;
import com.kiwobollae.api.infra.repository.IdempotencyKeyRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final Clock FIXED_KST_CLOCK =
			Clock.fixed(Instant.parse("2026-07-31T01:00:00Z"), KST);

	@Mock
	private IdempotencyKeyRepository idempotencyKeyRepository;

	@Mock
	private UserRepository userRepository;

	private IdempotencyService idempotencyService;

	@BeforeEach
	void setUp() {
		idempotencyService = new IdempotencyService(
				idempotencyKeyRepository,
				userRepository,
				FIXED_KST_CLOCK
		);
	}

	@Test
	void paymentRefundKeyAndResponseAreRetainedForSevenDays() {
		given(idempotencyKeyRepository.findForUpdate(7L, "PAYMENT_REFUND", "refund-key"))
				.willReturn(Optional.empty());
		given(userRepository.getReferenceById(7L))
				.willReturn(org.mockito.Mockito.mock(User.class));
		given(idempotencyKeyRepository.saveAndFlush(org.mockito.ArgumentMatchers.any()))
				.willAnswer(invocation -> invocation.getArgument(0));
		given(idempotencyKeyRepository.save(org.mockito.ArgumentMatchers.any()))
				.willAnswer(invocation -> invocation.getArgument(0));

		IdempotencyExecution execution = idempotencyService.start(
				7L,
				"PAYMENT_REFUND",
				"refund-key",
				"request-hash"
		);
		IdempotencyKey key = execution.key();

		assertThat(key.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 7, 31, 10, 0));
		assertThat(Duration.between(key.getCreatedAt(), key.getExpiresAt()))
				.isEqualTo(Duration.ofDays(7));

		idempotencyService.succeed(
				key,
				200,
				"{\"id\":31}",
				"PAYMENT_REFUND",
				31L
		);

		assertThat(key.getCompletedAt()).isEqualTo(LocalDateTime.of(2026, 7, 31, 10, 0));
		assertThat(Duration.between(key.getCompletedAt(), key.getResponseExpiresAt()))
				.isEqualTo(Duration.ofDays(7));
	}

	@Test
	void nonPaymentKeyKeepsDefaultOneDayRetention() {
		given(idempotencyKeyRepository.findForUpdate(7L, "CARD_PURCHASE", "purchase-key"))
				.willReturn(Optional.empty());
		given(userRepository.getReferenceById(7L))
				.willReturn(org.mockito.Mockito.mock(User.class));
		given(idempotencyKeyRepository.saveAndFlush(org.mockito.ArgumentMatchers.any()))
				.willAnswer(invocation -> invocation.getArgument(0));

		IdempotencyKey key = idempotencyService.start(
				7L,
				"CARD_PURCHASE",
				"purchase-key",
				"request-hash"
		).key();

		assertThat(Duration.between(key.getCreatedAt(), key.getExpiresAt()))
				.isEqualTo(Duration.ofHours(24));
	}
}
