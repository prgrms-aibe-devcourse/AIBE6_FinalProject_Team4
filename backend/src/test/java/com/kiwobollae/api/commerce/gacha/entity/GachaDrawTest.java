package com.kiwobollae.api.commerce.gacha.entity;

import static com.kiwobollae.api.commerce.gacha.GachaTimeZone.KST;
import static org.assertj.core.api.Assertions.assertThat;

import com.kiwobollae.api.commerce.gacha.entity.enums.GachaDrawStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class GachaDrawTest {

  @Test
  void auditTimestampsUseKst() {
    GachaDraw draw = GachaDraw.builder().build();
    LocalDateTime before = LocalDateTime.now(KST);

    draw.onCreate();

    LocalDateTime after = LocalDateTime.now(KST);
    assertThat(draw.getCreatedAt()).isBetween(before, after);
    assertThat(draw.getUpdatedAt()).isEqualTo(draw.getCreatedAt());
  }

  @Test
  void movesToManualReviewAfterInitialAttemptAndThreeRetriesFail() {
    GachaDraw draw = GachaDraw.builder().status(GachaDrawStatus.PENDING).attemptCount(0).build();
    LocalDateTime retryAt = LocalDateTime.of(2026, 7, 30, 0, 0);

    draw.markRetryable("FAIL_1", retryAt);
    assertThat(draw.getStatus()).isEqualTo(GachaDrawStatus.RETRYABLE_FAILED);
    draw.markRetryable("FAIL_2", retryAt);
    assertThat(draw.getStatus()).isEqualTo(GachaDrawStatus.RETRYABLE_FAILED);
    draw.markRetryable("FAIL_3", retryAt);
    assertThat(draw.getStatus()).isEqualTo(GachaDrawStatus.RETRYABLE_FAILED);
    draw.markRetryable("FAIL_4", retryAt);

    assertThat(draw.getStatus()).isEqualTo(GachaDrawStatus.MANUAL_REVIEW);
    assertThat(draw.getAttemptCount()).isEqualTo(4);
    assertThat(draw.getNextRetryAt()).isNull();
    assertThat(draw.getLastErrorCode()).isEqualTo("FAIL_4");
  }
}
