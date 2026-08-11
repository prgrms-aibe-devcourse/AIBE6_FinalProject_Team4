package com.kiwobollae.api.timelapse.service;

import com.kiwobollae.api.timelapse.repository.PlantTimelapseRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 정상 워커가 죽거나(서버 재시작 등) fail() 자체가 실패해서 PROCESSING에 갇히거나, 실행 큐가
 * 가득 차 작업 제출 자체가 거부돼(RejectedExecutionException) PENDING에 갇힌 행을 주기적으로
 * 정리한다. commerce/gacha의 GachaRecoveryScheduler와 같은 목적이지만, 재시도/환불 같은 도메인
 * 로직이 없어 훨씬 단순하다 — 그냥 FAILED로 전환해서 사용자가 직접 재요청할 수 있게 할 뿐이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlantTimelapseRecoveryScheduler {

	private static final int BATCH_SIZE = 50;
	private static final int STALE_MINUTES = 5;
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final PlantTimelapseRepository plantTimelapseRepository;
	private final PlantTimelapseTransactionService transactionService;

	@Scheduled(fixedDelay = 30_000)
	public void recoverStaleRequests() {
		LocalDateTime staleBefore = LocalDateTime.now(KST).minusMinutes(STALE_MINUTES);
		List<Long> profileIds = plantTimelapseRepository.findStalePendingOrProcessingProfileIds(staleBefore, PageRequest.of(0, BATCH_SIZE));
		for (Long profileId : profileIds) {
			try {
				transactionService.recoverStale(profileId);
			} catch (RuntimeException exception) {
				// 한 건이 실패해도 나머지 배치는 계속 처리한다 — 여기서마저 실패하면 다음 스케줄
				// 주기(30초 뒤)에 다시 시도된다.
				log.error("Failed to recover stale plant timelapse. profileId={}", profileId, exception);
			}
		}
	}
}
