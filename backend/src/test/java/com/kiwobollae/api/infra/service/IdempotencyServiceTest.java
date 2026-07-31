package com.kiwobollae.api.infra.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.infra.entity.IdempotencyKey;
import com.kiwobollae.api.infra.repository.IdempotencyKeyRepository;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

	@Mock
	private IdempotencyKeyRepository idempotencyKeyRepository;

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private IdempotencyService idempotencyService;

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

		assertThat(Duration.between(key.getCreatedAt(), key.getExpiresAt()))
				.isEqualTo(Duration.ofDays(7));

		idempotencyService.succeed(
				key,
				200,
				"{\"id\":31}",
				"PAYMENT_REFUND",
				31L
		);

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
