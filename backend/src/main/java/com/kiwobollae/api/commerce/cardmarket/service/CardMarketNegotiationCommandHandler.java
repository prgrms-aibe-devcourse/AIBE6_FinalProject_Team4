package com.kiwobollae.api.commerce.cardmarket.service;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketNegotiationResponse;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketProposalRequest;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketTradeResponse;
import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketListing;
import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketNegotiation;
import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketProposal;
import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketTrade;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketNegotiationStatus;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketParticipantType;
import com.kiwobollae.api.commerce.cardmarket.port.CardMarketPointPort;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketNegotiationRepository;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketProposalRepository;
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
public class CardMarketNegotiationCommandHandler {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final CardMarketNegotiationRepository negotiationRepository;
  private final CardMarketProposalRepository proposalRepository;
  private final UserRepository userRepository;
  private final CardMarketPointPort pointPort;
  private final CardMarketNotificationService notificationService;
  private final CardMarketTradeProcessor tradeProcessor;
  private final CardMarketResponseMapper responseMapper;
  private final CardMarketCommandSupport support;
  private final Clock seoulClock;

  public CardMarketTradeResponse buyNow(Long userId, Long listingId) {
    CardMarketListing listing = support.requireListingForUpdate(listingId);
    LocalDateTime now = now();
    support.validateOpenListing(listing, now);
    support.validateNotSelf(userId, listing);
    support.validateActiveCard(listing);
    User buyer = userRepository.getReferenceById(userId);
    CardMarketTrade trade = tradeProcessor.completeBuyNow(listing, buyer, now);
    return responseMapper.trade(trade);
  }

  public CardMarketNegotiationResponse create(
      Long userId,
      Long listingId,
      CardMarketProposalRequest request) {
    CardMarketListing listing = support.requireListingForUpdate(listingId);
    LocalDateTime now = now();
    support.validateOpenListing(listing, now);
    support.validateNotSelf(userId, listing);
    support.validateActiveCard(listing);
    if (request.price() >= listing.getAskingPrice()) {
      throw new BusinessException(ErrorCode.CARD_MARKET_PROPOSAL_PRICE_INVALID);
    }
    if (negotiationRepository.existsByListing_IdAndBuyer_IdAndStatus(
        listingId, userId, CardMarketNegotiationStatus.NEGOTIATING)) {
      throw new BusinessException(ErrorCode.CARD_MARKET_NEGOTIATION_DUPLICATE);
    }
    User buyer = userRepository.getReferenceById(userId);
    CardMarketNegotiation negotiation =
        negotiationRepository.saveAndFlush(
            CardMarketNegotiation.builder()
                .listing(listing)
                .buyer(buyer)
                .status(CardMarketNegotiationStatus.NEGOTIATING)
                .turn(CardMarketParticipantType.SELLER)
                .currentProposerType(CardMarketParticipantType.BUYER)
                .currentPrice(request.price())
                .escrowedPaidPoint(request.price())
                .sellerCounterCount(0)
                .nextSequence(1)
                .expiresAt(CardMarketPolicy.negotiationExpiresAt(listing.getExpiresAt(), now))
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build());
    pointPort.reserveOffer(userId, request.price(), negotiation.getId());
    proposalRepository.save(
        CardMarketProposal.builder()
            .negotiation(negotiation)
            .proposer(buyer)
            .proposerType(CardMarketParticipantType.BUYER)
            .sequenceNo(negotiation.takeNextSequence())
            .proposedPrice(request.price())
            .messageCode(request.messageCode())
            .createdAt(now)
            .build());
    notificationService.offerCreated(negotiation);
    return responseMapper.negotiation(negotiation, List.of());
  }

  public CardMarketNegotiationResponse propose(
      Long userId,
      Long negotiationId,
      CardMarketProposalRequest request) {
    CardMarketNegotiation snapshot = support.requireNegotiation(negotiationId);
    CardMarketListing listing = support.requireListingForUpdate(snapshot.getListing().getId());
    CardMarketNegotiation negotiation = support.requireNegotiationForUpdate(negotiationId);
    LocalDateTime now = now();
    support.validateOpenListing(listing, now);
    support.validateNegotiating(negotiation, now);
    support.validateActiveCard(listing);
    CardMarketParticipantType proposer = support.participantType(userId, negotiation);
    if (negotiation.getTurn() != proposer) {
      throw new BusinessException(ErrorCode.CARD_MARKET_NEGOTIATION_TURN_CONFLICT);
    }

    List<CardMarketProposal> history =
        proposalRepository.findAllByNegotiation_IdOrderBySequenceNoAsc(negotiationId);
    validateCounterPrice(proposer, request.price(), listing, negotiation, history);
    long escrow = negotiation.getEscrowedPaidPoint();
    if (proposer == CardMarketParticipantType.BUYER) {
      long additional = request.price() - escrow;
      if (additional <= 0) {
        throw new BusinessException(ErrorCode.CARD_MARKET_PROPOSAL_PRICE_INVALID);
      }
      pointPort.reserveOffer(userId, additional, negotiationId);
      escrow = request.price();
    }
    negotiation.updateProposal(
        proposer,
        request.price(),
        escrow,
        CardMarketPolicy.negotiationExpiresAt(listing.getExpiresAt(), now),
        now);
    proposalRepository.save(
        CardMarketProposal.builder()
            .negotiation(negotiation)
            .proposer(userRepository.getReferenceById(userId))
            .proposerType(proposer)
            .sequenceNo(negotiation.takeNextSequence())
            .proposedPrice(request.price())
            .messageCode(request.messageCode())
            .createdAt(now)
            .build());
    notificationService.counterProposed(negotiation, userId);
    return responseMapper.negotiation(negotiation, List.of());
  }

  public CardMarketTradeResponse accept(Long userId, Long negotiationId) {
    CardMarketNegotiation snapshot = support.requireNegotiation(negotiationId);
    CardMarketListing listing = support.requireListingForUpdate(snapshot.getListing().getId());
    CardMarketNegotiation negotiation = support.requireNegotiationForUpdate(negotiationId);
    LocalDateTime now = now();
    support.validateOpenListing(listing, now);
    support.validateNegotiating(negotiation, now);
    support.validateActiveCard(listing);
    CardMarketParticipantType responder = support.participantType(userId, negotiation);
    if (negotiation.getTurn() != responder) {
      throw new BusinessException(ErrorCode.CARD_MARKET_NEGOTIATION_TURN_CONFLICT);
    }

    long tradePrice = negotiation.getCurrentPrice();
    long escrowedPaidPoint = negotiation.getEscrowedPaidPoint();
    long additionalBuyerPoint = Math.max(0L, tradePrice - escrowedPaidPoint);
    long excessEscrow = Math.max(0L, escrowedPaidPoint - tradePrice);
    CardMarketTrade trade =
        tradeProcessor.completeNegotiated(
            listing,
            negotiation,
            tradePrice,
            additionalBuyerPoint,
            excessEscrow,
            now);
    return responseMapper.trade(trade);
  }

  public CardMarketNegotiationResponse reject(Long userId, Long negotiationId) {
    CardMarketNegotiation snapshot = support.requireNegotiation(negotiationId);
    CardMarketListing listing = support.requireListingForUpdate(snapshot.getListing().getId());
    CardMarketNegotiation negotiation = support.requireNegotiationForUpdate(negotiationId);
    LocalDateTime now = now();
    support.validateOpenListing(listing, now);
    support.validateNegotiating(negotiation, now);
    CardMarketParticipantType responder = support.participantType(userId, negotiation);
    if (negotiation.getTurn() != responder) {
      throw new BusinessException(ErrorCode.CARD_MARKET_NEGOTIATION_TURN_CONFLICT);
    }
    long release =
        negotiation.closeAndRelease(
            CardMarketNegotiationStatus.REJECTED, "REJECTED", now);
    pointPort.releaseOffer(
        negotiation.getBuyer().getId(), release, negotiation.getId());
    notificationService.negotiationRejected(negotiation, userId);
    return responseMapper.negotiation(negotiation, List.of());
  }

  public CardMarketNegotiationResponse cancel(Long userId, Long negotiationId) {
    CardMarketNegotiation snapshot = support.requireNegotiation(negotiationId);
    CardMarketListing listing = support.requireListingForUpdate(snapshot.getListing().getId());
    CardMarketNegotiation negotiation = support.requireNegotiationForUpdate(negotiationId);
    LocalDateTime now = now();
    support.validateOpenListing(listing, now);
    support.validateNegotiating(negotiation, now);
    if (!negotiation.getBuyer().getId().equals(userId)) {
      throw new BusinessException(ErrorCode.CARD_MARKET_NEGOTIATION_NOT_FOUND);
    }
    long release =
        negotiation.closeAndRelease(
            CardMarketNegotiationStatus.CANCELLED, "BUYER_CANCELLED", now);
    pointPort.releaseOffer(userId, release, negotiationId);
    notificationService.negotiationCancelled(negotiation);
    return responseMapper.negotiation(negotiation, List.of());
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(seoulClock.instant(), KST);
  }

  private void validateCounterPrice(
      CardMarketParticipantType proposer,
      long price,
      CardMarketListing listing,
      CardMarketNegotiation negotiation,
      List<CardMarketProposal> history) {
    CardMarketPolicy.requirePrice(price);
    if (proposer == CardMarketParticipantType.SELLER) {
      long lastSellerPrice =
          history.stream()
              .filter(item -> item.getProposerType() == CardMarketParticipantType.SELLER)
              .mapToLong(CardMarketProposal::getProposedPrice)
              .reduce((first, second) -> second)
              .orElse(listing.getAskingPrice());
      if (price <= negotiation.getCurrentPrice() || price > lastSellerPrice) {
        throw new BusinessException(ErrorCode.CARD_MARKET_PROPOSAL_PRICE_INVALID);
      }
      return;
    }
    long lastBuyerPrice =
        history.stream()
            .filter(item -> item.getProposerType() == CardMarketParticipantType.BUYER)
            .mapToLong(CardMarketProposal::getProposedPrice)
            .reduce((first, second) -> second)
            .orElse(0L);
    if (price <= lastBuyerPrice || price >= negotiation.getCurrentPrice()) {
      throw new BusinessException(ErrorCode.CARD_MARKET_PROPOSAL_PRICE_INVALID);
    }
  }
}
