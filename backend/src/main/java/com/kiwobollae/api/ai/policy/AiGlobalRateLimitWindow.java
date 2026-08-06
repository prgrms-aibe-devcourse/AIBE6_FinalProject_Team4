package com.kiwobollae.api.ai.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 모든 AI 기능이 함께 소비하는 전역 호출 예산 창.
 *
 * <p>id=1인 행 하나만 사용한다. 사용자별 제한과 달리 이 카운터는 종 캐시가 대량 무효화되거나 서로 다른 사용자가 동시에 요청해도 외부 AI 호출의 총량을 넘지 않게
 * 한다.
 */
@Getter
@Entity
@Table(name = "ai_global_rate_limit_windows")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiGlobalRateLimitWindow {

  static final long GLOBAL_WINDOW_ID = 1L;

  @Id private Long id;

  @Column(name = "window_started_at", nullable = false)
  private LocalDateTime windowStartedAt;

  @Column(nullable = false)
  private int consumed;
}
