package com.kiwobollae.api.commerce.gacha.service;

import static com.kiwobollae.api.commerce.gacha.GachaTimeZone.KST;

import com.kiwobollae.api.commerce.gacha.dto.GachaManualRetryResponse;
import com.kiwobollae.api.commerce.gacha.entity.enums.GachaDrawStatus;
import com.kiwobollae.api.commerce.gacha.repository.GachaDrawRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GachaManualReviewService {

  private final GachaDrawRepository gachaDrawRepository;

  @Transactional
  public GachaManualRetryResponse retry(Long drawId) {
    int updated =
        gachaDrawRepository.requeueManualReview(
            drawId, GachaDrawStatus.MANUAL_REVIEW, GachaDrawStatus.PENDING, LocalDateTime.now(KST));
    if (updated == 1) {
      return new GachaManualRetryResponse(drawId, GachaDrawStatus.PENDING);
    }
    if (!gachaDrawRepository.existsById(drawId)) {
      throw new BusinessException(ErrorCode.GACHA_DRAW_NOT_FOUND);
    }
    throw new BusinessException(ErrorCode.GACHA_MANUAL_RETRY_INVALID_STATE);
  }
}
