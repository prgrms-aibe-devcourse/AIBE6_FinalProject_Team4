package com.kiwobollae.api.ai.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kiwobollae.api.ai.config.AiPolicyProperties;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class AiRequestGuardTest {

  @Test
  void limitsRequestsPerUserAndFeature() {
    AtomicLong now = new AtomicLong(1_000L);
    AiRequestGuard guard = new AiRequestGuard(properties(2, 5), now::get);

    guard.checkRateLimit(1L, AiFeature.PLANT_CHAT);
    guard.checkRateLimit(1L, AiFeature.PLANT_CHAT);

    assertThatThrownBy(() -> guard.checkRateLimit(1L, AiFeature.PLANT_CHAT))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> {
              assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMON_RATE_LIMITED);
              assertThat(exception.getDetails()).containsEntry("retryAfterSeconds", 60L);
            });
    assertThatCode(() -> guard.checkRateLimit(1L, AiFeature.PLANT_RECOMMENDATION))
        .doesNotThrowAnyException();
    assertThatCode(() -> guard.checkRateLimit(2L, AiFeature.PLANT_CHAT)).doesNotThrowAnyException();
  }

  @Test
  void resetsLimitAfterWindowExpires() {
    AtomicLong now = new AtomicLong(1_000L);
    AiRequestGuard guard = new AiRequestGuard(properties(1, 5), now::get);

    guard.checkRateLimit(1L, AiFeature.PLANT_CHAT);
    now.addAndGet(Duration.ofMinutes(1).toMillis());

    assertThatCode(() -> guard.checkRateLimit(1L, AiFeature.PLANT_CHAT)).doesNotThrowAnyException();
  }

  @Test
  void enforcesRateLimitAtomicallyForConcurrentRequests() throws Exception {
    AiRequestGuard guard = new AiRequestGuard(properties(5, 5), () -> 1_000L);
    ExecutorService executor = Executors.newFixedThreadPool(10);
    List<Callable<Boolean>> calls =
        java.util.stream.IntStream.range(0, 20)
            .mapToObj(
                index ->
                    (Callable<Boolean>)
                        () -> {
                          try {
                            guard.checkRateLimit(1L, AiFeature.PLANT_CHAT);
                            return true;
                          } catch (BusinessException exception) {
                            return false;
                          }
                        })
            .toList();

    try {
      long allowed =
          executor.invokeAll(calls).stream()
              .map(
                  future -> {
                    try {
                      return future.get();
                    } catch (Exception exception) {
                      throw new AssertionError(exception);
                    }
                  })
              .filter(Boolean::booleanValue)
              .count();
      assertThat(allowed).isEqualTo(5);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void validatesBlankAndOversizedUserInput() {
    AiRequestGuard guard = new AiRequestGuard(properties(2, 5), () -> 1_000L);

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

  private AiPolicyProperties properties(int maxRequests, int maxInputLength) {
    return new AiPolicyProperties(
        maxInputLength, new AiPolicyProperties.RateLimit(maxRequests, Duration.ofMinutes(1)));
  }
}
