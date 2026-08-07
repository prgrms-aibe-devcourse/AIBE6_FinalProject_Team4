package com.kiwobollae.api.commerce.cardmarket.service;

import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketListingResponse;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketNegotiationResponse;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketPageResponse;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketProposalResponse;
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
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketProposalRepository;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketTradeRepository;
import com.kiwobollae.api.commerce.cardmarket.port.CardMarketPointPort;
import com.kiwobollae.api.commerce.gacha.entity.GoldenCardInstance;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardStatus;
import com.kiwobollae.api.commerce.gacha.repository.GoldenCardInstanceRepository;
import com.kiwobollae.api.commerce.gacha.repository.UserCardCollectionRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CardMarketQueryService {

  private final CardMarketListingRepository listingRepository;
  private final CardMarketNegotiationRepository negotiationRepository;
  private final CardMarketProposalRepository proposalRepository;
  private final CardMarketTradeRepository tradeRepository;
  private final UserCardCollectionRepository collectionRepository;
  private final GoldenCardInstanceRepository goldenInstanceRepository;
  private final CardMarketPointPort pointPort;

  @Value("${app.asset.base-url:}")
  private String assetBaseUrl;

  public CardMarketPageResponse<CardMarketListingResponse> getListings(
      CardMarketAssetType assetType, Long cardId, Pageable pageable) {
    Page<CardMarketListingResponse> page =
        listingRepository
            .search(CardMarketListingStatus.OPEN, assetType, cardId, pageable)
            .map(this::listingResponse);
    return CardMarketPageResponse.from(page);
  }

  public CardMarketListingResponse getListing(Long listingId) {
    CardMarketListing listing = requireListing(listingId);
    if (listing.getStatus() != CardMarketListingStatus.OPEN
        || listing.getCard().getStatus() != TradingCardStatus.ACTIVE) {
      throw new BusinessException(ErrorCode.CARD_MARKET_LISTING_NOT_FOUND);
    }
    return listingResponse(listing);
  }

  public List<CardMarketListingResponse> getMyListings(Long userId) {
    requireUser(userId);
    return listingRepository.findAllBySeller_IdOrderByCreatedAtDesc(userId).stream()
        .map(this::listingResponse)
        .toList();
  }

  public List<CardMarketNegotiationResponse> getMySentNegotiations(Long userId) {
    requireUser(userId);
    return negotiationRepository.findAllByBuyer_IdOrderByUpdatedAtDesc(userId).stream()
        .map(this::negotiationResponse)
        .toList();
  }

  public List<CardMarketNegotiationResponse> getMyReceivedNegotiations(Long userId) {
    requireUser(userId);
    return negotiationRepository
        .findAllByListing_Seller_IdOrderByUpdatedAtDesc(userId).stream()
        .map(this::negotiationResponse)
        .toList();
  }

  public List<CardMarketTradeResponse> getMyTrades(Long userId) {
    requireUser(userId);
    return tradeRepository.findAllByBuyer_IdOrSeller_IdOrderByCompletedAtDesc(userId, userId).stream()
        .map(trade -> CardMarketTradeResponse.from(trade, imageUrl(trade.getImageKeySnapshot())))
        .toList();
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
                  imageUrl(card.getImageKey()),
                  collection.getOwnedCount(),
                  sellableCount,
                  instances);
            })
        .toList();
  }

  private CardMarketListingResponse listingResponse(CardMarketListing listing) {
    long offerCount =
        negotiationRepository.countByListing_IdAndStatus(
            listing.getId(), CardMarketNegotiationStatus.NEGOTIATING);
    return CardMarketListingResponse.from(
        listing, imageUrl(listing.getCard().getImageKey()), offerCount);
  }

  private CardMarketNegotiationResponse negotiationResponse(CardMarketNegotiation negotiation) {
    List<CardMarketProposalResponse> proposals =
        proposalRepository.findAllByNegotiation_IdOrderBySequenceNoAsc(negotiation.getId()).stream()
            .map(CardMarketProposalResponse::from)
            .toList();
    return CardMarketNegotiationResponse.from(
        negotiation, imageUrl(negotiation.getListing().getCard().getImageKey()), proposals);
  }

  private CardMarketListing requireListing(Long listingId) {
    if (listingId == null || listingId < 1) {
      throw new BusinessException(ErrorCode.CARD_MARKET_LISTING_NOT_FOUND);
    }
    return listingRepository
        .findWithDetailsById(listingId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CARD_MARKET_LISTING_NOT_FOUND));
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

  private void requireUser(Long userId) {
    if (userId == null || userId < 1) {
      throw new BusinessException(ErrorCode.AUTH_AUTHENTICATION_REQUIRED);
    }
  }
}
