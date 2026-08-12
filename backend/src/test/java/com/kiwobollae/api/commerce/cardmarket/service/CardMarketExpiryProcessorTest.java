package com.kiwobollae.api.commerce.cardmarket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketListing;
import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketNegotiation;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketAssetType;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketListingStatus;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketNegotiationStatus;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketParticipantType;
import com.kiwobollae.api.commerce.cardmarket.port.CardMarketPointPort;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketListingRepository;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketNegotiationRepository;
import com.kiwobollae.api.commerce.gacha.entity.TradingCard;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardStatus;
import com.kiwobollae.api.commerce.gacha.repository.UserCardCollectionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CardMarketExpiryProcessorTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 6, 0, 0);

  @Mock private CardMarketListingRepository listingRepository;
  @Mock private CardMarketNegotiationRepository negotiationRepository;
  @Mock private UserCardCollectionRepository collectionRepository;
  @Mock private CardMarketPointPort pointPort;
  @Mock private CardMarketNotificationService notificationService;

  private CardMarketExpiryProcessor processor;

  @BeforeEach
  void setUp() {
    processor =
        new CardMarketExpiryProcessor(
            listingRepository,
            negotiationRepository,
            collectionRepository,
            pointPort,
            notificationService,
            Clock.fixed(Instant.parse("2026-08-05T15:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void expiresListingRestoresHyperCardAndReleasesAllOffers() {
    User seller = user(1L);
    TradingCard card = card(11L, TradingCardStatus.ACTIVE);
    CardMarketListing listing = listing(21L, seller, card, NOW.minusSeconds(1));
    CardMarketNegotiation first = negotiation(31L, listing, user(2L), 700L);
    CardMarketNegotiation second = negotiation(32L, listing, user(3L), 800L);
    when(listingRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(listing));
    when(negotiationRepository.findAllByListingIdAndStatusForUpdate(
            21L, CardMarketNegotiationStatus.NEGOTIATING))
        .thenReturn(List.of(second, first));

    processor.expireListing(21L);

    assertThat(listing.getStatus()).isEqualTo(CardMarketListingStatus.EXPIRED);
    assertThat(first.getStatus()).isEqualTo(CardMarketNegotiationStatus.LISTING_CLOSED);
    assertThat(second.getStatus()).isEqualTo(CardMarketNegotiationStatus.LISTING_CLOSED);
    verify(pointPort)
        .releaseOffers(
            List.of(
                new CardMarketPointPort.OfferRelease(2L, 700L, 31L),
                new CardMarketPointPort.OfferRelease(3L, 800L, 32L)));
    verify(collectionRepository).incrementOwnedCount(1L, 11L, NOW);
  }

  @Test
  void leavesListingUntouchedBeforeDeadline() {
    CardMarketListing listing = listing(21L, user(1L), card(11L, TradingCardStatus.ACTIVE), NOW.plusMinutes(1));
    when(listingRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(listing));

    processor.expireListing(21L);

    assertThat(listing.getStatus()).isEqualTo(CardMarketListingStatus.OPEN);
    verify(pointPort, never()).releaseOffers(any());
    verify(collectionRepository, never()).incrementOwnedCount(any(), any(), any());
  }

  @Test
  void hiddenCardClosesListingAndRestoresReservedHyperCard() {
    CardMarketListing listing =
        listing(21L, user(1L), card(11L, TradingCardStatus.HIDDEN), NOW.plusDays(1));
    when(listingRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(listing));
    when(negotiationRepository.findAllByListingIdAndStatusForUpdate(
            21L, CardMarketNegotiationStatus.NEGOTIATING))
        .thenReturn(List.of());

    processor.closeHiddenCardListing(21L);

    assertThat(listing.getStatus()).isEqualTo(CardMarketListingStatus.CANCELLED);
    assertThat(listing.getClosedReason()).isEqualTo("CARD_HIDDEN");
    verify(pointPort).releaseOffers(List.of());
    verify(collectionRepository).incrementOwnedCount(1L, 11L, NOW);
  }

  @Test
  void expiresNegotiationAndReturnsEscrow() {
    CardMarketListing listing =
        listing(21L, user(1L), card(11L, TradingCardStatus.ACTIVE), NOW.plusDays(1));
    CardMarketNegotiation negotiation = negotiation(31L, listing, user(2L), 700L);
    ReflectionTestUtils.setField(negotiation, "expiresAt", NOW.minusSeconds(1));
    when(negotiationRepository.findById(31L)).thenReturn(Optional.of(negotiation));
    when(listingRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(listing));
    when(negotiationRepository.findByIdForUpdate(31L)).thenReturn(Optional.of(negotiation));

    processor.expireNegotiation(31L);

    assertThat(negotiation.getStatus()).isEqualTo(CardMarketNegotiationStatus.EXPIRED);
    assertThat(negotiation.getEscrowedPaidPoint()).isZero();
    verify(pointPort).releaseOffer(2L, 700L, 31L);
  }

  private CardMarketListing listing(
      Long id, User seller, TradingCard card, LocalDateTime expiresAt) {
    CardMarketListing listing =
        CardMarketListing.builder()
            .seller(seller)
            .card(card)
            .assetType(CardMarketAssetType.HYPER_RARE)
            .askingPrice(1_000L)
            .status(CardMarketListingStatus.OPEN)
            .expiresAt(expiresAt)
            .version(0L)
            .createdAt(NOW.minusDays(1))
            .updatedAt(NOW.minusDays(1))
            .build();
    ReflectionTestUtils.setField(listing, "id", id);
    return listing;
  }

  private CardMarketNegotiation negotiation(
      Long id, CardMarketListing listing, User buyer, long escrow) {
    CardMarketNegotiation negotiation =
        CardMarketNegotiation.builder()
            .listing(listing)
            .buyer(buyer)
            .status(CardMarketNegotiationStatus.NEGOTIATING)
            .turn(CardMarketParticipantType.SELLER)
            .currentProposerType(CardMarketParticipantType.BUYER)
            .currentPrice(escrow)
            .escrowedPaidPoint(escrow)
            .sellerCounterCount(0)
            .nextSequence(2)
            .expiresAt(NOW.minusSeconds(1))
            .version(0L)
            .createdAt(NOW.minusHours(1))
            .updatedAt(NOW.minusHours(1))
            .build();
    ReflectionTestUtils.setField(negotiation, "id", id);
    return negotiation;
  }

  private User user(Long id) {
    User user = mock(User.class);
    lenient().when(user.getId()).thenReturn(id);
    return user;
  }

  private TradingCard card(Long id, TradingCardStatus status) {
    TradingCard card =
        TradingCard.builder()
            .code("CARD_" + id)
            .name("테스트 카드")
            .rarity(TradingCardRarity.HYPER_RARE)
            .drawWeight(1)
            .displayOrder(1)
            .status(status)
            .build();
    ReflectionTestUtils.setField(card, "id", id);
    return card;
  }
}
