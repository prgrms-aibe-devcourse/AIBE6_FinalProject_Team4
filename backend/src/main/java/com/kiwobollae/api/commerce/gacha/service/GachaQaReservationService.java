package com.kiwobollae.api.commerce.gacha.service;

import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.commerce.gacha.dto.GachaQaBatchDrawResponse;
import com.kiwobollae.api.commerce.gacha.dto.GachaQaDrawResponse;
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
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local")
@ConditionalOnProperty(prefix = "app.gacha.qa", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class GachaQaReservationService {

  private static final String API_TYPE = "GACHA_QA_TEST";

  private final IdempotencyService idempotencyService;
  private final GachaDrawRepository gachaDrawRepository;
  private final UserRepository userRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public GachaQaDrawResponse reserve(Long userId, String clientKey) {
    return reserveOne(userId, clientKey);
  }

  @Transactional
  public GachaQaBatchDrawResponse reserveBatch(Long userId, String clientKey, int packCount) {
    if (packCount < 1 || packCount > 100) {
      throw new IllegalArgumentException("QA pack count must be between 1 and 100.");
    }
    List<Long> drawIds = new ArrayList<>(packCount);
    for (int index = 0; index < packCount; index++) {
      drawIds.add(reserveOne(userId, clientKey + "-" + index).drawId());
    }
    return new GachaQaBatchDrawResponse(List.copyOf(drawIds), packCount);
  }

  private GachaQaDrawResponse reserveOne(Long userId, String clientKey) {
    if (userId == null) {
      throw new BusinessException(ErrorCode.AUTH_AUTHENTICATION_REQUIRED);
    }

    IdempotencyExecution execution =
        idempotencyService.start(
            userId, API_TYPE, clientKey, sha256(userId + "|" + clientKey + "|ADMIN"));
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
      return new GachaQaDrawResponse(drawId, status);
    }

    GachaDraw draw =
        gachaDrawRepository.saveAndFlush(
            GachaDraw.builder()
                .user(userRepository.getReferenceById(userId))
                .sourceType(GachaSourceType.ADMIN)
                .sourceId(execution.key().getId())
                .status(GachaDrawStatus.PENDING)
                .drawCount(5)
                .rateVersion(1)
                .attemptCount(0)
                .build());
    idempotencyService.succeed(
        execution.key(), 201, "{\"drawId\":" + draw.getId() + "}", "GACHA_DRAW", draw.getId());
    eventPublisher.publishEvent(new GachaRewardCreatedEvent(draw.getId()));
    return new GachaQaDrawResponse(draw.getId(), draw.getStatus());
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
