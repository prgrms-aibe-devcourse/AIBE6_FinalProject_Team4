package com.kiwobollae.api.ai.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 호출 정책. 제한이 세 겹인 이유는 각각 막는 것이 다르기 때문이다.
 *
 * <ul>
 *   <li>{@code rateLimit} — (사용자, 기능)별 짧은 창. 순간 폭주를 막는다.
 *   <li>{@code userDailyLimit} — 사용자별 하루 총량(기능 합산). 계정 하나가 전역 예산을 통째로 소진해 다른 사용자의 AI 기능을 멈추는 것을
 *       막는다. 이 항목이 없으면 짧은 창을 계속 채우는 것만으로 그렇게 할 수 있다.
 *   <li>{@code globalRateLimit} — 전체 비용 상한. 마지막 안전장치다.
 * </ul>
 */
@ConfigurationProperties(prefix = "ai.policy")
public record AiPolicyProperties(
    int maxInputLength, RateLimit rateLimit, RateLimit userDailyLimit, RateLimit globalRateLimit) {
  public AiPolicyProperties {
    if (maxInputLength <= 0) {
      throw new IllegalArgumentException("AI 입력 최대 길이는 0보다 커야 합니다.");
    }
    if (rateLimit == null) {
      throw new IllegalArgumentException("AI 호출 제한 설정이 필요합니다.");
    }
    if (userDailyLimit == null) {
      throw new IllegalArgumentException("AI 사용자별 일일 호출 제한 설정이 필요합니다.");
    }
    if (globalRateLimit == null) {
      throw new IllegalArgumentException("AI 전역 호출 제한 설정이 필요합니다.");
    }
  }

  public record RateLimit(int maxRequests, Duration window) {
    public RateLimit {
      if (maxRequests <= 0) {
        throw new IllegalArgumentException("AI 호출 제한 횟수는 0보다 커야 합니다.");
      }
      if (window == null || window.isZero() || window.isNegative()) {
        throw new IllegalArgumentException("AI 호출 제한 시간은 0보다 커야 합니다.");
      }
    }
  }
}
