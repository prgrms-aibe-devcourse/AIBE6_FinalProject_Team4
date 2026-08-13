package com.kiwobollae.api.infra.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.infra.entity.IdempotencyKey;
import com.kiwobollae.api.infra.entity.enums.IdempotencyStatus;
import com.kiwobollae.api.infra.repository.IdempotencyKeyRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final Clock FIXED_KST_CLOCK =
			Clock.fixed(Instant.parse("2026-07-31T01:00:00Z"), KST);

	@Mock
	private IdempotencyKeyRepository idempotencyKeyRepository;

	private IdempotencyService idempotencyService;

	@BeforeEach
	void setUp() {
		idempotencyService = new IdempotencyService(
				idempotencyKeyRepository,
				FIXED_KST_CLOCK
		);
	}

	@Test
	void paymentRefundKeyAndResponseAreRetainedForSevenDays() {
		IdempotencyKey key = spy(IdempotencyKey.builder()
				.apiType("PAYMENT_REFUND")
				.status(IdempotencyStatus.IN_PROGRESS)
				.createdAt(LocalDateTime.of(2026, 7, 31, 10, 0))
				.expiresAt(LocalDateTime.of(2026, 8, 7, 10, 0))
				.build());
		AtomicReference<String> claimToken = new AtomicReference<>();
		given(idempotencyKeyRepository.claim(
				org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any()
		)).willAnswer(invocation -> {
			claimToken.set(invocation.getArgument(4));
			return 1;
		});
		given(key.getClaimToken()).willAnswer(invocation -> claimToken.get());
		given(idempotencyKeyRepository.findForUpdate(7L, "PAYMENT_REFUND", "refund-key"))
				.willReturn(Optional.of(key));
		given(idempotencyKeyRepository.save(org.mockito.ArgumentMatchers.any()))
				.willAnswer(invocation -> invocation.getArgument(0));

		IdempotencyExecution execution = idempotencyService.start(
				7L,
				"PAYMENT_REFUND",
				"refund-key",
				"request-hash"
		);
		key = execution.key();

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
		IdempotencyKey key = mock(IdempotencyKey.class);
		AtomicReference<String> claimToken = new AtomicReference<>();
		given(idempotencyKeyRepository.claim(
				org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any()
		)).willAnswer(invocation -> {
			claimToken.set(invocation.getArgument(4));
			return 1;
		});
		given(key.getClaimToken()).willAnswer(invocation -> claimToken.get());
		given(idempotencyKeyRepository.findForUpdate(7L, "CARD_PURCHASE", "purchase-key"))
				.willReturn(Optional.of(key));

		idempotencyService.start(
				7L,
				"CARD_PURCHASE",
				"purchase-key",
				"request-hash"
		);

		ArgumentCaptor<LocalDateTime> expiresAt = ArgumentCaptor.forClass(LocalDateTime.class);
		ArgumentCaptor<LocalDateTime> createdAt = ArgumentCaptor.forClass(LocalDateTime.class);
		verify(idempotencyKeyRepository).claim(
				org.mockito.ArgumentMatchers.eq(7L),
				org.mockito.ArgumentMatchers.eq("CARD_PURCHASE"),
				org.mockito.ArgumentMatchers.eq("purchase-key"),
				org.mockito.ArgumentMatchers.eq("request-hash"),
				org.mockito.ArgumentMatchers.anyString(),
				expiresAt.capture(),
				createdAt.capture()
		);
		assertThat(Duration.between(createdAt.getValue(), expiresAt.getValue()))
				.isEqualTo(Duration.ofHours(24));
	}

	@Test
	void returnsCompletedReplayWithoutTakingWriteLock() {
		IdempotencyKey key = mock(IdempotencyKey.class);
		given(key.getRequestHash()).willReturn("request-hash");
		given(key.getStatus()).willReturn(IdempotencyStatus.SUCCEEDED);
		given(idempotencyKeyRepository.findByUser_IdAndApiTypeAndClientKey(
				7L, "GACHA_COSMETIC_PURCHASE", "replay-key"))
				.willReturn(Optional.of(key));

		Optional<IdempotencyExecution> replay = idempotencyService.replayIfPresent(
				7L,
				"GACHA_COSMETIC_PURCHASE",
				"replay-key",
				"request-hash"
		);

		assertThat(replay).hasValueSatisfying(execution -> assertThat(execution.replay()).isTrue());
		verify(idempotencyKeyRepository, never())
				.findForUpdate(7L, "GACHA_COSMETIC_PURCHASE", "replay-key");
	}

	@Test
	void acceptsSucceededLegacyHashForVersionedRequest() {
		IdempotencyKey key = mock(IdempotencyKey.class);
		given(key.getRequestHash()).willReturn("legacy-hash");
		given(key.getStatus()).willReturn(IdempotencyStatus.SUCCEEDED);
		given(idempotencyKeyRepository.findForUpdate(
				7L, "GACHA_PACK_PURCHASE", "purchase-key"))
				.willReturn(Optional.of(key));
		given(idempotencyKeyRepository.claim(
				org.mockito.ArgumentMatchers.eq(7L),
				org.mockito.ArgumentMatchers.eq("GACHA_PACK_PURCHASE"),
				org.mockito.ArgumentMatchers.eq("purchase-key"),
				org.mockito.ArgumentMatchers.eq("versioned-hash"),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any()
		)).willReturn(0);

		IdempotencyExecution execution = idempotencyService.startWithCompatibleHash(
				7L,
				"GACHA_PACK_PURCHASE",
				"purchase-key",
				"versioned-hash",
				"legacy-hash"
		);

		assertThat(execution.replay()).isTrue();
		assertThat(execution.key()).isSameAs(key);
	}

	@Test
	void reportsLegacyRequestInProgressForVersionedRetry() {
		IdempotencyKey key = mock(IdempotencyKey.class);
		given(key.getRequestHash()).willReturn("legacy-hash");
		given(key.getStatus()).willReturn(IdempotencyStatus.IN_PROGRESS);
		given(idempotencyKeyRepository.findForUpdate(
				7L, "GACHA_PACK_PURCHASE", "purchase-key"))
				.willReturn(Optional.of(key));
		given(idempotencyKeyRepository.claim(
				org.mockito.ArgumentMatchers.eq(7L),
				org.mockito.ArgumentMatchers.eq("GACHA_PACK_PURCHASE"),
				org.mockito.ArgumentMatchers.eq("purchase-key"),
				org.mockito.ArgumentMatchers.eq("versioned-hash"),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any()
		)).willReturn(0);

		assertThatThrownBy(() -> idempotencyService.startWithCompatibleHash(
				7L,
				"GACHA_PACK_PURCHASE",
				"purchase-key",
				"versioned-hash",
				"legacy-hash"
		))
				.isInstanceOfSatisfying(
						BusinessException.class,
						exception -> assertThat(exception.getErrorCode())
								.isEqualTo(ErrorCode.COMMON_IDEMPOTENCY_IN_PROGRESS)
				);
	}
}
