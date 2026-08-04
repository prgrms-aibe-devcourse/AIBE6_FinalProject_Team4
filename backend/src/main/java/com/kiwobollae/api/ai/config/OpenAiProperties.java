package com.kiwobollae.api.ai.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.openai")
public record OpenAiProperties(
    String baseUrl,
    String apiKey,
    String textModel,
    String visionModel,
    Duration connectTimeout,
    Duration readTimeout,
    int maxOutputTokens) {
  public OpenAiProperties {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("OpenAI API 주소는 비어 있을 수 없습니다.");
    }
    if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()) {
      throw new IllegalArgumentException("OpenAI 연결 타임아웃은 0보다 커야 합니다.");
    }
    if (readTimeout == null || readTimeout.isZero() || readTimeout.isNegative()) {
      throw new IllegalArgumentException("OpenAI 응답 타임아웃은 0보다 커야 합니다.");
    }
    if (maxOutputTokens <= 0) {
      throw new IllegalArgumentException("OpenAI 최대 출력 토큰은 0보다 커야 합니다.");
    }
  }
}
