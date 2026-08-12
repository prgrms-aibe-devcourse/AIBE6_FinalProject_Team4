package com.kiwobollae.api.commerce.gacha.entity;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.commerce.gacha.entity.enums.GoldenOriginType;
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
    name = "golden_card_instances",
    indexes = {
      @Index(
          name = "uk_golden_card_instances_rank",
          columnList = "card_id, golden_origin_rank",
          unique = true),
      @Index(name = "idx_golden_card_instances_owner_card", columnList = "owner_user_id, card_id"),
      @Index(name = "idx_golden_card_instances_origin", columnList = "origin_user_id, card_id")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class GoldenCardInstance extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "card_id", nullable = false)
  private TradingCard card;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_user_id", nullable = false)
  private User ownerUser;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "origin_user_id", nullable = false)
  private User originUser;

  @Enumerated(EnumType.STRING)
  @Column(name = "origin_type", nullable = false, length = 20)
  private GoldenOriginType originType;

  @Column(name = "golden_origin_rank")
  private Long goldenOriginRank;

  @Column(name = "origin_acquired_at", nullable = false)
  private LocalDateTime originAcquiredAt;

  @Column(name = "current_owner_since", nullable = false)
  private LocalDateTime currentOwnerSince;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  public void transferTo(User newOwner, LocalDateTime now) {
    this.ownerUser = newOwner;
    this.currentOwnerSince = now;
  }
}
