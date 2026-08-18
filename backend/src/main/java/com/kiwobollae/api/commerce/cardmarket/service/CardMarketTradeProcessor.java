package com.kiwobollae.api.commerce.cardmarket.service;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketListing;
import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketNegotiation;
import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketTrade;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketAssetType;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketNegotiationStatus;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketTradeType;
import com.kiwobollae.api.commerce.cardmarket.port.CardMarketPointPort;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketNegotiationRepository;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketTradeRepository;
import com.kiwobollae.api.commerce.gacha.entity.GoldenCardInstance;
import com.kiwobollae.api.commerce.gacha.repository.GoldenCardInstanceRepository;
import com.kiwobollae.api.commerce.gacha.repository.UserCardCollectionRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
@RequiredArgsConstructor
public class CardMarketTradeProcessor {

  private final CardMarketTradeRepository tradeRepository;
  private final CardMarketNegotiationRepository negotiationRepository;
  private final UserCardCollectionRepository collectionRepository;
  private final GoldenCardInstanceRepository goldenInstanceRepository;
  private final CardMarketPointPort pointPort;
  private final CardMarketNotificationService notificationService;

  public CardMarketTrade completeBuyNow(
      CardMarketListing listing, User buyer, LocalDateTime now) {
    CardMarketTrade trade =
        createTrade(
            listing,
            null,
            buyer,
            CardMarketTradeType.BUY_NOW,
            listing.getAskingPrice(),
            now);
    List<CardMarketPointPort.OfferRelease> releases =
        closeOtherNegotiations(listing.getId(), null, "LISTING_SOLD", now);
    pointPort.settleTrade(
        buyer.getId(),
        listing.getSeller().getId(),
        listing.getAskingPrice(),
        trade.getSellerReceivedPoint(),
        trade.getId(),
        releases);
    transferCard(listing, buyer, now);
    listing.markSold("BUY_NOW", now);
    notificationService.tradeCompleted(
        listing, buyer.getId(), trade.getId(), trade.getTradePrice());
    return trade;
  }

  public CardMarketTrade completeNegotiated(
      CardMarketListing listing,
      CardMarketNegotiation negotiation,
      long tradePrice,
      long additionalBuyerPoint,
      long excessEscrow,
      LocalDateTime now) {
    CardMarketTrade trade =
        createTrade(
            listing,
            negotiation,
            negotiation.getBuyer(),
            CardMarketTradeType.NEGOTIATED,
            tradePrice,
            now);
    List<CardMarketPointPort.OfferRelease> releases =
        closeOtherNegotiations(listing.getId(), negotiation.getId(), "LISTING_SOLD", now);
    if (excessEscrow > 0) {
      releases.add(
          new CardMarketPointPort.OfferRelease(
              negotiation.getBuyer().getId(), excessEscrow, negotiation.getId()));
    }
    pointPort.settleTrade(
        negotiation.getBuyer().getId(),
        listing.getSeller().getId(),
        additionalBuyerPoint,
        trade.getSellerReceivedPoint(),
        trade.getId(),
        releases);
    negotiation.accept(now);
    transferCard(listing, negotiation.getBuyer(), now);
    listing.markSold("NEGOTIATED", now);
    notificationService.tradeCompleted(
        listing, negotiation.getBuyer().getId(), trade.getId(), trade.getTradePrice());
    return trade;
  }

  public List<CardMarketPointPort.OfferRelease> closeOtherNegotiations(
      Long listingId, Long acceptedNegotiationId, String reason, LocalDateTime now) {
    List<CardMarketPointPort.OfferRelease> releases = new ArrayList<>();
    negotiationRepository
        .findAllByListingIdAndStatusForUpdate(
            listingId, CardMarketNegotiationStatus.NEGOTIATING)
        .stream()
        .filter(negotiation -> !Objects.equals(negotiation.getId(), acceptedNegotiationId))
        .sorted(Comparator.comparing(negotiation -> negotiation.getBuyer().getId()))
        .forEach(
            negotiation -> {
              long amount =
                  negotiation.closeAndRelease(
                      CardMarketNegotiationStatus.LISTING_CLOSED, reason, now);
              if (amount > 0) {
                releases.add(
                    new CardMarketPointPort.OfferRelease(
                        negotiation.getBuyer().getId(), amount, negotiation.getId()));
              }
              notificationService.negotiationClosed(negotiation, reason);
            });
    return releases;
  }

  private CardMarketTrade createTrade(
      CardMarketListing listing,
      CardMarketNegotiation negotiation,
      User buyer,
      CardMarketTradeType tradeType,
      long tradePrice,
      LocalDateTime now) {
    long fee = CardMarketPolicy.fee(tradePrice);
    long sellerReceived = tradePrice - fee;
    return tradeRepository.saveAndFlush(
        CardMarketTrade.builder()
            .listing(listing)
            .negotiation(negotiation)
            .tradeType(tradeType)
            .seller(listing.getSeller())
            .buyer(buyer)
            .card(listing.getCard())
            .goldenInstance(listing.getGoldenInstance())
            .cardCodeSnapshot(listing.getCard().getCode())
            .cardNameSnapshot(listing.getCard().getName())
            .raritySnapshot(listing.getCard().getRarity())
            .imageKeySnapshot(listing.getCard().getImageKey())
            .askingPriceSnapshot(listing.getAskingPrice())
            .tradePrice(tradePrice)
            .feeRateBps(CardMarketPolicy.FEE_RATE_BPS)
            .feePoint(fee)
            .sellerReceivedPoint(sellerReceived)
            .completedAt(now)
            .build());
  }

  private void transferCard(CardMarketListing listing, User buyer, LocalDateTime now) {
    if (listing.getAssetType() == CardMarketAssetType.HYPER_RARE) {
      collectionRepository.incrementOwnedCount(buyer.getId(), listing.getCard().getId(), now);
      return;
    }
    GoldenCardInstance instance =
        goldenInstanceRepository
            .findByIdForUpdate(listing.getGoldenInstance().getId())
            .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_DATA_CONFLICT));
    if (!instance.getOwnerUser().getId().equals(listing.getSeller().getId())) {
      throw new BusinessException(ErrorCode.COMMON_DATA_CONFLICT);
    }
    if (collectionRepository.decrementOwnedCount(
            listing.getSeller().getId(), listing.getCard().getId(), now)
        == 0) {
      throw new BusinessException(ErrorCode.COMMON_DATA_CONFLICT);
    }
    collectionRepository.incrementOwnedCount(buyer.getId(), listing.getCard().getId(), now);
    instance.transferTo(buyer, now);
  }
}
