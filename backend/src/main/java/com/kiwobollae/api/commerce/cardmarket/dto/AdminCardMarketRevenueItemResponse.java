package com.kiwobollae.api.commerce.cardmarket.dto;

import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketTrade;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketTradeType;
import java.time.LocalDateTime;

public record AdminCardMarketRevenueItemResponse(
    Long tradeId,
    Long listingId,
    String cardName,
    CardMarketTradeType tradeType,
    Long sellerUserId,
    String sellerNickname,
    Long buyerUserId,
    String buyerNickname,
    Long tradePoint,
    Long feePoint,
    Long sellerReceivedPoint,
    LocalDateTime completedAt) {

  public static AdminCardMarketRevenueItemResponse from(CardMarketTrade trade) {
    return new AdminCardMarketRevenueItemResponse(
        trade.getId(),
        trade.getListing().getId(),
        trade.getCardNameSnapshot(),
        trade.getTradeType(),
        trade.getSeller().getId(),
        trade.getSeller().getNickname(),
        trade.getBuyer().getId(),
        trade.getBuyer().getNickname(),
        trade.getTradePrice(),
        trade.getFeePoint(),
        trade.getSellerReceivedPoint(),
        trade.getCompletedAt());
  }
}
