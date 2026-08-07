package com.kiwobollae.api.commerce.cardmarket.service;

import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketListingStatus;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketNegotiationStatus;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketListingRepository;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketNegotiationRepository;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CardMarketExpiryScheduler {

  private final CardMarketListingRepository listingRepository;
  private final CardMarketNegotiationRepository negotiationRepository;
  private final CardMarketExpiryProcessor processor;
  private final Clock seoulClock;

  @Scheduled(fixedDelayString = "${card-market.expiry.fixed-delay-ms:60000}")
  public void expireDueResources() {
    LocalDateTime now = LocalDateTime.ofInstant(seoulClock.instant(), ZoneOffset.UTC);
    negotiationRepository
        .findAllByStatusAndExpiresAtLessThanEqual(
            CardMarketNegotiationStatus.NEGOTIATING, now)
        .forEach(negotiation -> processor.expireNegotiation(negotiation.getId()));
    listingRepository
        .findAllByStatusAndExpiresAtLessThanEqual(CardMarketListingStatus.OPEN, now)
        .forEach(listing -> processor.expireListing(listing.getId()));
    listingRepository
        .findAllByStatusAndCard_StatusNot(
            CardMarketListingStatus.OPEN, TradingCardStatus.ACTIVE)
        .forEach(listing -> processor.closeHiddenCardListing(listing.getId()));
  }
}
