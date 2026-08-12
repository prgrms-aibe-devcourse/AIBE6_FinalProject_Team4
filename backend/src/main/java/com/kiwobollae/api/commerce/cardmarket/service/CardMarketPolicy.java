package com.kiwobollae.api.commerce.cardmarket.service;

import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.time.LocalDateTime;

final class CardMarketPolicy {

  static final long MIN_PRICE = 100L;
  static final long MAX_PRICE = 99_999_999L;
  static final int FEE_RATE_BPS = 2_000;

  private CardMarketPolicy() {}

  static long requirePrice(Long price) {
    if (price == null || price < MIN_PRICE || price > MAX_PRICE) {
      throw new BusinessException(ErrorCode.CARD_MARKET_PRICE_INVALID);
    }
    return price;
  }

  static long fee(long tradePrice) {
    requirePrice(tradePrice);
    return Math.multiplyExact(tradePrice, FEE_RATE_BPS) / 10_000L;
  }

  static LocalDateTime listingExpiresAt(LocalDateTime now) {
    return now.plusDays(7);
  }

  static LocalDateTime negotiationExpiresAt(
      LocalDateTime listingExpiresAt, LocalDateTime now) {
    LocalDateTime offerExpiry = now.plusHours(24);
    return offerExpiry.isBefore(listingExpiresAt) ? offerExpiry : listingExpiresAt;
  }
}
