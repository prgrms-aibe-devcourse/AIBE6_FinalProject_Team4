package com.kiwobollae.api.commerce.cardmarket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketListing;
import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketNegotiation;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketListingStatus;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketNegotiationStatus;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketParticipantType;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketListingRepository;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketNegotiationRepository;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketProposalRepository;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketTradeRepository;
import com.kiwobollae.api.commerce.cardmarket.port.CardMarketPointPort;
import com.kiwobollae.api.commerce.gacha.entity.TradingCard;
import com.kiwobollae.api.commerce.gacha.entity.UserCardCollection;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardStatus;
import com.kiwobollae.api.commerce.gacha.repository.GoldenCardInstanceRepository;
import com.kiwobollae.api.commerce.gacha.repository.UserCardCollectionRepository;
import com.kiwobollae.api.commerce.service.CommerceAssetUrlResolver;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class CardMarketQueryServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-08-05T15:00:00Z"), ZoneOffset.UTC);
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 6, 0, 0);

  @Test
  void hidesClosedOrHiddenListingFromPublicDetail() {
    CardMarketListingRepository listingRepository = mock(CardMarketListingRepository.class);
    CardMarketQueryService service =
        queryService(
            listingRepository,
            mock(CardMarketNegotiationRepository.class),
            mock(CardMarketProposalRepository.class),
            mock(CardMarketTradeRepository.class),
            mock(UserCardCollectionRepository.class),
            mock(GoldenCardInstanceRepository.class),
            mock(CardMarketPointPort.class),
            responseMapper(),
            CLOCK);
    CardMarketListing listing = mock(CardMarketListing.class);
    TradingCard card = mock(TradingCard.class);
    when(listingRepository.findWithDetailsById(1L)).thenReturn(Optional.of(listing));
    when(listing.getStatus()).thenReturn(CardMarketListingStatus.OPEN);
    when(listing.getExpiresAt()).thenReturn(NOW.plusMinutes(1));
    when(listing.getCard()).thenReturn(card);
    when(card.getStatus()).thenReturn(TradingCardStatus.HIDDEN);

    assertThatThrownBy(() -> service.getListing(1L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.CARD_MARKET_LISTING_NOT_FOUND));
  }

  @Test
  void excludesHiddenCardsFromNewSaleCandidates() {
    CardMarketListingRepository listingRepository = mock(CardMarketListingRepository.class);
    UserCardCollectionRepository collectionRepository =
        mock(UserCardCollectionRepository.class);
    CardMarketQueryService service =
        queryService(
            listingRepository,
            mock(CardMarketNegotiationRepository.class),
            mock(CardMarketProposalRepository.class),
            mock(CardMarketTradeRepository.class),
            collectionRepository,
            mock(GoldenCardInstanceRepository.class),
            mock(CardMarketPointPort.class),
            responseMapper(),
            CLOCK);
    UserCardCollection activeCollection = mock(UserCardCollection.class);
    UserCardCollection hiddenCollection = mock(UserCardCollection.class);
    TradingCard active = mock(TradingCard.class);
    TradingCard hidden = mock(TradingCard.class);
    when(collectionRepository.findAllByUser_Id(7L))
        .thenReturn(List.of(activeCollection, hiddenCollection));
    when(activeCollection.getOwnedCount()).thenReturn(3);
    when(activeCollection.getCard()).thenReturn(active);
    when(active.getId()).thenReturn(11L);
    when(active.getName()).thenReturn("활성 하이퍼");
    when(active.getStatus()).thenReturn(TradingCardStatus.ACTIVE);
    when(active.getRarity()).thenReturn(TradingCardRarity.HYPER_RARE);
    when(hiddenCollection.getOwnedCount()).thenReturn(5);
    when(hiddenCollection.getCard()).thenReturn(hidden);
    when(hidden.getStatus()).thenReturn(TradingCardStatus.HIDDEN);

    var result = service.getMySellableCards(7L);

    assertThat(result).singleElement().satisfies(card -> assertThat(card.cardId()).isEqualTo(11L));
    assertThat(result.getFirst().sellableCount()).isEqualTo(2);
  }

  @Test
  void hidesExpiredListingFromPublicDetailBeforeSchedulerRuns() {
    CardMarketListingRepository listingRepository = mock(CardMarketListingRepository.class);
    CardMarketQueryService service =
        queryService(
            listingRepository,
            mock(CardMarketNegotiationRepository.class),
            mock(CardMarketProposalRepository.class),
            mock(CardMarketTradeRepository.class),
            mock(UserCardCollectionRepository.class),
            mock(GoldenCardInstanceRepository.class),
            mock(CardMarketPointPort.class),
            responseMapper(),
            CLOCK);
    CardMarketListing listing = mock(CardMarketListing.class);
    TradingCard card = mock(TradingCard.class);
    when(listingRepository.findWithDetailsById(1L)).thenReturn(Optional.of(listing));
    when(listing.getStatus()).thenReturn(CardMarketListingStatus.OPEN);
    when(listing.getExpiresAt()).thenReturn(NOW);
    when(listing.getCard()).thenReturn(card);
    when(card.getStatus()).thenReturn(TradingCardStatus.ACTIVE);

    assertThatThrownBy(() -> service.getListing(1L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.CARD_MARKET_LISTING_NOT_FOUND));
  }

  @Test
  void loadsOfferCountsForListingPageWithOneBatchQuery() {
    CardMarketListingRepository listingRepository = mock(CardMarketListingRepository.class);
    CardMarketNegotiationRepository negotiationRepository =
        mock(CardMarketNegotiationRepository.class);
    CardMarketQueryService service =
        queryService(
            listingRepository,
            negotiationRepository,
            mock(CardMarketProposalRepository.class),
            mock(CardMarketTradeRepository.class),
            mock(UserCardCollectionRepository.class),
            mock(GoldenCardInstanceRepository.class),
            mock(CardMarketPointPort.class),
            responseMapper(),
            CLOCK);
    CardMarketListing listing = mock(CardMarketListing.class);
    TradingCard card = mock(TradingCard.class);
    User seller = mock(User.class);
    CardMarketNegotiationRepository.ListingOfferCount count =
        mock(CardMarketNegotiationRepository.ListingOfferCount.class);
    PageRequest pageable = PageRequest.of(0, 20);
    when(listing.getId()).thenReturn(10L);
    when(listing.getCard()).thenReturn(card);
    when(listing.getSeller()).thenReturn(seller);
    when(card.getId()).thenReturn(20L);
    when(card.getStatus()).thenReturn(TradingCardStatus.ACTIVE);
    when(listingRepository.search(
            CardMarketListingStatus.OPEN, null, null, null, NOW, pageable))
        .thenReturn(new PageImpl<>(List.of(listing), pageable, 1));
    when(count.getListingId()).thenReturn(10L);
    when(count.getOfferCount()).thenReturn(3L);
    when(negotiationRepository.countByListingIdsAndStatus(
            List.of(10L), CardMarketNegotiationStatus.NEGOTIATING))
        .thenReturn(List.of(count));

    var result = service.getListings(null, null, null, pageable);

    assertThat(result.content()).singleElement().satisfies(item -> assertThat(item.activeOfferCount()).isEqualTo(3L));
    verify(negotiationRepository)
        .countByListingIdsAndStatus(List.of(10L), CardMarketNegotiationStatus.NEGOTIATING);
    verify(negotiationRepository, never())
        .countByListing_IdAndStatus(10L, CardMarketNegotiationStatus.NEGOTIATING);
  }

  @Test
  void loadsNegotiationProposalsWithOneBatchQuery() {
    CardMarketNegotiationRepository negotiationRepository =
        mock(CardMarketNegotiationRepository.class);
    CardMarketProposalRepository proposalRepository = mock(CardMarketProposalRepository.class);
    CardMarketQueryService service =
        queryService(
            mock(CardMarketListingRepository.class),
            negotiationRepository,
            proposalRepository,
            mock(CardMarketTradeRepository.class),
            mock(UserCardCollectionRepository.class),
            mock(GoldenCardInstanceRepository.class),
            mock(CardMarketPointPort.class),
            responseMapper(),
            CLOCK);
    CardMarketNegotiation negotiation = mock(CardMarketNegotiation.class);
    CardMarketListing listing = mock(CardMarketListing.class);
    TradingCard card = mock(TradingCard.class);
    User buyer = mock(User.class);
    User seller = mock(User.class);
    PageRequest pageable = PageRequest.of(0, 20);
    when(negotiation.getId()).thenReturn(30L);
    when(negotiation.getListing()).thenReturn(listing);
    when(negotiation.getBuyer()).thenReturn(buyer);
    when(negotiation.getStatus()).thenReturn(CardMarketNegotiationStatus.NEGOTIATING);
    when(negotiation.getTurn()).thenReturn(CardMarketParticipantType.SELLER);
    when(negotiation.getCurrentProposerType()).thenReturn(CardMarketParticipantType.BUYER);
    when(listing.getId()).thenReturn(10L);
    when(listing.getCard()).thenReturn(card);
    when(listing.getSeller()).thenReturn(seller);
    when(card.getId()).thenReturn(20L);
    when(negotiationRepository.findAllByBuyer_Id(2L, pageable))
        .thenReturn(new PageImpl<>(List.of(negotiation), pageable, 1));
    when(proposalRepository.findAllByNegotiation_IdInOrderByNegotiation_IdAscSequenceNoAsc(
            List.of(30L)))
        .thenReturn(List.of());

    var result = service.getMySentNegotiations(2L, pageable);

    assertThat(result.content()).singleElement().satisfies(item -> assertThat(item.id()).isEqualTo(30L));
    verify(proposalRepository)
        .findAllByNegotiation_IdInOrderByNegotiation_IdAscSequenceNoAsc(List.of(30L));
    verify(proposalRepository, never()).findAllByNegotiation_IdOrderBySequenceNoAsc(30L);
  }

  private static CardMarketResponseMapper responseMapper() {
    return new CardMarketResponseMapper(new CommerceAssetUrlResolver(""));
  }

  private static CardMarketQueryService queryService(
      CardMarketListingRepository listingRepository,
      CardMarketNegotiationRepository negotiationRepository,
      CardMarketProposalRepository proposalRepository,
      CardMarketTradeRepository tradeRepository,
      UserCardCollectionRepository collectionRepository,
      GoldenCardInstanceRepository goldenInstanceRepository,
      CardMarketPointPort pointPort,
      CardMarketResponseMapper responseMapper,
      Clock clock) {
    return new CardMarketQueryService(
        listingRepository,
        negotiationRepository,
        tradeRepository,
        collectionRepository,
        goldenInstanceRepository,
        pointPort,
        responseMapper,
        new CardMarketQueryResponseAssembler(
            negotiationRepository, proposalRepository, responseMapper),
        clock);
  }
}
