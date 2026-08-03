package com.kiwobollae.api.commerce.gacha.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GachaDrawWorker {

  private final GachaDrawTransactionService transactionService;
  private final GachaFailureService failureService;

  public void process(Long drawId) {
    try {
      transactionService.process(drawId);
    } catch (RuntimeException exception) {
      log.warn("Gacha draw processing failed. drawId={}", drawId, exception);
      failureService.recordFailure(drawId, exception);
    }
  }
}
