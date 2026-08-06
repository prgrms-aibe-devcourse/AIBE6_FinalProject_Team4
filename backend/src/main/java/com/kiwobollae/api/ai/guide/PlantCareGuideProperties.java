package com.kiwobollae.api.ai.guide;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 재배 가이드 생성 선점 lease의 운영 설정. */
@ConfigurationProperties(prefix = "ai.plant-care-guide")
public record PlantCareGuideProperties(Duration generationLockLease) {

  public PlantCareGuideProperties {
    if (generationLockLease == null
        || generationLockLease.isZero()
        || generationLockLease.isNegative()) {
      throw new IllegalArgumentException("재배 가이드 생성 lease 시간은 0보다 커야 합니다.");
    }
  }
}
