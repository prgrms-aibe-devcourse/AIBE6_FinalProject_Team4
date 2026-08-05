package com.kiwobollae.api.content.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlantTimelapseWorkerTest {

	@Mock private PlantTimelapseTransactionService transactionService;

	@InjectMocks
	private PlantTimelapseWorker worker;

	@Test
	void processDelegatesToTransactionService() {
		worker.process(21L);

		verify(transactionService).process(21L);
	}

	@Test
	void processCatchesExceptionAndMarksFailed() {
		willThrow(new TimelapseEncodingException("boom")).given(transactionService).process(21L);

		worker.process(21L);

		verify(transactionService).fail(eq(21L), eq("boom"));
	}
}
