package com.kiwobollae.api.commerce.gacha.entity;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
    name = "user_card_collections",
    indexes = {
      @Index(
          name = "uk_user_card_collections_user_card",
          columnList = "user_id, card_id",
          unique = true),
      @Index(name = "idx_user_card_collections_card", columnList = "card_id")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserCardCollection extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "card_id", nullable = false)
  private TradingCard card;

  @Builder.Default
  @Column(name = "owned_count", nullable = false)
  private Integer ownedCount = 0;

  @Column(name = "first_acquired_at", nullable = false)
  private LocalDateTime firstAcquiredAt;

  @Column(name = "golden_gacha_acquired_at")
  private LocalDateTime goldenGachaAcquiredAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  public int acquireGolden(LocalDateTime now) {
    if (goldenGachaAcquiredAt != null) {
      return 0;
    }
    goldenGachaAcquiredAt = now;
    ownedCount += 1;
    updatedAt = now;
    return ownedCount;
  }
}
