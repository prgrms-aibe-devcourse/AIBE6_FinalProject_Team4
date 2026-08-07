package com.kiwobollae.api.commerce.cardmarket.entity;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketAssetType;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketListingStatus;
import com.kiwobollae.api.commerce.gacha.entity.GoldenCardInstance;
import com.kiwobollae.api.commerce.gacha.entity.TradingCard;
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
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "card_market_listings",
    indexes = {
      @Index(name = "idx_card_market_listings_status_created", columnList = "status, created_at, id"),
      @Index(name = "idx_card_market_listings_status_price", columnList = "status, asking_price, id"),
      @Index(name = "idx_card_market_listings_card_status_price", columnList = "card_id, status, asking_price, id"),
      @Index(name = "idx_card_market_listings_seller_status", columnList = "seller_user_id, status, created_at, id"),
      @Index(name = "idx_card_market_listings_expiry", columnList = "status, expires_at")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CardMarketListing extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "seller_user_id", nullable = false)
  private User seller;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "card_id", nullable = false)
  private TradingCard card;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "golden_instance_id")
  private GoldenCardInstance goldenInstance;

  @Enumerated(EnumType.STRING)
  @Column(name = "asset_type", nullable = false, length = 20)
  private CardMarketAssetType assetType;

  @Column(name = "asking_price", nullable = false)
  private Long askingPrice;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private CardMarketListingStatus status = CardMarketListingStatus.OPEN;

  @Column(name = "closed_reason", length = 30)
  private String closedReason;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Column(name = "sold_at")
  private LocalDateTime soldAt;

  @Column(name = "cancelled_at")
  private LocalDateTime cancelledAt;

  @Version
  @Column(nullable = false)
  private Long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  public void markSold(String reason, LocalDateTime now) {
    this.status = CardMarketListingStatus.SOLD;
    this.closedReason = reason;
    this.soldAt = now;
    this.updatedAt = now;
  }

  public void cancel(String reason, LocalDateTime now) {
    this.status = CardMarketListingStatus.CANCELLED;
    this.closedReason = reason;
    this.cancelledAt = now;
    this.updatedAt = now;
  }

  public void expire(LocalDateTime now) {
    this.status = CardMarketListingStatus.EXPIRED;
    this.closedReason = "EXPIRED";
    this.updatedAt = now;
  }
}
