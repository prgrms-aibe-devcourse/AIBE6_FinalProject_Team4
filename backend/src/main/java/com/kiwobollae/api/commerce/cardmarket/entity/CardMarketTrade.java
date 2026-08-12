package com.kiwobollae.api.commerce.cardmarket.entity;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketTradeType;
import com.kiwobollae.api.commerce.gacha.entity.GoldenCardInstance;
import com.kiwobollae.api.commerce.gacha.entity.TradingCard;
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
    name = "card_market_trades",
    indexes = {
      @Index(name = "uk_card_market_trades_listing", columnList = "listing_id", unique = true),
      @Index(name = "uk_card_market_trades_negotiation", columnList = "negotiation_id", unique = true),
      @Index(name = "idx_card_market_trades_buyer_completed", columnList = "buyer_user_id, completed_at, id"),
      @Index(name = "idx_card_market_trades_seller_completed", columnList = "seller_user_id, completed_at, id"),
      @Index(name = "idx_card_market_trades_card_completed", columnList = "card_id, completed_at, id")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CardMarketTrade extends BaseEntity {

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "listing_id", nullable = false)
  private CardMarketListing listing;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "negotiation_id")
  private CardMarketNegotiation negotiation;

  @Enumerated(EnumType.STRING)
  @Column(name = "trade_type", nullable = false, length = 20)
  private CardMarketTradeType tradeType;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "seller_user_id", nullable = false)
  private User seller;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "buyer_user_id", nullable = false)
  private User buyer;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "card_id", nullable = false)
  private TradingCard card;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "golden_instance_id")
  private GoldenCardInstance goldenInstance;

  @Column(name = "card_code_snapshot", nullable = false, length = 100)
  private String cardCodeSnapshot;

  @Column(name = "card_name_snapshot", nullable = false, length = 100)
  private String cardNameSnapshot;

  @Enumerated(EnumType.STRING)
  @Column(name = "rarity_snapshot", nullable = false, length = 20)
  private TradingCardRarity raritySnapshot;

  @Column(name = "image_key_snapshot", length = 500)
  private String imageKeySnapshot;

  @Column(name = "asking_price_snapshot", nullable = false)
  private Long askingPriceSnapshot;

  @Column(name = "trade_price", nullable = false)
  private Long tradePrice;

  @Column(name = "fee_rate_bps", nullable = false)
  private Integer feeRateBps;

  @Column(name = "fee_point", nullable = false)
  private Long feePoint;

  @Column(name = "seller_received_point", nullable = false)
  private Long sellerReceivedPoint;

  @Column(name = "completed_at", nullable = false, updatable = false)
  private LocalDateTime completedAt;
}
