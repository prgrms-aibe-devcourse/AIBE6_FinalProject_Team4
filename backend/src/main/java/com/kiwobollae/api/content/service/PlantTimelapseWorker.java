package com.kiwobollae.api.content.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlantTimelapseWorker {

	private final PlantTimelapseTransactionService transactionService;

	public void process(Long profileId, String previousVideoUrl) {
		try {
			if (!transactionService.claim(profileId)) {
				return;
			}
			String videoUrl = transactionService.encodeAndUpload(profileId, previousVideoUrl);
			transactionService.complete(profileId, videoUrl);
		} catch (RuntimeException exception) {
			log.warn("Plant timelapse processing failed. profileId={}", profileId, exception);
			transactionService.fail(profileId, failReasonOf(exception));
		}
	}

	private String failReasonOf(RuntimeException exception) {
		String message = exception.getMessage();
		return message != null ? message : exception.getClass().getSimpleName();
	}
}
