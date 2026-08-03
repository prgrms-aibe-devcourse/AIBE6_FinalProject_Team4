package com.kiwobollae.api.commerce.gacha.entity;

import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "trading_cards",
    indexes = {
      @Index(name = "uk_trading_cards_code", columnList = "code", unique = true),
      @Index(
          name = "idx_trading_cards_series_status_order",
          columnList = "series_code, status, display_order"),
      @Index(name = "idx_trading_cards_rarity_status", columnList = "rarity, status")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TradingCard extends GachaTimeEntity {

  @Builder.Default
  @Column(name = "series_code", nullable = false, length = 50)
  private String seriesCode = "SEASON_01";

  @Column(nullable = false, length = 100)
  private String code;

  @Column(nullable = false, length = 100)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TradingCardRarity rarity;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "image_key", length = 500)
  private String imageKey;

  @Column(name = "draw_weight", nullable = false)
  private Integer drawWeight;

  @Column(name = "display_order", nullable = false)
  private Integer displayOrder;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TradingCardStatus status = TradingCardStatus.ACTIVE;

  public void updateSeed(
      String name,
      TradingCardRarity rarity,
      String description,
      String imageKey,
      Integer drawWeight,
      Integer displayOrder) {
    this.name = Objects.requireNonNull(name);
    this.rarity = Objects.requireNonNull(rarity);
    this.description = description;
    this.imageKey = imageKey;
    this.drawWeight = Objects.requireNonNull(drawWeight);
    this.displayOrder = Objects.requireNonNull(displayOrder);
    this.status = TradingCardStatus.ACTIVE;
  }
}
