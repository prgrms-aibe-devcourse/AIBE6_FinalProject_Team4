package com.kiwobollae.api.commerce.cardmarket.service;

import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketListingCreateRequest;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketListingResponse;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketNegotiationResponse;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketProposalRequest;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketTradeResponse;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CardMarketCommandService {

  private final CardMarketListingCommandHandler listingHandler;
  private final CardMarketNegotiationCommandHandler negotiationHandler;
  private final CardMarketIdempotencyExecutor idempotencyExecutor;

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public CardMarketListingResponse createListing(
      Long userId,
      String idempotencyKey,
      CardMarketListingCreateRequest request) {
    requireUserAndKey(userId, idempotencyKey);
    if (request == null) {
      throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
    }
    return idempotencyExecutor.execute(
        userId,
        idempotencyKey,
        "CARD_MARKET_LISTING_CREATE",
        request.cardId() + ":" + request.goldenInstanceId() + ":" + request.askingPrice(),
        CardMarketListingResponse.class,
        response -> response.id(),
        () -> listingHandler.create(userId, request));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public CardMarketListingResponse cancelListing(
      Long userId, Long listingId, String idempotencyKey) {
    requireUserAndKey(userId, idempotencyKey);
    return idempotencyExecutor.execute(
        userId,
        idempotencyKey,
        "CARD_MARKET_LISTING_CANCEL",
        String.valueOf(listingId),
        CardMarketListingResponse.class,
        response -> response.id(),
        () -> listingHandler.cancel(userId, listingId));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public CardMarketTradeResponse buyNow(
      Long userId, Long listingId, String idempotencyKey) {
    requireUserAndKey(userId, idempotencyKey);
    return idempotencyExecutor.execute(
        userId,
        idempotencyKey,
        "CARD_MARKET_BUY_NOW",
        String.valueOf(listingId),
        CardMarketTradeResponse.class,
        response -> response.id(),
        () -> negotiationHandler.buyNow(userId, listingId));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public CardMarketNegotiationResponse createNegotiation(
      Long userId,
      Long listingId,
      String idempotencyKey,
      CardMarketProposalRequest request) {
    requireUserAndKey(userId, idempotencyKey);
    requireProposalRequest(request);
    return idempotencyExecutor.execute(
        userId,
        idempotencyKey,
        "CARD_MARKET_OFFER_CREATE",
        listingId + ":" + request.price() + ":" + request.messageCode(),
        CardMarketNegotiationResponse.class,
        response -> response.id(),
        () -> negotiationHandler.create(userId, listingId, request));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public CardMarketNegotiationResponse propose(
      Long userId,
      Long negotiationId,
      String idempotencyKey,
      CardMarketProposalRequest request) {
    requireUserAndKey(userId, idempotencyKey);
    requireProposalRequest(request);
    return idempotencyExecutor.execute(
        userId,
        idempotencyKey,
        "CARD_MARKET_OFFER_PROPOSE",
        negotiationId + ":" + request.price() + ":" + request.messageCode(),
        CardMarketNegotiationResponse.class,
        response -> response.id(),
        () -> negotiationHandler.propose(userId, negotiationId, request));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public CardMarketTradeResponse accept(
      Long userId, Long negotiationId, String idempotencyKey) {
    requireUserAndKey(userId, idempotencyKey);
    return idempotencyExecutor.execute(
        userId,
        idempotencyKey,
        "CARD_MARKET_OFFER_ACCEPT",
        String.valueOf(negotiationId),
        CardMarketTradeResponse.class,
        response -> response.id(),
        () -> negotiationHandler.accept(userId, negotiationId));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public CardMarketNegotiationResponse reject(
      Long userId, Long negotiationId, String idempotencyKey) {
    requireUserAndKey(userId, idempotencyKey);
    return idempotencyExecutor.execute(
        userId,
        idempotencyKey,
        "CARD_MARKET_OFFER_REJECT",
        String.valueOf(negotiationId),
        CardMarketNegotiationResponse.class,
        response -> response.id(),
        () -> negotiationHandler.reject(userId, negotiationId));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public CardMarketNegotiationResponse cancelNegotiation(
      Long userId, Long negotiationId, String idempotencyKey) {
    requireUserAndKey(userId, idempotencyKey);
    return idempotencyExecutor.execute(
        userId,
        idempotencyKey,
        "CARD_MARKET_OFFER_CANCEL",
        String.valueOf(negotiationId),
        CardMarketNegotiationResponse.class,
        response -> response.id(),
        () -> negotiationHandler.cancel(userId, negotiationId));
  }

  private void requireProposalRequest(CardMarketProposalRequest request) {
    if (request == null) {
      throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
    }
    CardMarketPolicy.requirePrice(request.price());
  }

  private void requireUserAndKey(Long userId, String idempotencyKey) {
    if (userId == null || userId < 1) {
      throw new BusinessException(ErrorCode.AUTH_AUTHENTICATION_REQUIRED);
    }
    if (idempotencyKey == null
        || idempotencyKey.isBlank()
        || idempotencyKey.length() > 64) {
      throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
    }
  }
}
