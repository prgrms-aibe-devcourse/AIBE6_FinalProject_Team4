package com.kiwobollae.api.commerce.cardmarket.dto;

import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketTrade;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketTradeType;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;
import java.time.LocalDateTime;

public record CardMarketTradeResponse(
    Long id,
    Long listingId,
    Long sellerUserId,
    Long buyerUserId,
    Long cardId,
    Long goldenInstanceId,
    String cardCode,
    String cardName,
    TradingCardRarity rarity,
    String imageUrl,
    CardMarketTradeType tradeType,
    Long askingPrice,
    Long tradePrice,
    Integer feeRateBps,
    Long feePoint,
    Long sellerReceivedPoint,
    LocalDateTime completedAt) {

  public static CardMarketTradeResponse from(CardMarketTrade trade, String imageUrl) {
    return new CardMarketTradeResponse(
        trade.getId(),
        trade.getListing().getId(),
        trade.getSeller().getId(),
        trade.getBuyer().getId(),
        trade.getCard().getId(),
        trade.getGoldenInstance() == null ? null : trade.getGoldenInstance().getId(),
        trade.getCardCodeSnapshot(),
        trade.getCardNameSnapshot(),
        trade.getRaritySnapshot(),
        imageUrl,
        trade.getTradeType(),
        trade.getAskingPriceSnapshot(),
        trade.getTradePrice(),
        trade.getFeeRateBps(),
        trade.getFeePoint(),
        trade.getSellerReceivedPoint(),
        trade.getCompletedAt());
  }
}
