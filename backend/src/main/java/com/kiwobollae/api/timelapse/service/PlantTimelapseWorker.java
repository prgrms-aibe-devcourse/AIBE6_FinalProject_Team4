package com.kiwobollae.api.timelapse.service;

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
			markFailed(profileId, failReasonOf(exception));
		}
	}

	// fail() 자체가 실패하면(그 사이 프로필/행이 삭제됐거나 DB 오류 등) 이 예외가 그대로 새어나가
	// @Async 메서드 밖에서 조용히 사라지고, 행은 PROCESSING에 영구히 남아 재요청도 막힌다
	// (TIMELAPSE_ALREADY_PROCESSING). 자동 복구는 범위 밖v
	// functional-spec §8)이라 여기서는
	// 최소한 로그로 남겨 운영에서 인지·수동 개입할 수 있게 한다.
	private void markFailed(Long profileId, String reason) {
		try {
			transactionService.fail(profileId, reason);
		} catch (RuntimeException exception) {
			log.error("Failed to mark plant timelapse as FAILED; profileId={} may be stuck in PROCESSING and require manual recovery.",
					profileId, exception);
		}
	}

	private String failReasonOf(RuntimeException exception) {
		String message = exception.getMessage();
		return message != null ? message : exception.getClass().getSimpleName();
	}
}
