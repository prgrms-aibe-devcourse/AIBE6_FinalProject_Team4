package com.kiwobollae.api.commerce.cardmarket.service;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketListingCreateRequest;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketListingResponse;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketNegotiationResponse;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketProposalRequest;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketProposalResponse;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketTradeResponse;
import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketListing;
import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketNegotiation;
import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketProposal;
import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketTrade;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketAssetType;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketListingStatus;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketNegotiationStatus;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketParticipantType;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketTradeType;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketListingRepository;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketNegotiationRepository;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketProposalRepository;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketTradeRepository;
import com.kiwobollae.api.commerce.cardmarket.port.CardMarketPointPort;
import com.kiwobollae.api.commerce.gacha.entity.GoldenCardInstance;
import com.kiwobollae.api.commerce.gacha.entity.TradingCard;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardStatus;
import com.kiwobollae.api.commerce.gacha.repository.GoldenCardInstanceRepository;
import com.kiwobollae.api.commerce.gacha.repository.TradingCardRepository;
import com.kiwobollae.api.commerce.gacha.repository.UserCardCollectionRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.infra.service.IdempotencyExecution;
import com.kiwobollae.api.infra.service.IdempotencyService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class CardMarketCommandService {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final CardMarketListingRepository listingRepository;
  private final CardMarketNegotiationRepository negotiationRepository;
  private final CardMarketProposalRepository proposalRepository;
  private final CardMarketTradeRepository tradeRepository;
  private final TradingCardRepository tradingCardRepository;
  private final UserCardCollectionRepository collectionRepository;
  private final GoldenCardInstanceRepository goldenInstanceRepository;
  private final UserRepository userRepository;
  private final CardMarketPointPort pointPort;
  private final CardMarketNotificationService notificationService;
  private final IdempotencyService idempotencyService;
  private final ObjectMapper objectMapper;
  private final Clock seoulClock;

  @Value("${app.asset.base-url:}")
  private String assetBaseUrl;

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public CardMarketListingResponse createListing(
      Long userId,
      String idempotencyKey,
      CardMarketListingCreateRequest request) {
    requireUserAndKey(userId, idempotencyKey);
    if (request == null) {
      throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
    }
    return idempotent(
        userId,
        idempotencyKey,
        "CARD_MARKET_LISTING_CREATE",
        request.cardId() + ":" + request.goldenInstanceId() + ":" + request.askingPrice(),
        CardMarketListingResponse.class,
        response -> response.id(),
        () -> doCreateListing(userId, request));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public CardMarketListingResponse cancelListing(
      Long userId, Long listingId, String idempotencyKey) {
    requireUserAndKey(userId, idempotencyKey);
    return idempotent(
        userId,
        idempotencyKey,
        "CARD_MARKET_LISTING_CANCEL",
        String.valueOf(listingId),
        CardMarketListingResponse.class,
        response -> response.id(),
        () -> doCancelListing(userId, listingId));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public CardMarketTradeResponse buyNow(
      Long userId, Long listingId, String idempotencyKey) {
    requireUserAndKey(userId, idempotencyKey);
    return idempotent(
        userId,
        idempotencyKey,
        "CARD_MARKET_BUY_NOW",
        String.valueOf(listingId),
        CardMarketTradeResponse.class,
        response -> response.id(),
        () -> doBuyNow(userId, listingId));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public CardMarketNegotiationResponse createNegotiation(
      Long userId,
      Long listingId,
      String idempotencyKey,
      CardMarketProposalRequest request) {
    requireUserAndKey(userId, idempotencyKey);
    requireProposalRequest(request);
    return idempotent(
        userId,
        idempotencyKey,
        "CARD_MARKET_OFFER_CREATE",
        listingId + ":" + request.price() + ":" + request.messageCode(),
        CardMarketNegotiationResponse.class,
        response -> response.id(),
        () -> doCreateNegotiation(userId, listingId, request));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public CardMarketNegotiationResponse propose(
      Long userId,
      Long negotiationId,
      String idempotencyKey,
      CardMarketProposalRequest request) {
    requireUserAndKey(userId, idempotencyKey);
    requireProposalRequest(request);
    return idempotent(
        userId,
        idempotencyKey,
        "CARD_MARKET_OFFER_PROPOSE",
        negotiationId + ":" + request.price() + ":" + request.messageCode(),
        CardMarketNegotiationResponse.class,
        response -> response.id(),
        () -> doPropose(userId, negotiationId, request));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public CardMarketTradeResponse accept(
      Long userId, Long negotiationId, String idempotencyKey) {
    requireUserAndKey(userId, idempotencyKey);
    return idempotent(
        userId,
        idempotencyKey,
        "CARD_MARKET_OFFER_ACCEPT",
        String.valueOf(negotiationId),
        CardMarketTradeResponse.class,
        response -> response.id(),
        () -> doAccept(userId, negotiationId));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public CardMarketNegotiationResponse reject(
      Long userId, Long negotiationId, String idempotencyKey) {
    requireUserAndKey(userId, idempotencyKey);
    return idempotent(
        userId,
        idempotencyKey,
        "CARD_MARKET_OFFER_REJECT",
        String.valueOf(negotiationId),
        CardMarketNegotiationResponse.class,
        response -> response.id(),
        () -> doReject(userId, negotiationId));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public CardMarketNegotiationResponse cancelNegotiation(
      Long userId, Long negotiationId, String idempotencyKey) {
    requireUserAndKey(userId, idempotencyKey);
    return idempotent(
        userId,
        idempotencyKey,
        "CARD_MARKET_OFFER_CANCEL",
        String.valueOf(negotiationId),
        CardMarketNegotiationResponse.class,
        response -> response.id(),
        () -> doCancelNegotiation(userId, negotiationId));
  }

  private CardMarketListingResponse doCreateListing(
      Long userId, CardMarketListingCreateRequest request) {
    validatePrice(request.askingPrice());
    TradingCard card =
        tradingCardRepository
            .findByIdForUpdate(request.cardId())
            .orElseThrow(() -> new BusinessException(ErrorCode.GACHA_CARD_NOT_OWNED));
    if (card.getStatus() != TradingCardStatus.ACTIVE) {
      throw new BusinessException(ErrorCode.CARD_MARKET_CARD_NOT_TRADABLE);
    }

    LocalDateTime now = now();
    CardMarketAssetType assetType;
    GoldenCardInstance goldenInstance = null;
    if (card.getRarity() == TradingCardRarity.HYPER_RARE) {
      if (request.goldenInstanceId() != null) {
        throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
      }
      if (collectionRepository.decrementKeepingOne(userId, card.getId(), 1, now) == 0) {
        throw new BusinessException(ErrorCode.CARD_MARKET_CARD_KEEP_ONE_REQUIRED);
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
    return listingResponse(listing, 0);
  }

  private CardMarketListingResponse doCancelListing(Long userId, Long listingId) {
    CardMarketListing listing = requireOpenListingForUpdate(listingId);
    if (!listing.getSeller().getId().equals(userId)) {
      throw new BusinessException(ErrorCode.CARD_MARKET_LISTING_NOT_FOUND);
    }
    LocalDateTime now = now();
    List<CardMarketPointPort.OfferRelease> releases =
        closeOtherNegotiations(listing.getId(), null, "LISTING_CANCELLED", now);
    pointPort.releaseOffers(releases);
    if (listing.getAssetType() == CardMarketAssetType.HYPER_RARE) {
      collectionRepository.incrementOwnedCount(userId, listing.getCard().getId(), now);
    }
    listing.cancel("SELLER_CANCELLED", now);
    return listingResponse(listing, 0);
  }

  private CardMarketTradeResponse doBuyNow(Long userId, Long listingId) {
    CardMarketListing listing = requireOpenListingForUpdate(listingId);
    validateNotSelf(userId, listing);
    validateActiveCard(listing);
    LocalDateTime now = now();
    User buyer = userRepository.getReferenceById(userId);
    CardMarketTrade trade = createTrade(listing, null, buyer, CardMarketTradeType.BUY_NOW, listing.getAskingPrice(), now);
    List<CardMarketPointPort.OfferRelease> releases =
        closeOtherNegotiations(listing.getId(), null, "LISTING_SOLD", now);
    pointPort.settleTrade(
        userId,
        listing.getSeller().getId(),
        listing.getAskingPrice(),
        trade.getSellerReceivedPoint(),
        trade.getId(),
        releases);
    transferCard(listing, buyer, now);
    listing.markSold("BUY_NOW", now);
    notificationService.tradeCompleted(
        listing, buyer.getId(), trade.getId(), trade.getTradePrice());
    return tradeResponse(trade);
  }

  private CardMarketNegotiationResponse doCreateNegotiation(
      Long userId, Long listingId, CardMarketProposalRequest request) {
    CardMarketListing listing = requireOpenListingForUpdate(listingId);
    validateNotSelf(userId, listing);
    validateActiveCard(listing);
    if (request.price() >= listing.getAskingPrice()) {
      throw new BusinessException(ErrorCode.CARD_MARKET_PROPOSAL_PRICE_INVALID);
    }
    if (negotiationRepository.existsByListing_IdAndBuyer_IdAndStatus(
        listingId, userId, CardMarketNegotiationStatus.NEGOTIATING)) {
      throw new BusinessException(ErrorCode.CARD_MARKET_NEGOTIATION_DUPLICATE);
    }
    LocalDateTime now = now();
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
                .expiresAt(expiresAt(listing, now))
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
    return negotiationResponse(negotiation);
  }

  private CardMarketNegotiationResponse doPropose(
      Long userId, Long negotiationId, CardMarketProposalRequest request) {
    CardMarketNegotiation snapshot = requireNegotiation(negotiationId);
    CardMarketListing listing = requireOpenListingForUpdate(snapshot.getListing().getId());
    CardMarketNegotiation negotiation = requireNegotiationForUpdate(negotiationId);
    validateNegotiating(negotiation);
    validateActiveCard(listing);
    CardMarketParticipantType proposer = participantType(userId, negotiation);
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
    LocalDateTime now = now();
    negotiation.updateProposal(proposer, request.price(), escrow, expiresAt(listing, now), now);
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
    return negotiationResponse(negotiation);
  }

  private CardMarketTradeResponse doAccept(Long userId, Long negotiationId) {
    CardMarketNegotiation snapshot = requireNegotiation(negotiationId);
    CardMarketListing listing = requireOpenListingForUpdate(snapshot.getListing().getId());
    CardMarketNegotiation negotiation = requireNegotiationForUpdate(negotiationId);
    validateNegotiating(negotiation);
    validateActiveCard(listing);
    CardMarketParticipantType responder = participantType(userId, negotiation);
    if (negotiation.getTurn() != responder) {
      throw new BusinessException(ErrorCode.CARD_MARKET_NEGOTIATION_TURN_CONFLICT);
    }

    LocalDateTime now = now();
    long tradePrice = negotiation.getCurrentPrice();
    long escrowedPaidPoint = negotiation.getEscrowedPaidPoint();
    long additionalBuyerPoint = Math.max(0L, tradePrice - escrowedPaidPoint);
    long excessEscrow = Math.max(0L, escrowedPaidPoint - tradePrice);
    CardMarketTrade trade =
        createTrade(
            listing,
            negotiation,
            negotiation.getBuyer(),
            CardMarketTradeType.NEGOTIATED,
            tradePrice,
            now);
    List<CardMarketPointPort.OfferRelease> releases =
        closeOtherNegotiations(listing.getId(), negotiationId, "LISTING_SOLD", now);
    if (excessEscrow > 0) {
      releases.add(
          new CardMarketPointPort.OfferRelease(
              negotiation.getBuyer().getId(), excessEscrow, negotiationId));
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
    return tradeResponse(trade);
  }

  private CardMarketNegotiationResponse doReject(Long userId, Long negotiationId) {
    CardMarketNegotiation snapshot = requireNegotiation(negotiationId);
    requireOpenListingForUpdate(snapshot.getListing().getId());
    CardMarketNegotiation negotiation = requireNegotiationForUpdate(negotiationId);
    validateNegotiating(negotiation);
    CardMarketParticipantType responder = participantType(userId, negotiation);
    if (negotiation.getTurn() != responder) {
      throw new BusinessException(ErrorCode.CARD_MARKET_NEGOTIATION_TURN_CONFLICT);
    }
    LocalDateTime now = now();
    long release =
        negotiation.closeAndRelease(CardMarketNegotiationStatus.REJECTED, "REJECTED", now);
    pointPort.releaseOffer(
        negotiation.getBuyer().getId(), release, negotiation.getId());
    notificationService.negotiationRejected(negotiation, userId);
    return negotiationResponse(negotiation);
  }

  private CardMarketNegotiationResponse doCancelNegotiation(Long userId, Long negotiationId) {
    CardMarketNegotiation snapshot = requireNegotiation(negotiationId);
    requireOpenListingForUpdate(snapshot.getListing().getId());
    CardMarketNegotiation negotiation = requireNegotiationForUpdate(negotiationId);
    validateNegotiating(negotiation);
    if (!negotiation.getBuyer().getId().equals(userId)) {
      throw new BusinessException(ErrorCode.CARD_MARKET_NEGOTIATION_NOT_FOUND);
    }
    LocalDateTime now = now();
    long release =
        negotiation.closeAndRelease(
            CardMarketNegotiationStatus.CANCELLED, "BUYER_CANCELLED", now);
    pointPort.releaseOffer(userId, release, negotiationId);
    notificationService.negotiationCancelled(negotiation);
    return negotiationResponse(negotiation);
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

  private List<CardMarketPointPort.OfferRelease> closeOtherNegotiations(
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

  private void validateCounterPrice(
      CardMarketParticipantType proposer,
      long price,
      CardMarketListing listing,
      CardMarketNegotiation negotiation,
      List<CardMarketProposal> history) {
    validatePrice(price);
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

  private CardMarketParticipantType participantType(
      Long userId, CardMarketNegotiation negotiation) {
    if (negotiation.getBuyer().getId().equals(userId)) {
      return CardMarketParticipantType.BUYER;
    }
    if (negotiation.getListing().getSeller().getId().equals(userId)) {
      return CardMarketParticipantType.SELLER;
    }
    throw new BusinessException(ErrorCode.CARD_MARKET_NEGOTIATION_NOT_FOUND);
  }

  private CardMarketListing requireOpenListingForUpdate(Long listingId) {
    if (listingId == null || listingId < 1) {
      throw new BusinessException(ErrorCode.CARD_MARKET_LISTING_NOT_FOUND);
    }
    CardMarketListing listing =
        listingRepository
            .findByIdForUpdate(listingId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CARD_MARKET_LISTING_NOT_FOUND));
    if (listing.getStatus() != CardMarketListingStatus.OPEN || !listing.getExpiresAt().isAfter(now())) {
      throw new BusinessException(ErrorCode.CARD_MARKET_LISTING_NOT_OPEN);
    }
    return listing;
  }

  private CardMarketNegotiation requireNegotiation(Long negotiationId) {
    if (negotiationId == null || negotiationId < 1) {
      throw new BusinessException(ErrorCode.CARD_MARKET_NEGOTIATION_NOT_FOUND);
    }
    return negotiationRepository
        .findById(negotiationId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CARD_MARKET_NEGOTIATION_NOT_FOUND));
  }

  private CardMarketNegotiation requireNegotiationForUpdate(Long negotiationId) {
    return negotiationRepository
        .findByIdForUpdate(negotiationId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CARD_MARKET_NEGOTIATION_NOT_FOUND));
  }

  private void validateNegotiating(CardMarketNegotiation negotiation) {
    if (negotiation.getStatus() != CardMarketNegotiationStatus.NEGOTIATING
        || !negotiation.getExpiresAt().isAfter(now())) {
      throw new BusinessException(ErrorCode.CARD_MARKET_NEGOTIATION_INVALID_STATE);
    }
  }

  private void validateNotSelf(Long userId, CardMarketListing listing) {
    if (listing.getSeller().getId().equals(userId)) {
      throw new BusinessException(ErrorCode.CARD_MARKET_SELF_TRADE);
    }
  }

  private void validateActiveCard(CardMarketListing listing) {
    if (listing.getCard().getStatus() != TradingCardStatus.ACTIVE) {
      throw new BusinessException(ErrorCode.CARD_MARKET_LISTING_NOT_OPEN);
    }
  }

  private void validatePrice(Long price) {
    CardMarketPolicy.requirePrice(price);
  }

  private void requireProposalRequest(CardMarketProposalRequest request) {
    if (request == null) {
      throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
    }
    validatePrice(request.price());
  }

  private LocalDateTime expiresAt(CardMarketListing listing, LocalDateTime now) {
    return CardMarketPolicy.negotiationExpiresAt(listing.getExpiresAt(), now);
  }

  private CardMarketListingResponse listingResponse(CardMarketListing listing, long offers) {
    return CardMarketListingResponse.from(
        listing, imageUrl(listing.getCard().getImageKey()), offers);
  }

  private CardMarketNegotiationResponse negotiationResponse(
      CardMarketNegotiation negotiation) {
    return CardMarketNegotiationResponse.from(
        negotiation, imageUrl(negotiation.getListing().getCard().getImageKey()), List.of());
  }

  private CardMarketTradeResponse tradeResponse(CardMarketTrade trade) {
    return CardMarketTradeResponse.from(trade, imageUrl(trade.getImageKeySnapshot()));
  }

  private String imageUrl(String imageKey) {
    if (imageKey == null || imageKey.isBlank()) {
      return null;
    }
    String normalized = imageKey.startsWith("/") ? imageKey.substring(1) : imageKey;
    if (assetBaseUrl == null || assetBaseUrl.isBlank()) {
      return "/" + normalized;
    }
    return assetBaseUrl.replaceAll("/+$", "") + "/" + normalized;
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(seoulClock.instant(), KST);
  }

  private void requireUserAndKey(Long userId, String idempotencyKey) {
    if (userId == null || userId < 1) {
      throw new BusinessException(ErrorCode.AUTH_AUTHENTICATION_REQUIRED);
    }
    if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
      throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
    }
  }

  private <T> T idempotent(
      Long userId,
      String key,
      String apiType,
      String canonicalRequest,
      Class<T> responseType,
      Function<T, Long> resourceId,
      Supplier<T> action) {
    String hash = hash(canonicalRequest);
    var replay = idempotencyService.replayIfPresent(userId, apiType, key, hash);
    if (replay.isPresent()) {
      return deserialize(replay.get().key().getResponseSnapshot(), responseType);
    }
    IdempotencyExecution execution = idempotencyService.start(userId, apiType, key, hash);
    if (execution.replay()) {
      return deserialize(execution.key().getResponseSnapshot(), responseType);
    }
    T response = action.get();
    idempotencyService.succeed(
        execution.key(), 200, serialize(response), "CARD_MARKET", resourceId.apply(response));
    return response;
  }

  private String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
    }
  }

  private String serialize(Object response) {
    try {
      return objectMapper.writeValueAsString(response);
    } catch (Exception exception) {
      throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
    }
  }

  private <T> T deserialize(String snapshot, Class<T> responseType) {
    try {
      return objectMapper.readValue(snapshot, responseType);
    } catch (Exception exception) {
      throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
    }
  }
}
