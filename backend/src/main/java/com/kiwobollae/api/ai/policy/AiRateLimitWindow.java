package com.kiwobollae.api.ai.policy;

import com.kiwobollae.api.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자·기능별 AI 호출 제한 창.
 *
 * <p>이전에는 JVM 힙(ConcurrentHashMap)에 두었는데, Blue/Green 배포가 컨테이너를 교체하므로 배포할 때마다 모든 카운터가 0으로 리셋됐다. 교체 중
 * 두 인스턴스가 함께 떠 있는 동안에는 인스턴스마다 카운터가 따로 있어 실효 한도가 두 배가 되기도 했다. 그래서 DB에 두고 조건부 UPDATE로 원자적으로 소비한다.
 *
 * <p>행은 (user_id, feature) 조합마다 하나만 만들어 재사용하므로 테이블 크기는 사용자 수 × 기능 수로 묶인다 — 별도 정리 배치가 필요 없다.
 *
 * <p><b>users에 FK를 걸지 않는다.</b> 호출부 트랜잭션이 무엇을 잠그고 있든 별도 트랜잭션에서 이 행을 갱신해야 하는데, FK가 있으면 부모 행 공유 락을
 * 기다리다 락 타임아웃이 날 수 있다. 운영 카운터라 참조 무결성보다 "항상 셀 수 있다"가 중요하다.
 */
@Getter
@Entity
@Table(
    name = "ai_rate_limit_windows",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_ai_rate_limit_window_user_feature",
          columnNames = {"user_id", "feature"})
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AiRateLimitWindow extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private Long userId;

  // columnDefinition으로 varchar를 못박는다. 지정하지 않으면 Hibernate가 Java enum을 MySQL ENUM
  // 컬럼으로 만들고, 그러면 AiFeature에 값을 추가할 때마다 ALTER TABLE이 필요하다. 운영은
  // ddl-auto: update라 ENUM 정의를 갱신해 주지 않아, ALTER를 잊으면 새 값 저장이 런타임에 실패한다.
  // 값 검증은 Java enum 파싱이 하므로 DB 제약이 없어도 잘못된 값이 들어갈 경로는 없다.
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30, columnDefinition = "varchar(30)")
  private AiFeature feature;

  /** 현재 창이 시작된 시각. 이 시각 + 창 길이가 지나면 다음 소비에서 창이 새로 열린다. */
  @Column(name = "window_started_at", nullable = false)
  private LocalDateTime windowStartedAt;

  /** 현재 창에서 소비된 호출 수. */
  @Column(nullable = false)
  private int consumed;
}
