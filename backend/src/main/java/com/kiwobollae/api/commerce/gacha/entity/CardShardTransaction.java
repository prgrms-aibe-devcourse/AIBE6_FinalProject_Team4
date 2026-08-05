package com.kiwobollae.api.commerce.gacha.entity;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.commerce.gacha.entity.enums.CardShardTransactionType;
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
    name = "card_shard_transactions",
    indexes = {
      @Index(
          name = "uk_card_shard_transactions_request_line",
          columnList = "user_id, request_id, line_no",
          unique = true),
      @Index(name = "idx_card_shard_transactions_user_created", columnList = "user_id, created_at")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CardShardTransaction extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(name = "transaction_type", nullable = false, length = 30)
  private CardShardTransactionType transactionType;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "card_id")
  private TradingCard card;

  @Column(name = "cosmetic_code", length = 100)
  private String cosmeticCode;

  @Column(name = "card_quantity")
  private Integer cardQuantity;

  @Column(name = "shard_per_card_snapshot")
  private Long shardPerCardSnapshot;

  @Column(nullable = false)
  private Long amount;

  @Column(name = "balance_after", nullable = false)
  private Long balanceAfter;

  @Column(name = "request_id", nullable = false)
  private Long requestId;

  @Column(name = "line_no", nullable = false)
  private Integer lineNo;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;
}
