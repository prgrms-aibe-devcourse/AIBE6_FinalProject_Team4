package com.kiwobollae.api.commerce.gacha.entity;

import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;
import com.kiwobollae.api.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
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
    name = "gacha_draw_items",
    indexes = {
      @Index(
          name = "uk_gacha_draw_items_sequence",
          columnList = "gacha_draw_id, draw_seq",
          unique = true),
      @Index(
          name = "uk_gacha_draw_items_golden_instance",
          columnList = "golden_instance_id",
          unique = true),
      @Index(name = "idx_gacha_draw_items_card_created", columnList = "card_id, created_at")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class GachaDrawItem extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "gacha_draw_id", nullable = false)
  private GachaDraw gachaDraw;

  @Column(name = "draw_seq", nullable = false)
  private Integer drawSeq;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "card_id", nullable = false)
  private TradingCard card;

  @Column(name = "roll_value", nullable = false)
  private Integer rollValue;

  @Enumerated(EnumType.STRING)
  @Column(name = "rolled_rarity", nullable = false, length = 20)
  private TradingCardRarity rolledRarity;

  @Enumerated(EnumType.STRING)
  @Column(name = "final_rarity", nullable = false, length = 20)
  private TradingCardRarity finalRarity;

  @Column(name = "owned_count_after", nullable = false)
  private Integer ownedCountAfter;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "golden_instance_id")
  private GoldenCardInstance goldenInstance;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
