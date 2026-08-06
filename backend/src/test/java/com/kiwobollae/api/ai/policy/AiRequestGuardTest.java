package com.kiwobollae.api.ai.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.kiwobollae.api.ai.config.AiPolicyProperties;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * 창 계산과 원자성은 DB가 담당하므로 {@link AiRateLimitStore}의 MySQL 통합 테스트에서 검증한다. 여기서는 가드가 저장소 결과를 어떻게 번역하는지와
 * 입력 검증만 본다.
 */
class AiRequestGuardTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final Clock FIXED_KST_CLOCK =
      Clock.fixed(Instant.parse("2026-08-05T01:00:00Z"), KST);
  private static final LocalDateTime FIXED_KST_TIME = LocalDateTime.of(2026, 8, 5, 10, 0);

  private final AiRateLimitStore rateLimitStore = mock(AiRateLimitStore.class);

  @Test
  void translatesStoreRejectionIntoRateLimitedError() {
    doThrow(new AiQuotaExceededException(42L))
        .when(rateLimitStore)
        .consume(
            eq(1L),
            eq(AiFeature.PLANT_CHAT),
            any(LocalDateTime.class),
            any(Duration.class),
            anyInt(),
            any(Duration.class),
            anyInt());

    assertThatThrownBy(() -> guard(2, 5).checkRateLimit(1L, AiFeature.PLANT_CHAT))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> {
              assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMON_RATE_LIMITED);
              assertThat(exception.getDetails()).containsEntry("retryAfterSeconds", 42L);
            });
  }

  @Test
  void allowsRequestWhenStoreConsumesIt() {
    assertThatCode(() -> guard(2, 5).checkRateLimit(1L, AiFeature.PLANT_CHAT))
        .doesNotThrowAnyException();
  }

  // 카운터가 DB에 있으므로 시각은 애플리케이션이 정해 넘긴다. 프로젝트 표준인 KST 시계를 쓰는지 고정한다.
  @Test
  void passesConfiguredPolicyAndSeoulClockToStore() {
    guard(7, 5).checkRateLimit(1L, AiFeature.JOURNAL_GUIDE);

    verify(rateLimitStore)
        .consume(
            1L,
            AiFeature.JOURNAL_GUIDE,
            FIXED_KST_TIME,
            Duration.ofMinutes(1),
            7,
            Duration.ofDays(1),
            100);
  }

  @Test
  void rejectsMissingUserIdOrFeature() {
    AiRequestGuard guard = guard(2, 5);

    assertThatThrownBy(() -> guard.checkRateLimit(null, AiFeature.PLANT_CHAT))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> guard.checkRateLimit(0L, AiFeature.PLANT_CHAT))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> guard.checkRateLimit(1L, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void validatesBlankAndOversizedUserInput() {
    AiRequestGuard guard = guard(2, 5);

    assertThatThrownBy(() -> guard.validateUserInput(" "))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED));
    assertThatThrownBy(() -> guard.validateUserInput("123456"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getMessage()).contains("5자 이하"));
    assertThatCode(() -> guard.validateUserInput("12345")).doesNotThrowAnyException();
  }

  private AiRequestGuard guard(int maxRequests, int maxInputLength) {
    return new AiRequestGuard(
        new AiPolicyProperties(
            maxInputLength,
            new AiPolicyProperties.RateLimit(maxRequests, Duration.ofMinutes(1)),
            new AiPolicyProperties.RateLimit(100, Duration.ofDays(1))),
        rateLimitStore,
        FIXED_KST_CLOCK);
  }
}
