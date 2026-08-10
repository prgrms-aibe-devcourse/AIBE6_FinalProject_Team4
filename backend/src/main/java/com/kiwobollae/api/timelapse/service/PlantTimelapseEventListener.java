package com.kiwobollae.api.timelapse.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PlantTimelapseEventListener {

	private final PlantTimelapseWorker worker;

	@Async("timelapseTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onTimelapseRequested(PlantTimelapseRequestedEvent event) {
		worker.process(event.profileId(), event.previousVideoUrl());
	}
}
