package com.kiwobollae.api.commerce.cardmarket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketListing;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketListingStatus;
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
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CardMarketQueryServiceTest {

  @Test
  void hidesClosedOrHiddenListingFromPublicDetail() {
    CardMarketListingRepository listingRepository = mock(CardMarketListingRepository.class);
    CardMarketQueryService service =
        new CardMarketQueryService(
            listingRepository,
            mock(CardMarketNegotiationRepository.class),
            mock(CardMarketProposalRepository.class),
            mock(CardMarketTradeRepository.class),
            mock(UserCardCollectionRepository.class),
            mock(GoldenCardInstanceRepository.class),
            mock(CardMarketPointPort.class));
    CardMarketListing listing = mock(CardMarketListing.class);
    TradingCard card = mock(TradingCard.class);
    when(listingRepository.findWithDetailsById(1L)).thenReturn(Optional.of(listing));
    when(listing.getStatus()).thenReturn(CardMarketListingStatus.OPEN);
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
        new CardMarketQueryService(
            listingRepository,
            mock(CardMarketNegotiationRepository.class),
            mock(CardMarketProposalRepository.class),
            mock(CardMarketTradeRepository.class),
            collectionRepository,
            mock(GoldenCardInstanceRepository.class),
            mock(CardMarketPointPort.class));
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
}
