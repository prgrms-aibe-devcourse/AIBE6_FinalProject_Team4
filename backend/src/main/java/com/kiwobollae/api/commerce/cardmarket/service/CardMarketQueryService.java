package com.kiwobollae.api.commerce.cardmarket.service;

import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketListingResponse;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketNegotiationResponse;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketPageResponse;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketSellableCardResponse;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketTradeResponse;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketWalletResponse;
import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketListing;
import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketNegotiation;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketAssetType;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketListingStatus;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketNegotiationStatus;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketListingRepository;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketNegotiationRepository;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketTradeRepository;
import com.kiwobollae.api.commerce.cardmarket.port.CardMarketPointPort;
import com.kiwobollae.api.commerce.gacha.entity.GoldenCardInstance;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardStatus;
import com.kiwobollae.api.commerce.gacha.repository.GoldenCardInstanceRepository;
import com.kiwobollae.api.commerce.gacha.repository.UserCardCollectionRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CardMarketQueryService {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final CardMarketListingRepository listingRepository;
  private final CardMarketNegotiationRepository negotiationRepository;
  private final CardMarketTradeRepository tradeRepository;
  private final UserCardCollectionRepository collectionRepository;
  private final GoldenCardInstanceRepository goldenInstanceRepository;
  private final CardMarketPointPort pointPort;
  private final CardMarketResponseMapper responseMapper;
  private final CardMarketQueryResponseAssembler responseAssembler;
  private final Clock seoulClock;

  public CardMarketPageResponse<CardMarketListingResponse> getListings(
      CardMarketAssetType assetType, Long cardId, String keyword, Pageable pageable) {
    String normalizedKeyword = normalizeKeyword(keyword);
    Page<CardMarketListing> page =
        listingRepository
            .search(
                CardMarketListingStatus.OPEN,
                assetType,
                cardId,
                normalizedKeyword,
                now(),
                pageable);
    return responseAssembler.listingPage(page);
  }

  public CardMarketListingResponse getListing(Long listingId) {
    CardMarketListing listing = requireListing(listingId);
    if (listing.getStatus() != CardMarketListingStatus.OPEN
        || !listing.getExpiresAt().isAfter(now())
        || listing.getCard().getStatus() != TradingCardStatus.ACTIVE) {
      throw new BusinessException(ErrorCode.CARD_MARKET_LISTING_NOT_FOUND);
    }
    return responseAssembler.listing(listing);
  }

  public CardMarketPageResponse<CardMarketListingResponse> getMyListings(
      Long userId, CardMarketListingStatus status, Pageable pageable) {
    requireUser(userId);
    Page<CardMarketListing> page =
        status == null
            ? listingRepository.findAllBySeller_Id(userId, pageable)
            : listingRepository.findAllBySeller_IdAndStatus(userId, status, pageable);
    return responseAssembler.listingPage(page);
  }

  public CardMarketPageResponse<CardMarketNegotiationResponse> getMySentNegotiations(
      Long userId, Pageable pageable) {
    requireUser(userId);
    return responseAssembler.negotiationPage(
        negotiationRepository.findAllByBuyer_Id(userId, pageable));
  }

  public CardMarketPageResponse<CardMarketNegotiationResponse> getMyReceivedNegotiations(
      Long userId, Pageable pageable) {
    requireUser(userId);
    return responseAssembler.negotiationPage(
        negotiationRepository.findAllByListing_Seller_Id(userId, pageable));
  }

  public CardMarketPageResponse<CardMarketTradeResponse> getMyTrades(
      Long userId, Pageable pageable) {
    requireUser(userId);
    return CardMarketPageResponse.from(
        tradeRepository
            .findAllByBuyer_IdOrSeller_Id(userId, userId, pageable)
            .map(responseMapper::trade));
  }

  public CardMarketNegotiationResponse getMyNegotiation(Long userId, Long negotiationId) {
    requireUser(userId);
    CardMarketNegotiation negotiation =
        negotiationRepository
            .findOwnedDetailsById(negotiationId, userId)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.CARD_MARKET_NEGOTIATION_NOT_FOUND));
    return responseAssembler.negotiation(negotiation);
  }

  public CardMarketWalletResponse getMyWallet(Long userId) {
    requireUser(userId);
    CardMarketPointPort.Balance wallet = pointPort.getBalance(userId);
    long escrowed =
        negotiationRepository.sumEscrowedPoint(userId, CardMarketNegotiationStatus.NEGOTIATING);
    return new CardMarketWalletResponse(
        wallet.paidPoint(),
        wallet.freePoint(),
        escrowed,
        "유상 포인트만 카드 거래에 사용할 수 있습니다.",
        "무상 포인트는 카드 거래에 사용할 수 없습니다.");
  }

  public List<CardMarketSellableCardResponse> getMySellableCards(Long userId) {
    requireUser(userId);
    List<GoldenCardInstance> goldenInstances =
        goldenInstanceRepository.findAllByOwnerUser_IdOrderByIdAsc(userId);
    Map<Long, List<GoldenCardInstance>> goldenByCard =
        goldenInstances.stream().collect(Collectors.groupingBy(instance -> instance.getCard().getId()));

    return collectionRepository.findAllByUser_Id(userId).stream()
        .filter(collection -> collection.getOwnedCount() > 0)
        .filter(collection -> collection.getCard().getStatus() == TradingCardStatus.ACTIVE)
        .filter(
            collection ->
                collection.getCard().getRarity() == TradingCardRarity.HYPER_RARE
                    || collection.getCard().getRarity() == TradingCardRarity.GOLDEN_RARE)
        .map(
            collection -> {
              var card = collection.getCard();
              List<CardMarketSellableCardResponse.GoldenInstance> instances =
                  goldenByCard.getOrDefault(card.getId(), List.of()).stream()
                      .map(
                          instance ->
                              new CardMarketSellableCardResponse.GoldenInstance(
                                  instance.getId(),
                                  instance.getGoldenOriginRank(),
                                  listingRepository.existsByGoldenInstance_IdAndStatus(
                                      instance.getId(), CardMarketListingStatus.OPEN)))
                      .toList();
              int sellableCount =
                  card.getRarity() == TradingCardRarity.HYPER_RARE
                      ? Math.max(0, collection.getOwnedCount() - 1)
                      : (int) instances.stream().filter(instance -> !instance.listed()).count();
              return new CardMarketSellableCardResponse(
                  card.getId(),
                  card.getName(),
                  card.getRarity(),
                  responseMapper.imageUrl(card.getImageKey()),
                  collection.getOwnedCount(),
                  sellableCount,
                  instances);
            })
        .toList();
  }

  private CardMarketListing requireListing(Long listingId) {
    if (listingId == null || listingId < 1) {
      throw new BusinessException(ErrorCode.CARD_MARKET_LISTING_NOT_FOUND);
    }
    return listingRepository
        .findWithDetailsById(listingId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CARD_MARKET_LISTING_NOT_FOUND));
  }

  private void requireUser(Long userId) {
    if (userId == null || userId < 1) {
      throw new BusinessException(ErrorCode.AUTH_AUTHENTICATION_REQUIRED);
    }
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(seoulClock.instant(), KST);
  }

  private String normalizeKeyword(String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return null;
    }
    String normalized = keyword.trim();
    if (normalized.length() > 50) {
      throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
    }
    return normalized;
  }
}
