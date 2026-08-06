package com.kiwobollae.api.ai.policy;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiGlobalRateLimitWindowRepository
    extends JpaRepository<AiGlobalRateLimitWindow, Long> {

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      nativeQuery = true,
      value =
          """
          INSERT IGNORE INTO ai_global_rate_limit_windows (id, window_started_at, consumed)
          VALUES (:id, :now, 0)
          """)
  void insertWindowIfAbsent(@Param("id") Long id, @Param("now") LocalDateTime now);

  /**
   * 전역 창을 갱신하면서 호출 한 건을 예약한다.
   *
   * @return 1이면 예약 성공, 0이면 현재 창의 예산을 모두 소비한 상태
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      UPDATE AiGlobalRateLimitWindow w
      SET w.consumed =
              CASE WHEN w.windowStartedAt <= :windowFloor THEN 1 ELSE w.consumed + 1 END,
          w.windowStartedAt =
              CASE WHEN w.windowStartedAt <= :windowFloor THEN :now ELSE w.windowStartedAt END
      WHERE w.id = :id
        AND (w.windowStartedAt <= :windowFloor OR w.consumed < :maxRequests)
      """)
  int consumeIfAllowed(
      @Param("id") Long id,
      @Param("now") LocalDateTime now,
      @Param("windowFloor") LocalDateTime windowFloor,
      @Param("maxRequests") int maxRequests);
}
