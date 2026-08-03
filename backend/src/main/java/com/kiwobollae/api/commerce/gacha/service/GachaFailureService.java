package com.kiwobollae.api.commerce.gacha.service;

import static com.kiwobollae.api.commerce.gacha.GachaTimeZone.KST;

import com.kiwobollae.api.commerce.gacha.entity.GachaDraw;
import com.kiwobollae.api.commerce.gacha.entity.enums.GachaDrawStatus;
import com.kiwobollae.api.commerce.gacha.entity.enums.GachaSourceType;
import com.kiwobollae.api.commerce.gacha.repository.GachaDrawRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.point.service.WalletService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GachaFailureService {

  private static final long[] BACKOFF_MINUTES = {1, 5, 30};

  private final GachaDrawRepository gachaDrawRepository;
  private final WalletService walletService;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordFailure(Long drawId, RuntimeException exception) {
    GachaDraw draw = gachaDrawRepository.findByIdForUpdate(drawId).orElse(null);
    if (draw == null
        || draw.getStatus() == GachaDrawStatus.COMPLETED
        || draw.getStatus() == GachaDrawStatus.MANUAL_REVIEW
        || draw.getStatus() == GachaDrawStatus.REFUNDED) {
      return;
    }
    LocalDateTime now = LocalDateTime.now(KST);
    int backoffIndex = Math.min(draw.getAttemptCount(), BACKOFF_MINUTES.length - 1);
    String errorCode =
        exception instanceof BusinessException business
            ? business.getErrorCode().name()
            : exception.getClass().getSimpleName();
    if (shouldRefundAfterManualRetry(draw)) {
      refundPurchase(draw, errorCode);
      return;
    }
    draw.markRetryable(errorCode, now.plusMinutes(BACKOFF_MINUTES[backoffIndex]));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recoverStale(Long drawId) {
    GachaDraw draw = gachaDrawRepository.findByIdForUpdate(drawId).orElse(null);
    if (draw == null || draw.getStatus() != GachaDrawStatus.PROCESSING) {
      return;
    }
    if (shouldRefundAfterManualRetry(draw)) {
      refundPurchase(draw, "PROCESSING_TIMEOUT");
      return;
    }
    draw.markRetryable("PROCESSING_TIMEOUT", LocalDateTime.now(KST).plusMinutes(1));
  }

  private boolean shouldRefundAfterManualRetry(GachaDraw draw) {
    return draw.getSourceType() == GachaSourceType.PURCHASE && draw.getAttemptCount() >= 4;
  }

  private void refundPurchase(GachaDraw draw, String errorCode) {
    walletService.restoreGachaPurchasePoints(draw.getUser().getId(), draw.getSourceId());
    draw.refund(errorCode);
  }
}
