package com.kiwobollae.api.commerce.gacha.entity;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.commerce.gacha.entity.enums.GachaCosmeticType;
import com.kiwobollae.api.global.common.BaseEntity;
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
    name = "user_card_cosmetics",
    indexes = {
      @Index(
          name = "uk_user_card_cosmetics_user_code",
          columnList = "user_id, cosmetic_code",
          unique = true),
      @Index(name = "idx_user_card_cosmetics_user_type", columnList = "user_id, cosmetic_type")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserCardCosmetic extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "cosmetic_code", nullable = false, length = 100)
  private String cosmeticCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "cosmetic_type", nullable = false, length = 20)
  private GachaCosmeticType cosmeticType;

  @Column(name = "shard_price_snapshot", nullable = false)
  private Long shardPriceSnapshot;

  @Column(name = "unlocked_at", nullable = false)
  private LocalDateTime unlockedAt;

  @Column(name = "equipped_at")
  private LocalDateTime equippedAt;

  public void equip(LocalDateTime now) {
    equippedAt = now;
  }

  public void unequip() {
    equippedAt = null;
  }
}
