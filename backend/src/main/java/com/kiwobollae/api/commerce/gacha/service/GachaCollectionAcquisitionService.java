package com.kiwobollae.api.commerce.gacha.service;

import com.kiwobollae.api.commerce.gacha.repository.UserCardCollectionRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GachaCollectionAcquisitionService {

  private final UserCardCollectionRepository collectionRepository;

  @Transactional(propagation = Propagation.MANDATORY)
  public int acquireNormal(Long userId, Long cardId, LocalDateTime now) {
    collectionRepository.ensureCollectionRow(userId, cardId, now);
    collectionRepository
        .findForUpdate(userId, cardId)
        .orElseThrow(() -> new BusinessException(ErrorCode.GACHA_MASTER_DATA_INVALID));
    collectionRepository.incrementOwnedCount(userId, cardId, now);
    return collectionRepository
        .findOwnedCount(userId, cardId)
        .orElseThrow(() -> new BusinessException(ErrorCode.GACHA_MASTER_DATA_INVALID));
  }
}
