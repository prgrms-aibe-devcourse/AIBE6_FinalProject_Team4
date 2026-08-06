package com.kiwobollae.api.ai.policy;

import com.kiwobollae.api.ai.config.AiPolicyProperties;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Component;

@Component
public class AiRequestGuard {

  // 같은 (user, feature)의 첫 호출이 동시에 몰리면 창 행을 만드는 INSERT가 서로 경합해 드물게
  // 데드락이 난다. 창이 한 번 만들어진 뒤에는 조건부 UPDATE만 남아 발생하지 않으므로, 짧은
  // 재시도로 흡수한다. 모두 실패하면 예외를 그대로 올려 AI 호출을 하지 않는다 — 소비량을 셀 수
  // 없는 상태에서 외부 호출을 통과시키면 한도가 무의미해진다.
  private static final int MAX_CONSUME_ATTEMPTS = 3;

  private final AiPolicyProperties properties;
  private final AiRateLimitStore rateLimitStore;
  private final Clock seoulClock;

  public AiRequestGuard(
      AiPolicyProperties properties, AiRateLimitStore rateLimitStore, Clock seoulClock) {
    this.properties = properties;
    this.rateLimitStore = rateLimitStore;
    this.seoulClock = seoulClock;
  }

  public void checkRateLimit(Long userId, AiFeature feature) {
    if (userId == null || userId <= 0) {
      throw new IllegalArgumentException("AI 호출 사용자 ID가 필요합니다.");
    }
    if (feature == null) {
      throw new IllegalArgumentException("AI 기능 구분이 필요합니다.");
    }

    try {
      consumeWithRetry(userId, feature);
    } catch (AiQuotaExceededException exception) {
      throw new BusinessException(
          ErrorCode.COMMON_RATE_LIMITED,
          Map.of("retryAfterSeconds", exception.retryAfterSeconds()));
    }
  }

  private void consumeWithRetry(Long userId, AiFeature feature) {
    CannotAcquireLockException lastFailure = null;
    for (int attempt = 1; attempt <= MAX_CONSUME_ATTEMPTS; attempt++) {
      try {
        rateLimitStore.consume(
            userId,
            feature,
            LocalDateTime.now(seoulClock),
            properties.rateLimit().window(),
            properties.rateLimit().maxRequests(),
            properties.globalRateLimit().window(),
            properties.globalRateLimit().maxRequests());
        return;
      } catch (CannotAcquireLockException exception) {
        lastFailure = exception;
      }
    }
    throw lastFailure;
  }

  public void validateUserInput(String input) {
    if (input == null || input.isBlank()) {
      throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "AI 요청 내용을 입력해 주세요.");
    }
    if (input.length() > properties.maxInputLength()) {
      throw new BusinessException(
          ErrorCode.COMMON_VALIDATION_FAILED,
          "AI 요청 내용은 " + properties.maxInputLength() + "자 이하로 입력해 주세요.");
    }
  }
}
