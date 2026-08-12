package com.kiwobollae.api.commerce.cardmarket.service;

import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketListing;
import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketNegotiation;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketAssetType;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketListingStatus;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketNegotiationStatus;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketListingRepository;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketNegotiationRepository;
import com.kiwobollae.api.commerce.cardmarket.port.CardMarketPointPort;
import com.kiwobollae.api.commerce.gacha.repository.UserCardCollectionRepository;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CardMarketExpiryProcessor {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final CardMarketListingRepository listingRepository;
  private final CardMarketNegotiationRepository negotiationRepository;
  private final UserCardCollectionRepository collectionRepository;
  private final CardMarketPointPort pointPort;
  private final CardMarketNotificationService notificationService;
  private final Clock seoulClock;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void expireListing(Long listingId) {
    CardMarketListing listing = listingRepository.findByIdForUpdate(listingId).orElse(null);
    LocalDateTime now = LocalDateTime.ofInstant(seoulClock.instant(), KST);
    if (listing == null
        || listing.getStatus() != CardMarketListingStatus.OPEN
        || listing.getExpiresAt().isAfter(now)) {
      return;
    }
    List<CardMarketPointPort.OfferRelease> releases = new ArrayList<>();
    negotiationRepository
        .findAllByListingIdAndStatusForUpdate(
            listingId, CardMarketNegotiationStatus.NEGOTIATING)
        .stream()
        .sorted(Comparator.comparing(item -> item.getBuyer().getId()))
        .forEach(
            negotiation -> {
              long amount =
                  negotiation.closeAndRelease(
                      CardMarketNegotiationStatus.LISTING_CLOSED,
                      "LISTING_EXPIRED",
                      now);
              if (amount > 0) {
                releases.add(
                    new CardMarketPointPort.OfferRelease(
                        negotiation.getBuyer().getId(), amount, negotiation.getId()));
              }
              notificationService.negotiationClosed(negotiation, "LISTING_EXPIRED");
            });
    pointPort.releaseOffers(releases);
    if (listing.getAssetType() == CardMarketAssetType.HYPER_RARE) {
      collectionRepository.incrementOwnedCount(
          listing.getSeller().getId(), listing.getCard().getId(), now);
    }
    listing.expire(now);
    notificationService.listingExpired(listing);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void closeHiddenCardListing(Long listingId) {
    CardMarketListing listing = listingRepository.findByIdForUpdate(listingId).orElse(null);
    LocalDateTime now = LocalDateTime.ofInstant(seoulClock.instant(), KST);
    if (listing == null
        || listing.getStatus() != CardMarketListingStatus.OPEN
        || listing.getCard().getStatus() == TradingCardStatus.ACTIVE) {
      return;
    }
    List<CardMarketPointPort.OfferRelease> releases = new ArrayList<>();
    negotiationRepository
        .findAllByListingIdAndStatusForUpdate(
            listingId, CardMarketNegotiationStatus.NEGOTIATING)
        .stream()
        .sorted(Comparator.comparing(item -> item.getBuyer().getId()))
        .forEach(
            negotiation -> {
              long amount =
                  negotiation.closeAndRelease(
                      CardMarketNegotiationStatus.LISTING_CLOSED, "CARD_HIDDEN", now);
              if (amount > 0) {
                releases.add(
                    new CardMarketPointPort.OfferRelease(
                        negotiation.getBuyer().getId(), amount, negotiation.getId()));
              }
              notificationService.negotiationClosed(negotiation, "CARD_HIDDEN");
            });
    pointPort.releaseOffers(releases);
    if (listing.getAssetType() == CardMarketAssetType.HYPER_RARE) {
      collectionRepository.incrementOwnedCount(
          listing.getSeller().getId(), listing.getCard().getId(), now);
    }
    listing.cancel("CARD_HIDDEN", now);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void expireNegotiation(Long negotiationId) {
    CardMarketNegotiation snapshot = negotiationRepository.findById(negotiationId).orElse(null);
    if (snapshot == null) {
      return;
    }
    listingRepository.findByIdForUpdate(snapshot.getListing().getId()).orElse(null);
    CardMarketNegotiation negotiation =
        negotiationRepository.findByIdForUpdate(negotiationId).orElse(null);
    LocalDateTime now = LocalDateTime.ofInstant(seoulClock.instant(), KST);
    if (negotiation == null
        || negotiation.getStatus() != CardMarketNegotiationStatus.NEGOTIATING
        || negotiation.getExpiresAt().isAfter(now)) {
      return;
    }
    long amount =
        negotiation.closeAndRelease(CardMarketNegotiationStatus.EXPIRED, "EXPIRED", now);
    if (amount > 0) {
      pointPort.releaseOffer(
          negotiation.getBuyer().getId(), amount, negotiation.getId());
    }
    notificationService.negotiationExpired(negotiation);
  }
}
