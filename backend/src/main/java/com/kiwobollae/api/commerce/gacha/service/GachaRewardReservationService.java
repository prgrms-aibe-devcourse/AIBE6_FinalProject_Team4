package com.kiwobollae.api.commerce.gacha.service;

import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.commerce.gacha.entity.GachaDraw;
import com.kiwobollae.api.commerce.gacha.entity.enums.GachaDrawStatus;
import com.kiwobollae.api.commerce.gacha.entity.enums.GachaSourceType;
import com.kiwobollae.api.commerce.gacha.repository.GachaDrawRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.infra.service.IdempotencyExecution;
import com.kiwobollae.api.infra.service.IdempotencyService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GachaRewardReservationService {

  private static final String API_TYPE = "GACHA_DAILY_JOURNAL_REWARD";

  private final IdempotencyService idempotencyService;
  private final GachaDrawRepository gachaDrawRepository;
  private final UserRepository userRepository;
  private final ApplicationEventPublisher eventPublisher;

  public GachaRewardReservation reserveDailyJournalReward(Long userId, LocalDate rewardDate) {
    String clientKey = rewardDate.toString();
    IdempotencyExecution execution =
        idempotencyService.start(
            userId, API_TYPE, clientKey, sha256(userId + "|" + clientKey + "|LOG_REWARD"));
    if (execution.replay()) {
      Long drawId = execution.key().getResourceId();
      if (drawId == null) {
        throw new BusinessException(ErrorCode.GACHA_PROCESSING_CONFLICT);
      }
      GachaDrawStatus status =
          gachaDrawRepository
              .findById(drawId)
              .map(GachaDraw::getStatus)
              .orElse(GachaDrawStatus.MANUAL_REVIEW);
      return new GachaRewardReservation(false, drawId, status);
    }

    GachaDraw draw =
        gachaDrawRepository.saveAndFlush(
            GachaDraw.builder()
                .user(userRepository.getReferenceById(userId))
                .sourceType(GachaSourceType.LOG_REWARD)
                .sourceId(execution.key().getId())
                .status(GachaDrawStatus.PENDING)
                .drawCount(5)
                .rateVersion(1)
                .attemptCount(0)
                .build());
    idempotencyService.succeed(
        execution.key(), 201, "{\"drawId\":" + draw.getId() + "}", "GACHA_DRAW", draw.getId());
    eventPublisher.publishEvent(new GachaRewardCreatedEvent(draw.getId()));
    return new GachaRewardReservation(true, draw.getId(), draw.getStatus());
  }

  private String sha256(String source) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available.", exception);
    }
  }
}
