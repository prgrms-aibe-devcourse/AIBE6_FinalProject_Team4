package com.kiwobollae.api.commerce.cardmarket.dto;

import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketTrade;
import java.util.List;
import org.springframework.data.domain.Page;

public record AdminCardMarketRevenueResponse(
    long totalTradeCount,
    long totalTradePoint,
    long totalFeePoint,
    long totalSellerReceivedPoint,
    List<AdminCardMarketRevenueItemResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages) {

  public static AdminCardMarketRevenueResponse from(
      Page<CardMarketTrade> trades,
      long totalTradeCount,
      long totalTradePoint,
      long totalFeePoint,
      long totalSellerReceivedPoint) {
    return new AdminCardMarketRevenueResponse(
        totalTradeCount,
        totalTradePoint,
        totalFeePoint,
        totalSellerReceivedPoint,
        trades.getContent().stream().map(AdminCardMarketRevenueItemResponse::from).toList(),
        trades.getNumber(),
        trades.getSize(),
        trades.getTotalElements(),
        trades.getTotalPages());
  }
}
