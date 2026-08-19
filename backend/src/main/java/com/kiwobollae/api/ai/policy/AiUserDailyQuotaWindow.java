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
 * 사용자 한 명이 하루에 소비할 수 있는 AI 호출 총량 창.
 *
 * <p><b>왜 필요한가.</b> 기존 제한은 (user, feature)별 짧은 창({@link AiRateLimitWindow})과 모두가 공유하는 전역 창({@link
 * AiGlobalRateLimitWindow}) 둘뿐이었다. 짧은 창은 순간 폭주만 막고 하루 총량은 재우지 못하므로, 계정 하나가 분당 한도를 계속 채우는 것만으로 전역
 * 예산을 통째로 소진할 수 있었다. 그러면 남용한 본인이 아니라 <b>다른 모든 사용자의 AI 기능이 그날 내내 멈춘다</b>. 이 창은 그 사이를 메워, 한 계정의 손해가 그
 * 계정 안에서 끝나게 한다.
 *
 * <p><b>기능을 나누지 않고 합산한다.</b> {@link AiRateLimitWindow}와 달리 feature 컬럼이 없다. 기능별로 따로 세면 사용자가 기능을 번갈아
 * 호출해 한도를 기능 수만큼 곱해 쓸 수 있고, 그러면 전역 예산을 지킨다는 목적이 그대로 무너진다. 전역 예산이 기능을 구분하지 않으므로 이 창도 구분하지 않는다.
 *
 * <p>행은 사용자마다 하나만 만들어 재사용한다 — 창이 만료되면 같은 행의 카운터를 되돌릴 뿐이라 정리 배치가 필요 없다.
 *
 * <p>{@link AiRateLimitWindow}와 같은 이유로 <b>users에 FK를 걸지 않는다.</b> 별도 트랜잭션에서 갱신하는 운영 카운터라, 부모 행 공유 락을
 * 기다리다 락 타임아웃이 나는 쪽이 참조 무결성보다 위험하다.
 */
@Getter
@Entity
@Table(name = "ai_user_daily_quota_windows")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiUserDailyQuotaWindow {

  /** 사용자 식별자를 그대로 PK로 쓴다. 사용자당 행이 하나뿐이라 별도 대리키가 필요 없다. */
  @Id
  @Column(name = "user_id")
  private Long userId;

  /** 현재 창이 시작된 시각. 이 시각 + 창 길이가 지나면 다음 소비에서 창이 새로 열린다. */
  @Column(name = "window_started_at", nullable = false)
  private LocalDateTime windowStartedAt;

  /** 현재 창에서 소비된 호출 수. 기능 구분 없이 합산한 값이다. */
  @Column(nullable = false)
  private int consumed;
}
