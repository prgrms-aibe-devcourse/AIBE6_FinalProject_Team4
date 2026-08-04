package com.kiwobollae.api.ai.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.policy")
public record AiPolicyProperties(int maxInputLength, RateLimit rateLimit) {
  public AiPolicyProperties {
    if (maxInputLength <= 0) {
      throw new IllegalArgumentException("AI 입력 최대 길이는 0보다 커야 합니다.");
    }
    if (rateLimit == null) {
      throw new IllegalArgumentException("AI 호출 제한 설정이 필요합니다.");
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
