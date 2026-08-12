package com.kiwobollae.api.commerce.cardmarket.entity;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketMessageCode;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "card_market_proposals",
    indexes = {
      @Index(
          name = "uk_card_market_proposals_sequence",
          columnList = "negotiation_id, sequence_no",
          unique = true)
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CardMarketProposal extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "negotiation_id", nullable = false)
  private CardMarketNegotiation negotiation;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "proposer_user_id", nullable = false)
  private User proposer;

  @Enumerated(EnumType.STRING)
  @Column(name = "proposer_type", nullable = false, length = 10)
  private CardMarketParticipantType proposerType;

  @Column(name = "sequence_no", nullable = false)
  private Integer sequenceNo;

  @Column(name = "proposed_price", nullable = false)
  private Long proposedPrice;

  @Enumerated(EnumType.STRING)
  @Column(name = "message_code", length = 30)
  private CardMarketMessageCode messageCode;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
