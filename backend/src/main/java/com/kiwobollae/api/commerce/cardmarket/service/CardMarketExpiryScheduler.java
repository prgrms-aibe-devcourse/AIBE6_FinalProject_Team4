package com.kiwobollae.api.commerce.cardmarket.service;

import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketListingStatus;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketNegotiationStatus;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketListingRepository;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketNegotiationRepository;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CardMarketExpiryScheduler {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final CardMarketListingRepository listingRepository;
  private final CardMarketNegotiationRepository negotiationRepository;
  private final CardMarketExpiryProcessor processor;
  private final Clock seoulClock;

  @Value("${card-market.expiry.batch-size:100}")
  private int batchSize;

  @Scheduled(fixedDelayString = "${card-market.expiry.fixed-delay-ms:60000}")
  public void expireDueResources() {
    LocalDateTime now = LocalDateTime.ofInstant(seoulClock.instant(), KST);
    int effectiveBatchSize = Math.max(1, Math.min(batchSize, 500));
    PageRequest batch =
        PageRequest.of(0, effectiveBatchSize, Sort.by(Sort.Direction.ASC, "id"));
    negotiationRepository
        .findAllByStatusAndExpiresAtLessThanEqual(
            CardMarketNegotiationStatus.NEGOTIATING, now, batch)
        .forEach(negotiation -> processor.expireNegotiation(negotiation.getId()));
    listingRepository
        .findAllByStatusAndExpiresAtLessThanEqual(CardMarketListingStatus.OPEN, now, batch)
        .forEach(listing -> processor.expireListing(listing.getId()));
    listingRepository
        .findAllByStatusAndCard_StatusNot(
            CardMarketListingStatus.OPEN, TradingCardStatus.ACTIVE, batch)
        .forEach(listing -> processor.closeHiddenCardListing(listing.getId()));
  }
}
