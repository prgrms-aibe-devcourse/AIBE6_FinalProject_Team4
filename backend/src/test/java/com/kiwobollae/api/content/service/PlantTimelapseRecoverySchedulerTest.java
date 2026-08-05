package com.kiwobollae.api.content.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.kiwobollae.api.content.repository.PlantTimelapseRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlantTimelapseRecoverySchedulerTest {

	@Mock private PlantTimelapseRepository plantTimelapseRepository;
	@Mock private PlantTimelapseTransactionService transactionService;

	@InjectMocks
	private PlantTimelapseRecoveryScheduler scheduler;

	@Test
	void recoverStaleRequestsDelegatesToTransactionServiceForEachStaleProfile() {
		given(plantTimelapseRepository.findStalePendingOrProcessingProfileIds(any(), any()))
				.willReturn(List.of(11L, 12L));

		scheduler.recoverStaleRequests();

		verify(transactionService).recoverStale(11L);
		verify(transactionService).recoverStale(12L);
	}

	@Test
	void recoverStaleRequestsContinuesWhenOneProfileThrows() {
		given(plantTimelapseRepository.findStalePendingOrProcessingProfileIds(any(), any()))
				.willReturn(List.of(11L, 12L));
		willThrow(new RuntimeException("boom")).given(transactionService).recoverStale(11L);

		assertThatCode(() -> scheduler.recoverStaleRequests()).doesNotThrowAnyException();

		verify(transactionService).recoverStale(12L);
	}
}
