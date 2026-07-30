package com.kiwobollae.api.commerce.gacha.service;

import com.kiwobollae.api.commerce.gacha.entity.GachaDraw;
import com.kiwobollae.api.commerce.gacha.entity.enums.GachaDrawStatus;
import com.kiwobollae.api.commerce.gacha.repository.GachaDrawRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GachaFailureService {

  private static final long[] BACKOFF_MINUTES = {1, 5, 30};

  private final GachaDrawRepository gachaDrawRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordFailure(Long drawId, RuntimeException exception) {
    GachaDraw draw = gachaDrawRepository.findByIdForUpdate(drawId).orElse(null);
    if (draw == null
        || draw.getStatus() == GachaDrawStatus.COMPLETED
        || draw.getStatus() == GachaDrawStatus.MANUAL_REVIEW) {
      return;
    }
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    int backoffIndex = Math.min(draw.getAttemptCount(), BACKOFF_MINUTES.length - 1);
    String errorCode =
        exception instanceof BusinessException business
            ? business.getErrorCode().name()
            : exception.getClass().getSimpleName();
    draw.markRetryable(errorCode, now.plusMinutes(BACKOFF_MINUTES[backoffIndex]));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recoverStale(Long drawId) {
    GachaDraw draw = gachaDrawRepository.findByIdForUpdate(drawId).orElse(null);
    if (draw == null || draw.getStatus() != GachaDrawStatus.PROCESSING) {
      return;
    }
    draw.markRetryable("PROCESSING_TIMEOUT", LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1));
  }
}
