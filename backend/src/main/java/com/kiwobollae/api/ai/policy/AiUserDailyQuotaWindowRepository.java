package com.kiwobollae.api.ai.policy;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiUserDailyQuotaWindowRepository
    extends JpaRepository<AiUserDailyQuotaWindow, Long> {

  /**
   * 사용자 창을 아직 없을 때만 만든다. 이미 있으면 아무것도 하지 않는다.
   *
   * <p>{@link AiRateLimitWindowRepository#insertWindowIfAbsent}와 같은 이유다 — 행이 없는 상태에서 조건부 UPDATE를 먼저
   * 실행하면 InnoDB가 갭 락을 잡고 뒤이은 INSERT의 insert-intention 락과 서로 대기해 동시 요청이 데드락에 빠진다. 행을 먼저 만들어 두면
   * UPDATE가 항상 실제 행을 잡으므로 행 락으로 직렬화만 된다.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      nativeQuery = true,
      value =
          """
          INSERT IGNORE INTO ai_user_daily_quota_windows (user_id, window_started_at, consumed)
          VALUES (:userId, :now, 0)
          """)
  void insertWindowIfAbsent(@Param("userId") Long userId, @Param("now") LocalDateTime now);

  /**
   * 한 번의 조건부 UPDATE로 "창을 새로 열거나" "현재 창에서 한 칸 소비한다".
   *
   * @return 1이면 소비 성공, 0이면 행이 없거나 오늘 분량을 다 쓴 상태
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      UPDATE AiUserDailyQuotaWindow w
      SET w.consumed =
              CASE WHEN w.windowStartedAt <= :windowFloor THEN 1 ELSE w.consumed + 1 END,
          w.windowStartedAt =
              CASE WHEN w.windowStartedAt <= :windowFloor THEN :now ELSE w.windowStartedAt END
      WHERE w.userId = :userId
        AND (w.windowStartedAt <= :windowFloor OR w.consumed < :maxRequests)
      """)
  int consumeIfAllowed(
      @Param("userId") Long userId,
      @Param("now") LocalDateTime now,
      @Param("windowFloor") LocalDateTime windowFloor,
      @Param("maxRequests") int maxRequests);
}
