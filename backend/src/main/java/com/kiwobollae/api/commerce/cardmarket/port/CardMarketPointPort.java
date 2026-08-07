package com.kiwobollae.api.commerce.cardmarket.port;

import java.util.List;

public interface CardMarketPointPort {

  Balance getBalance(Long userId);

  void reserveOffer(Long userId, long amount, Long negotiationId);

  void releaseOffer(Long userId, long amount, Long negotiationId);

  void releaseOffers(List<OfferRelease> releases);

  void settleTrade(
      Long buyerUserId,
      Long sellerUserId,
      long buyerCharge,
      long sellerReceived,
      Long tradeId,
      List<OfferRelease> releases);

  record Balance(long paidPoint, long freePoint) {}

  record OfferRelease(Long userId, long amount, Long negotiationId) {}
}
