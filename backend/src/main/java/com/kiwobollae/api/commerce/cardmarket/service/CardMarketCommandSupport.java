package com.kiwobollae.api.commerce.cardmarket.service;

import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketListing;
import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketNegotiation;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketListingStatus;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketNegotiationStatus;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketParticipantType;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketListingRepository;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketNegotiationRepository;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardStatus;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(propagation = Propagation.MANDATORY)
@RequiredArgsConstructor
public class CardMarketCommandSupport {

  private final CardMarketListingRepository listingRepository;
  private final CardMarketNegotiationRepository negotiationRepository;

  public CardMarketListing requireListingForUpdate(Long listingId) {
    if (listingId == null || listingId < 1) {
      throw new BusinessException(ErrorCode.CARD_MARKET_LISTING_NOT_FOUND);
    }
    return listingRepository
        .findByIdForUpdate(listingId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CARD_MARKET_LISTING_NOT_FOUND));
  }

  public void validateOpenListing(CardMarketListing listing, LocalDateTime now) {
    if (listing.getStatus() != CardMarketListingStatus.OPEN
        || !listing.getExpiresAt().isAfter(now)) {
      throw new BusinessException(ErrorCode.CARD_MARKET_LISTING_NOT_OPEN);
    }
  }

  public CardMarketNegotiation requireNegotiation(Long negotiationId) {
    requireNegotiationId(negotiationId);
    return negotiationRepository
        .findById(negotiationId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CARD_MARKET_NEGOTIATION_NOT_FOUND));
  }

  public CardMarketNegotiation requireNegotiationForUpdate(Long negotiationId) {
    requireNegotiationId(negotiationId);
    return negotiationRepository
        .findByIdForUpdate(negotiationId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CARD_MARKET_NEGOTIATION_NOT_FOUND));
  }

  public void validateNegotiating(
      CardMarketNegotiation negotiation, LocalDateTime now) {
    if (negotiation.getStatus() != CardMarketNegotiationStatus.NEGOTIATING
        || !negotiation.getExpiresAt().isAfter(now)) {
      throw new BusinessException(ErrorCode.CARD_MARKET_NEGOTIATION_INVALID_STATE);
    }
  }

  public CardMarketParticipantType participantType(
      Long userId, CardMarketNegotiation negotiation) {
    if (negotiation.getBuyer().getId().equals(userId)) {
      return CardMarketParticipantType.BUYER;
    }
    if (negotiation.getListing().getSeller().getId().equals(userId)) {
      return CardMarketParticipantType.SELLER;
    }
    throw new BusinessException(ErrorCode.CARD_MARKET_NEGOTIATION_NOT_FOUND);
  }

  public void validateNotSelf(Long userId, CardMarketListing listing) {
    if (listing.getSeller().getId().equals(userId)) {
      throw new BusinessException(ErrorCode.CARD_MARKET_SELF_TRADE);
    }
  }

  public void validateActiveCard(CardMarketListing listing) {
    if (listing.getCard().getStatus() != TradingCardStatus.ACTIVE) {
      throw new BusinessException(ErrorCode.CARD_MARKET_LISTING_NOT_OPEN);
    }
  }

  private void requireNegotiationId(Long negotiationId) {
    if (negotiationId == null || negotiationId < 1) {
      throw new BusinessException(ErrorCode.CARD_MARKET_NEGOTIATION_NOT_FOUND);
    }
  }
}
