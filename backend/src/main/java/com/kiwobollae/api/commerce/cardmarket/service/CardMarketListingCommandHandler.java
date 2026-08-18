package com.kiwobollae.api.commerce.cardmarket.service;

import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketListingCreateRequest;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketListingResponse;
import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketListing;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketAssetType;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketListingStatus;
import com.kiwobollae.api.commerce.cardmarket.port.CardMarketPointPort;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketListingRepository;
import com.kiwobollae.api.commerce.gacha.entity.GoldenCardInstance;
import com.kiwobollae.api.commerce.gacha.entity.TradingCard;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardStatus;
import com.kiwobollae.api.commerce.gacha.repository.GoldenCardInstanceRepository;
import com.kiwobollae.api.commerce.gacha.repository.TradingCardRepository;
import com.kiwobollae.api.commerce.gacha.repository.UserCardCollectionRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
@RequiredArgsConstructor
public class CardMarketListingCommandHandler {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final CardMarketListingRepository listingRepository;
  private final TradingCardRepository tradingCardRepository;
  private final UserCardCollectionRepository collectionRepository;
  private final GoldenCardInstanceRepository goldenInstanceRepository;
  private final UserRepository userRepository;
  private final CardMarketPointPort pointPort;
  private final CardMarketTradeProcessor tradeProcessor;
  private final CardMarketResponseMapper responseMapper;
  private final CardMarketCommandSupport support;
  private final Clock seoulClock;

  public CardMarketListingResponse create(Long userId, CardMarketListingCreateRequest request) {
    CardMarketPolicy.requirePrice(request.askingPrice());
    TradingCard card =
        tradingCardRepository
            .findByIdForUpdate(request.cardId())
            .orElseThrow(() -> new BusinessException(ErrorCode.GACHA_CARD_NOT_OWNED));
    if (card.getStatus() != TradingCardStatus.ACTIVE) {
      throw new BusinessException(ErrorCode.CARD_MARKET_CARD_NOT_TRADABLE);
    }

    CardMarketAssetType assetType;
    GoldenCardInstance goldenInstance = null;
    if (card.getRarity() == TradingCardRarity.HYPER_RARE) {
      if (request.goldenInstanceId() != null) {
        throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
      }
      assetType = CardMarketAssetType.HYPER_RARE;
    } else if (card.getRarity() == TradingCardRarity.GOLDEN_RARE) {
      if (request.goldenInstanceId() == null) {
        throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
      }
      goldenInstance =
          goldenInstanceRepository
              .findByIdForUpdate(request.goldenInstanceId())
              .orElseThrow(() -> new BusinessException(ErrorCode.CARD_MARKET_GOLDEN_NOT_OWNED));
      if (!goldenInstance.getOwnerUser().getId().equals(userId)
          || !goldenInstance.getCard().getId().equals(card.getId())) {
        throw new BusinessException(ErrorCode.CARD_MARKET_GOLDEN_NOT_OWNED);
      }
      if (listingRepository.existsByGoldenInstance_IdAndStatus(
          goldenInstance.getId(), CardMarketListingStatus.OPEN)) {
        throw new BusinessException(ErrorCode.CARD_MARKET_GOLDEN_ALREADY_LISTED);
      }
      assetType = CardMarketAssetType.GOLDEN_RARE;
    } else {
      throw new BusinessException(ErrorCode.CARD_MARKET_CARD_NOT_TRADABLE);
    }

    LocalDateTime now = now();
    if (assetType == CardMarketAssetType.HYPER_RARE
        && collectionRepository.decrementKeepingOne(userId, card.getId(), 1, now) == 0) {
      throw new BusinessException(ErrorCode.CARD_MARKET_CARD_KEEP_ONE_REQUIRED);
    }

    CardMarketListing listing =
        listingRepository.save(
            CardMarketListing.builder()
                .seller(userRepository.getReferenceById(userId))
                .card(card)
                .goldenInstance(goldenInstance)
                .assetType(assetType)
                .askingPrice(request.askingPrice())
                .status(CardMarketListingStatus.OPEN)
                .expiresAt(CardMarketPolicy.listingExpiresAt(now))
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());
    return responseMapper.listing(listing, 0);
  }

  public CardMarketListingResponse cancel(Long userId, Long listingId) {
    CardMarketListing listing = support.requireListingForUpdate(listingId);
    LocalDateTime now = now();
    support.validateOpenListing(listing, now);
    if (!listing.getSeller().getId().equals(userId)) {
      throw new BusinessException(ErrorCode.CARD_MARKET_LISTING_NOT_FOUND);
    }
    List<CardMarketPointPort.OfferRelease> releases =
        tradeProcessor.closeOtherNegotiations(
            listing.getId(), null, "LISTING_CANCELLED", now);
    pointPort.releaseOffers(releases);
    if (listing.getAssetType() == CardMarketAssetType.HYPER_RARE) {
      collectionRepository.incrementOwnedCount(userId, listing.getCard().getId(), now);
    }
    listing.cancel("SELLER_CANCELLED", now);
    return responseMapper.listing(listing, 0);
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(seoulClock.instant(), KST);
  }
}
