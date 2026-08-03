package com.kiwobollae.api.commerce.gacha.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class GachaRewardEventListener {

  private final GachaDrawWorker drawWorker;

  @Async("gachaTaskExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onRewardCreated(GachaRewardCreatedEvent event) {
    drawWorker.process(event.drawId());
  }
}
