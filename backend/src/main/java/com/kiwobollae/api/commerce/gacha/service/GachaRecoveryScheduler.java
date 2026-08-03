package com.kiwobollae.api.commerce.gacha.service;

import static com.kiwobollae.api.commerce.gacha.GachaTimeZone.KST;

import com.kiwobollae.api.commerce.gacha.entity.enums.GachaDrawStatus;
import com.kiwobollae.api.commerce.gacha.repository.GachaDrawRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GachaRecoveryScheduler {

  private static final int BATCH_SIZE = 50;

  private final GachaDrawRepository gachaDrawRepository;
  private final GachaDrawWorker drawWorker;
  private final GachaFailureService failureService;

  @Scheduled(fixedDelay = 30_000)
  public void processWaitingDraws() {
    LocalDateTime now = LocalDateTime.now(KST);
    List<Long> drawIds =
        gachaDrawRepository.findProcessableIds(
            GachaDrawStatus.PENDING,
            GachaDrawStatus.RETRYABLE_FAILED,
            now,
            PageRequest.of(0, BATCH_SIZE));
    drawIds.forEach(drawWorker::process);
  }

  @Scheduled(fixedDelay = 30_000)
  public void recoverStaleDraws() {
    LocalDateTime staleBefore = LocalDateTime.now(KST).minusMinutes(5);
    List<Long> drawIds =
        gachaDrawRepository.findStaleProcessingIds(
            GachaDrawStatus.PROCESSING, staleBefore, PageRequest.of(0, BATCH_SIZE));
    drawIds.forEach(failureService::recoverStale);
  }
}
