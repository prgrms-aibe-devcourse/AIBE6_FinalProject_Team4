package com.kiwobollae.api.ai.policy;

import com.kiwobollae.api.ai.config.AiPolicyProperties;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AiRequestGuard {

  private static final long CLEANUP_INTERVAL_MASK = 0xffL;

  private final AiPolicyProperties properties;
  private final LongSupplier currentTimeMillis;
  private final ConcurrentHashMap<RequestKey, Window> windows = new ConcurrentHashMap<>();
  private final AtomicLong requestCount = new AtomicLong();

  @Autowired
  public AiRequestGuard(AiPolicyProperties properties) {
    this(properties, System::currentTimeMillis);
  }

  AiRequestGuard(AiPolicyProperties properties, LongSupplier currentTimeMillis) {
    this.properties = properties;
    this.currentTimeMillis = currentTimeMillis;
  }

  public void checkRateLimit(Long userId, AiFeature feature) {
    if (userId == null || userId <= 0) {
      throw new IllegalArgumentException("AI 호출 사용자 ID가 필요합니다.");
    }
    if (feature == null) {
      throw new IllegalArgumentException("AI 기능 구분이 필요합니다.");
    }

    long now = currentTimeMillis.getAsLong();
    long windowMillis = properties.rateLimit().window().toMillis();
    RequestKey key = new RequestKey(userId, feature);
    Window window = windows.computeIfAbsent(key, ignored -> new Window(now));
    long retryAfterSeconds =
        window.tryConsume(now, windowMillis, properties.rateLimit().maxRequests());
    cleanupExpiredWindowsPeriodically(now, windowMillis);

    if (retryAfterSeconds > 0) {
      throw new BusinessException(
          ErrorCode.COMMON_RATE_LIMITED, Map.of("retryAfterSeconds", retryAfterSeconds));
    }
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

  private void cleanupExpiredWindowsPeriodically(long now, long windowMillis) {
    if ((requestCount.incrementAndGet() & CLEANUP_INTERVAL_MASK) != 0) {
      return;
    }
    windows.entrySet().removeIf(entry -> entry.getValue().isExpired(now, windowMillis));
  }

  private record RequestKey(Long userId, AiFeature feature) {}

  private static final class Window {

    private volatile long startedAtMillis;
    private int consumed;

    private Window(long startedAtMillis) {
      this.startedAtMillis = startedAtMillis;
    }

    private synchronized long tryConsume(long now, long windowMillis, int limit) {
      if (now - startedAtMillis >= windowMillis) {
        startedAtMillis = now;
        consumed = 0;
      }
      if (consumed < limit) {
        consumed++;
        return 0;
      }
      long remainingMillis = Math.max(1L, startedAtMillis + windowMillis - now);
      return Math.max(1L, (remainingMillis + 999L) / 1_000L);
    }

    private boolean isExpired(long now, long windowMillis) {
      return now - startedAtMillis >= windowMillis * 2;
    }
  }
}
