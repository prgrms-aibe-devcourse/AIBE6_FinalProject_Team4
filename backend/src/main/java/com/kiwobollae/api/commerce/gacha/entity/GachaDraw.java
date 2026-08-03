package com.kiwobollae.api.commerce.gacha.entity;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.commerce.gacha.entity.enums.GachaDrawStatus;
import com.kiwobollae.api.commerce.gacha.entity.enums.GachaSourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "gacha_draws",
    indexes = {
      @Index(name = "uk_gacha_draws_source", columnList = "source_type, source_id", unique = true),
      @Index(name = "idx_gacha_draws_user_created", columnList = "user_id, created_at"),
      @Index(name = "idx_gacha_draws_retry", columnList = "status, next_retry_at")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class GachaDraw extends GachaTimeEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 20)
  private GachaSourceType sourceType;

  @Column(name = "source_id", nullable = false)
  private Long sourceId;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private GachaDrawStatus status = GachaDrawStatus.PENDING;

  @Builder.Default
  @Column(name = "draw_count", nullable = false)
  private Integer drawCount = 5;

  @Builder.Default
  @Column(name = "rate_version", nullable = false)
  private Integer rateVersion = 1;

  @Column(name = "result_viewed_at")
  private LocalDateTime resultViewedAt;

  @Builder.Default
  @Column(name = "attempt_count", nullable = false)
  private Integer attemptCount = 0;

  @Column(name = "next_retry_at")
  private LocalDateTime nextRetryAt;

  @Column(name = "last_error_code", length = 100)
  private String lastErrorCode;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  public void complete(LocalDateTime now) {
    status = GachaDrawStatus.COMPLETED;
    completedAt = now;
    nextRetryAt = null;
    lastErrorCode = null;
  }

  public void markRetryable(String errorCode, LocalDateTime nextRetryAt) {
    attemptCount += 1;
    status = attemptCount >= 4 ? GachaDrawStatus.MANUAL_REVIEW : GachaDrawStatus.RETRYABLE_FAILED;
    this.nextRetryAt = status == GachaDrawStatus.RETRYABLE_FAILED ? nextRetryAt : null;
    lastErrorCode = errorCode;
  }

  public void refund(String errorCode) {
    status = GachaDrawStatus.REFUNDED;
    nextRetryAt = null;
    lastErrorCode = errorCode;
  }
}
