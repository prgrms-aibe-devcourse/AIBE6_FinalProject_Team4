package com.kiwobollae.api.ai.policy;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiRateLimitWindowRepository extends JpaRepository<AiRateLimitWindow, Long> {

  Optional<AiRateLimitWindow> findByUserIdAndFeature(Long userId, AiFeature feature);

  /**
   * (user, feature) 창을 아직 없을 때만 만든다. 이미 있으면 아무것도 하지 않는다.
   *
   * <p>아래 {@code consumeIfAllowed}보다 먼저 불러 행의 존재를 보장하는 용도다. 행이 없는 상태에서 조건부 UPDATE를 먼저 실행하면 InnoDB가
   * 유니크 인덱스에 갭 락을 잡고, 뒤이은 INSERT의 insert-intention 락과 서로 대기해 동시 요청이 데드락에 빠진다. 먼저 행을 만들어 두면 UPDATE가
   * 항상 실제 행을 잡으므로 행 락으로 직렬화만 된다.
   *
   * <p>{@code consumed = 0}으로 넣어 첫 호출도 일반 소비 경로를 그대로 타게 한다 — 첫 호출만 다르게 처리하는 분기가 필요 없다.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      nativeQuery = true,
      value =
          """
          INSERT IGNORE INTO ai_rate_limit_windows (user_id, feature, window_started_at, consumed)
          VALUES (:userId, :feature, :now, 0)
          """)
  void insertWindowIfAbsent(
      @Param("userId") Long userId,
      @Param("feature") String feature,
      @Param("now") LocalDateTime now);

  /**
   * 한 번의 조건부 UPDATE로 "창을 새로 열거나" "현재 창에서 한 칸 소비한다".
   *
   * <p>두 판정을 한 문장에 담는 게 핵심이다. 조회 후 갱신으로 나누면 두 인스턴스가 같은 창을 함께 읽어 한도를 초과할 수 있다.
   *
   * <p>{@code windowFloor}는 (현재 시각 - 창 길이)다. {@code windowStartedAt}이 그보다 오래됐으면 창이 만료된 것이므로 소비 수를
   * 1로 되돌리고 창을 지금으로 옮긴다. 아니면 한도 미만일 때만 1 늘린다.
   *
   * @return 1이면 소비 성공, 0이면 행이 없거나 현재 창의 한도를 다 쓴 상태
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      UPDATE AiRateLimitWindow w
      SET w.consumed =
              CASE WHEN w.windowStartedAt <= :windowFloor THEN 1 ELSE w.consumed + 1 END,
          w.windowStartedAt =
              CASE WHEN w.windowStartedAt <= :windowFloor THEN :now ELSE w.windowStartedAt END
      WHERE w.userId = :userId
        AND w.feature = :feature
        AND (w.windowStartedAt <= :windowFloor OR w.consumed < :maxRequests)
      """)
  int consumeIfAllowed(
      @Param("userId") Long userId,
      @Param("feature") AiFeature feature,
      @Param("now") LocalDateTime now,
      @Param("windowFloor") LocalDateTime windowFloor,
      @Param("maxRequests") int maxRequests);
}
