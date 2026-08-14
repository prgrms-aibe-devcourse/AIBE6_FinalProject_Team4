package com.kiwobollae.api.commerce.cardmarket.service;

import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketListingCreateRequest;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketListingResponse;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketNegotiationResponse;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketProposalRequest;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketTradeResponse;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CardMarketCommandService {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final CardMarketListingCommandHandler listingHandler;
  private final CardMarketNegotiationCommandHandler negotiationHandler;
  private final CardMarketIdempotencyExecutor idempotencyExecutor;
  private final Clock seoulClock;

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
        () -> listingHandler.create(userId, request, now()));
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
        () -> listingHandler.cancel(userId, listingId, now()));
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
        () -> negotiationHandler.buyNow(userId, listingId, now()));
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
        () -> negotiationHandler.create(userId, listingId, request, now()));
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
        () -> negotiationHandler.propose(userId, negotiationId, request, now()));
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
        () -> negotiationHandler.accept(userId, negotiationId, now()));
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
        () -> negotiationHandler.reject(userId, negotiationId, now()));
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
        () -> negotiationHandler.cancel(userId, negotiationId, now()));
  }

  private void requireProposalRequest(CardMarketProposalRequest request) {
    if (request == null) {
      throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
    }
    CardMarketPolicy.requirePrice(request.price());
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(seoulClock.instant(), KST);
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
