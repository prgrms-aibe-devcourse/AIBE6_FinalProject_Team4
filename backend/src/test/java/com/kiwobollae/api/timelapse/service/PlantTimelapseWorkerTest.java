package com.kiwobollae.api.timelapse.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kiwobollae.api.timelapse.exception.TimelapseEncodingException;
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
	void processDoesNothingElseWhenClaimFails() {
		given(transactionService.claim(21L)).willReturn(false);

		worker.process(21L, null);

		verify(transactionService, never()).encodeAndUpload(any(), any());
		verify(transactionService, never()).complete(any(), any());
	}

	@Test
	void processEncodesAndCompletesWhenClaimSucceeds() {
		given(transactionService.claim(21L)).willReturn(true);
		given(transactionService.encodeAndUpload(eq(21L), isNull())).willReturn("/api/v1/plants/timelapse-videos/7/new.mp4");

		worker.process(21L, null);

		verify(transactionService).complete(21L, "/api/v1/plants/timelapse-videos/7/new.mp4");
	}

	@Test
	void processPassesPreviousVideoUrlThroughToEncodeAndUpload() {
		given(transactionService.claim(21L)).willReturn(true);
		given(transactionService.encodeAndUpload(21L, "/api/v1/plants/timelapse-videos/7/old.mp4"))
				.willReturn("/api/v1/plants/timelapse-videos/7/new.mp4");

		worker.process(21L, "/api/v1/plants/timelapse-videos/7/old.mp4");

		verify(transactionService).encodeAndUpload(21L, "/api/v1/plants/timelapse-videos/7/old.mp4");
	}

	@Test
	void processCatchesExceptionAndMarksFailed() {
		given(transactionService.claim(21L)).willReturn(true);
		willThrow(new TimelapseEncodingException("boom")).given(transactionService).encodeAndUpload(eq(21L), isNull());

		worker.process(21L, null);

		verify(transactionService).fail(eq(21L), eq("boom"));
	}

	@Test
	void processDoesNotPropagateWhenFailItselfThrows() {
		given(transactionService.claim(21L)).willReturn(true);
		willThrow(new TimelapseEncodingException("boom")).given(transactionService).encodeAndUpload(eq(21L), isNull());
		willThrow(new java.util.NoSuchElementException()).given(transactionService).fail(21L, "boom");

		org.assertj.core.api.Assertions.assertThatCode(() -> worker.process(21L, null)).doesNotThrowAnyException();
	}
}
