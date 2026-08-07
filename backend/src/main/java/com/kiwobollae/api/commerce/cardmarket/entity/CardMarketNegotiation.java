package com.kiwobollae.api.commerce.cardmarket.entity;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketNegotiationStatus;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketParticipantType;
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
    name = "card_market_negotiations",
    indexes = {
      @Index(name = "idx_card_market_negotiations_listing_status", columnList = "listing_id, status, created_at"),
      @Index(name = "idx_card_market_negotiations_buyer_status", columnList = "buyer_user_id, status, updated_at"),
      @Index(name = "idx_card_market_negotiations_expiry", columnList = "status, expires_at")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CardMarketNegotiation extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "listing_id", nullable = false)
  private CardMarketListing listing;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "buyer_user_id", nullable = false)
  private User buyer;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private CardMarketNegotiationStatus status = CardMarketNegotiationStatus.NEGOTIATING;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private CardMarketParticipantType turn;

  @Enumerated(EnumType.STRING)
  @Column(name = "current_proposer_type", nullable = false, length = 10)
  private CardMarketParticipantType currentProposerType;

  @Column(name = "current_price", nullable = false)
  private Long currentPrice;

  @Builder.Default
  @Column(name = "escrowed_paid_point", nullable = false)
  private Long escrowedPaidPoint = 0L;

  @Builder.Default
  @Column(name = "seller_counter_count", nullable = false)
  private Integer sellerCounterCount = 0;

  @Builder.Default
  @Column(name = "next_sequence", nullable = false)
  private Integer nextSequence = 1;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Column(name = "closed_reason", length = 30)
  private String closedReason;

  @Column(name = "closed_at")
  private LocalDateTime closedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  public int takeNextSequence() {
    return nextSequence++;
  }

  public void updateProposal(
      CardMarketParticipantType proposer,
      long price,
      long escrowedPoint,
      LocalDateTime expiresAt,
      LocalDateTime now) {
    this.currentProposerType = proposer;
    this.turn = proposer == CardMarketParticipantType.BUYER
        ? CardMarketParticipantType.SELLER
        : CardMarketParticipantType.BUYER;
    this.currentPrice = price;
    this.escrowedPaidPoint = escrowedPoint;
    if (proposer == CardMarketParticipantType.SELLER) {
      this.sellerCounterCount++;
    }
    this.expiresAt = expiresAt;
    this.updatedAt = now;
  }

  public void accept(LocalDateTime now) {
    close(CardMarketNegotiationStatus.ACCEPTED, "ACCEPTED", now);
    this.escrowedPaidPoint = 0L;
  }

  public long closeAndRelease(
      CardMarketNegotiationStatus terminalStatus, String reason, LocalDateTime now) {
    long released = this.escrowedPaidPoint;
    close(terminalStatus, reason, now);
    this.escrowedPaidPoint = 0L;
    return released;
  }

  private void close(
      CardMarketNegotiationStatus terminalStatus, String reason, LocalDateTime now) {
    this.status = terminalStatus;
    this.closedReason = reason;
    this.closedAt = now;
    this.updatedAt = now;
  }
}
