package com.kiwobollae.api.commerce.cardmarket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketListingCreateRequest;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketProposalRequest;
import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketListing;
import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketNegotiation;
import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketProposal;
import com.kiwobollae.api.commerce.cardmarket.entity.CardMarketTrade;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketAssetType;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketListingStatus;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketMessageCode;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketNegotiationStatus;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketParticipantType;
import com.kiwobollae.api.commerce.cardmarket.port.CardMarketPointPort;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketListingRepository;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketNegotiationRepository;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketProposalRepository;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketTradeRepository;
import com.kiwobollae.api.commerce.gacha.entity.GoldenCardInstance;
import com.kiwobollae.api.commerce.gacha.entity.TradingCard;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardStatus;
import com.kiwobollae.api.commerce.gacha.repository.GoldenCardInstanceRepository;
import com.kiwobollae.api.commerce.gacha.repository.TradingCardRepository;
import com.kiwobollae.api.commerce.gacha.repository.UserCardCollectionRepository;
import com.kiwobollae.api.commerce.service.CommerceAssetUrlResolver;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.infra.entity.IdempotencyKey;
import com.kiwobollae.api.infra.service.IdempotencyExecution;
import com.kiwobollae.api.infra.service.IdempotencyService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class CardMarketCommandServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 6, 0, 0);

  @Mock private CardMarketListingRepository listingRepository;
  @Mock private CardMarketNegotiationRepository negotiationRepository;
  @Mock private CardMarketProposalRepository proposalRepository;
  @Mock private CardMarketTradeRepository tradeRepository;
  @Mock private TradingCardRepository tradingCardRepository;
  @Mock private UserCardCollectionRepository collectionRepository;
  @Mock private GoldenCardInstanceRepository goldenInstanceRepository;
  @Mock private UserRepository userRepository;
  @Mock private CardMarketPointPort pointPort;
  @Mock private CardMarketNotificationService notificationService;
  @Mock private IdempotencyService idempotencyService;
  @Mock private Clock seoulClock;

  private CardMarketCommandService service;
  private User seller;
  private User buyer;

  @BeforeEach
  void setUp() {
    lenient().when(seoulClock.instant()).thenReturn(Instant.parse("2026-08-05T15:00:00Z"));
    CardMarketTradeProcessor tradeProcessor =
        new CardMarketTradeProcessor(
            tradeRepository,
            negotiationRepository,
            collectionRepository,
            goldenInstanceRepository,
            pointPort,
            notificationService);
    CardMarketResponseMapper responseMapper =
        new CardMarketResponseMapper(new CommerceAssetUrlResolver(""));
    CardMarketCommandSupport support =
        new CardMarketCommandSupport(listingRepository, negotiationRepository);
    CardMarketListingCommandHandler listingHandler =
        new CardMarketListingCommandHandler(
            listingRepository,
            tradingCardRepository,
            collectionRepository,
            goldenInstanceRepository,
            userRepository,
            pointPort,
            tradeProcessor,
            responseMapper,
            support,
            seoulClock);
    CardMarketNegotiationCommandHandler negotiationHandler =
        new CardMarketNegotiationCommandHandler(
            negotiationRepository,
            proposalRepository,
            userRepository,
            pointPort,
            notificationService,
            tradeProcessor,
            responseMapper,
            support,
            seoulClock);
    service =
        new CardMarketCommandService(
            listingHandler,
            negotiationHandler,
            new CardMarketIdempotencyExecutor(idempotencyService, new ObjectMapper()));
    seller = user(1L, "판매자");
    buyer = user(2L, "구매자");
    IdempotencyKey key = mock(IdempotencyKey.class);
    when(idempotencyService.replayIfPresent(anyLong(), anyString(), anyString(), anyString()))
        .thenReturn(Optional.empty());
    when(idempotencyService.start(anyLong(), anyString(), anyString(), anyString()))
        .thenReturn(new IdempotencyExecution(key, false));
  }

  @Test
  void createsHyperListingByReservingOnlyDuplicateCard() {
    TradingCard card = card(11L, TradingCardRarity.HYPER_RARE);
    when(tradingCardRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(card));
    when(collectionRepository.decrementKeepingOne(1L, 11L, 1, NOW)).thenReturn(1);
    when(userRepository.getReferenceById(1L)).thenReturn(seller);
    when(listingRepository.save(any(CardMarketListing.class)))
        .thenAnswer(invocation -> withId(invocation.getArgument(0), 101L));

    var response =
        service.createListing(
            1L, "listing-key", new CardMarketListingCreateRequest(11L, null, 1_000L));

    assertThat(response.id()).isEqualTo(101L);
    assertThat(response.assetType()).isEqualTo(CardMarketAssetType.HYPER_RARE);
    assertThat(response.expiresAt()).isEqualTo(NOW.plusDays(7));
    verify(collectionRepository).decrementKeepingOne(1L, 11L, 1, NOW);
    verify(pointPort, never()).reserveOffer(anyLong(), anyLong(), anyLong());
  }

  @Test
  void rejectsCommonCardBeforeChangingOwnership() {
    TradingCard card = card(11L, TradingCardRarity.COMMON);
    when(tradingCardRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(card));

    assertThatThrownBy(
            () ->
                service.createListing(
                    1L,
                    "listing-key",
                    new CardMarketListingCreateRequest(11L, null, 1_000L)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.CARD_MARKET_CARD_NOT_TRADABLE));

    verify(collectionRepository, never())
        .decrementKeepingOne(anyLong(), anyLong(), any(Integer.class), any(LocalDateTime.class));
  }

  @Test
  void createsGoldenListingOnlyForOwnedMatchingInstance() {
    TradingCard card = card(12L, TradingCardRarity.GOLDEN_RARE);
    GoldenCardInstance instance = mock(GoldenCardInstance.class);
    when(instance.getId()).thenReturn(301L);
    when(instance.getOwnerUser()).thenReturn(seller);
    when(instance.getCard()).thenReturn(card);
    when(tradingCardRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(card));
    when(goldenInstanceRepository.findByIdForUpdate(301L)).thenReturn(Optional.of(instance));
    when(listingRepository.existsByGoldenInstance_IdAndStatus(
            301L, CardMarketListingStatus.OPEN))
        .thenReturn(false);
    when(userRepository.getReferenceById(1L)).thenReturn(seller);
    when(listingRepository.save(any(CardMarketListing.class)))
        .thenAnswer(invocation -> withId(invocation.getArgument(0), 102L));

    var response =
        service.createListing(
            1L, "golden-key", new CardMarketListingCreateRequest(12L, 301L, 5_000L));

    assertThat(response.assetType()).isEqualTo(CardMarketAssetType.GOLDEN_RARE);
    assertThat(response.goldenInstanceId()).isEqualTo(301L);
    verify(collectionRepository, never())
        .decrementKeepingOne(anyLong(), anyLong(), any(Integer.class), any(LocalDateTime.class));
  }

  @Test
  void createsOfferWithPaidEscrowAndTwentyFourHourDeadline() {
    CardMarketListing listing = listing(201L, card(11L, TradingCardRarity.HYPER_RARE), 1_000L);
    when(listingRepository.findByIdForUpdate(201L)).thenReturn(Optional.of(listing));
    when(negotiationRepository.existsByListing_IdAndBuyer_IdAndStatus(
            201L, 2L, CardMarketNegotiationStatus.NEGOTIATING))
        .thenReturn(false);
    when(userRepository.getReferenceById(2L)).thenReturn(buyer);
    when(negotiationRepository.saveAndFlush(any(CardMarketNegotiation.class)))
        .thenAnswer(invocation -> withId(invocation.getArgument(0), 401L));

    var response =
        service.createNegotiation(
            2L,
            201L,
            "offer-key",
            new CardMarketProposalRequest(800L, CardMarketMessageCode.READY_TO_BUY));

    assertThat(response.currentPrice()).isEqualTo(800L);
    assertThat(response.turn()).isEqualTo(CardMarketParticipantType.SELLER);
    assertThat(response.expiresAt()).isEqualTo(NOW.plusHours(24));
    verify(pointPort).reserveOffer(2L, 800L, 401L);
    ArgumentCaptor<CardMarketProposal> proposalCaptor =
        ArgumentCaptor.forClass(CardMarketProposal.class);
    verify(proposalRepository).save(proposalCaptor.capture());
    assertThat(proposalCaptor.getValue().getSequenceNo()).isEqualTo(1);
  }

  @Test
  void sellerCounterOfferKeepsBuyerEscrowAndHandsTurnToBuyer() {
    CardMarketListing listing = listing(201L, card(11L, TradingCardRarity.HYPER_RARE), 1_000L);
    CardMarketNegotiation negotiation = negotiation(401L, listing, buyer, 800L, 800L);
    CardMarketProposal buyerOffer = proposal(negotiation, buyer, CardMarketParticipantType.BUYER, 1, 800L);
    when(negotiationRepository.findById(401L)).thenReturn(Optional.of(negotiation));
    when(listingRepository.findByIdForUpdate(201L)).thenReturn(Optional.of(listing));
    when(negotiationRepository.findByIdForUpdate(401L)).thenReturn(Optional.of(negotiation));
    when(proposalRepository.findAllByNegotiation_IdOrderBySequenceNoAsc(401L))
        .thenReturn(List.of(buyerOffer));
    when(userRepository.getReferenceById(1L)).thenReturn(seller);

    var response =
        service.propose(
            1L,
            401L,
            "seller-counter-key",
            new CardMarketProposalRequest(900L, CardMarketMessageCode.CONSIDERING));

    assertThat(response.currentPrice()).isEqualTo(900L);
    assertThat(response.escrowedPaidPoint()).isEqualTo(800L);
    assertThat(response.turn()).isEqualTo(CardMarketParticipantType.BUYER);
    verify(pointPort, never()).reserveOffer(anyLong(), anyLong(), anyLong());
  }

  @Test
  void buyerCounterOfferReservesOnlyAdditionalPaidPoint() {
    CardMarketListing listing = listing(201L, card(11L, TradingCardRarity.HYPER_RARE), 1_000L);
    CardMarketNegotiation negotiation = negotiation(401L, listing, buyer, 900L, 800L);
    ReflectionTestUtils.setField(negotiation, "turn", CardMarketParticipantType.BUYER);
    ReflectionTestUtils.setField(
        negotiation, "currentProposerType", CardMarketParticipantType.SELLER);
    CardMarketProposal buyerOffer = proposal(negotiation, buyer, CardMarketParticipantType.BUYER, 1, 800L);
    CardMarketProposal sellerCounter =
        proposal(negotiation, seller, CardMarketParticipantType.SELLER, 2, 900L);
    when(negotiationRepository.findById(401L)).thenReturn(Optional.of(negotiation));
    when(listingRepository.findByIdForUpdate(201L)).thenReturn(Optional.of(listing));
    when(negotiationRepository.findByIdForUpdate(401L)).thenReturn(Optional.of(negotiation));
    when(proposalRepository.findAllByNegotiation_IdOrderBySequenceNoAsc(401L))
        .thenReturn(List.of(buyerOffer, sellerCounter));
    when(userRepository.getReferenceById(2L)).thenReturn(buyer);

    var response =
        service.propose(
            2L,
            401L,
            "buyer-counter-key",
            new CardMarketProposalRequest(850L, CardMarketMessageCode.MAXIMUM_OFFER));

    assertThat(response.currentPrice()).isEqualTo(850L);
    assertThat(response.escrowedPaidPoint()).isEqualTo(850L);
    assertThat(response.turn()).isEqualTo(CardMarketParticipantType.SELLER);
    verify(pointPort).reserveOffer(2L, 50L, 401L);
  }

  @Test
  void acceptingSellerCounterChargesOnlyEscrowDifference() {
    TradingCard card = card(11L, TradingCardRarity.HYPER_RARE);
    CardMarketListing listing = listing(201L, card, 1_000L);
    CardMarketNegotiation negotiation = negotiation(401L, listing, buyer, 900L, 800L);
    ReflectionTestUtils.setField(negotiation, "turn", CardMarketParticipantType.BUYER);
    ReflectionTestUtils.setField(
        negotiation, "currentProposerType", CardMarketParticipantType.SELLER);
    when(negotiationRepository.findById(401L)).thenReturn(Optional.of(negotiation));
    when(listingRepository.findByIdForUpdate(201L)).thenReturn(Optional.of(listing));
    when(negotiationRepository.findByIdForUpdate(401L)).thenReturn(Optional.of(negotiation));
    when(negotiationRepository.findAllByListingIdAndStatusForUpdate(
            201L, CardMarketNegotiationStatus.NEGOTIATING))
        .thenReturn(List.of(negotiation));
    when(tradeRepository.saveAndFlush(any(CardMarketTrade.class)))
        .thenAnswer(invocation -> withId(invocation.getArgument(0), 502L));

    var response = service.accept(2L, 401L, "accept-key");

    assertThat(response.tradePrice()).isEqualTo(900L);
    assertThat(response.feePoint()).isEqualTo(180L);
    assertThat(response.sellerReceivedPoint()).isEqualTo(720L);
    assertThat(negotiation.getStatus()).isEqualTo(CardMarketNegotiationStatus.ACCEPTED);
    verify(pointPort).settleTrade(2L, 1L, 100L, 720L, 502L, List.of());
    verify(collectionRepository).incrementOwnedCount(2L, 11L, NOW);
    InOrder timing = inOrder(listingRepository, negotiationRepository, seoulClock);
    timing.verify(listingRepository).findByIdForUpdate(201L);
    timing.verify(negotiationRepository).findByIdForUpdate(401L);
    timing.verify(seoulClock).instant();
  }

  @Test
  void acceptingPriceBelowEscrowReleasesExcessInSameSettlement() {
    TradingCard card = card(11L, TradingCardRarity.HYPER_RARE);
    CardMarketListing listing = listing(201L, card, 1_000L);
    CardMarketNegotiation negotiation = negotiation(401L, listing, buyer, 700L, 800L);
    ReflectionTestUtils.setField(negotiation, "turn", CardMarketParticipantType.BUYER);
    ReflectionTestUtils.setField(
        negotiation, "currentProposerType", CardMarketParticipantType.SELLER);
    when(negotiationRepository.findById(401L)).thenReturn(Optional.of(negotiation));
    when(listingRepository.findByIdForUpdate(201L)).thenReturn(Optional.of(listing));
    when(negotiationRepository.findByIdForUpdate(401L)).thenReturn(Optional.of(negotiation));
    when(negotiationRepository.findAllByListingIdAndStatusForUpdate(
            201L, CardMarketNegotiationStatus.NEGOTIATING))
        .thenReturn(List.of(negotiation));
    when(tradeRepository.saveAndFlush(any(CardMarketTrade.class)))
        .thenAnswer(invocation -> withId(invocation.getArgument(0), 503L));

    var response = service.accept(2L, 401L, "accept-lower-key");

    assertThat(response.tradePrice()).isEqualTo(700L);
    assertThat(response.sellerReceivedPoint()).isEqualTo(560L);
    verify(pointPort)
        .settleTrade(
            2L,
            1L,
            0L,
            560L,
            503L,
            List.of(new CardMarketPointPort.OfferRelease(2L, 100L, 401L)));
    verify(collectionRepository).incrementOwnedCount(2L, 11L, NOW);
  }

  @Test
  void buyNowChargesFullPricePaysEightyPercentAndReleasesExistingOffers() {
    TradingCard card = card(11L, TradingCardRarity.HYPER_RARE);
    CardMarketListing listing = listing(201L, card, 1_000L);
    CardMarketNegotiation ownOffer = negotiation(401L, listing, buyer, 900L, 900L);
    when(listingRepository.findByIdForUpdate(201L)).thenReturn(Optional.of(listing));
    when(userRepository.getReferenceById(2L)).thenReturn(buyer);
    when(tradeRepository.saveAndFlush(any(CardMarketTrade.class)))
        .thenAnswer(invocation -> withId(invocation.getArgument(0), 501L));
    when(negotiationRepository.findAllByListingIdAndStatusForUpdate(
            201L, CardMarketNegotiationStatus.NEGOTIATING))
        .thenReturn(List.of(ownOffer));

    var response = service.buyNow(2L, 201L, "buy-key");

    assertThat(response.tradePrice()).isEqualTo(1_000L);
    assertThat(response.feePoint()).isEqualTo(200L);
    assertThat(response.sellerReceivedPoint()).isEqualTo(800L);
    assertThat(listing.getStatus()).isEqualTo(CardMarketListingStatus.SOLD);
    assertThat(ownOffer.getStatus()).isEqualTo(CardMarketNegotiationStatus.LISTING_CLOSED);
    verify(pointPort)
        .settleTrade(
            2L,
            1L,
            1_000L,
            800L,
            501L,
            List.of(new CardMarketPointPort.OfferRelease(2L, 900L, 401L)));
    verify(collectionRepository).incrementOwnedCount(2L, 11L, NOW);
    InOrder timing = inOrder(listingRepository, seoulClock);
    timing.verify(listingRepository).findByIdForUpdate(201L);
    timing.verify(seoulClock).instant();
  }

  @Test
  void rejectsOfferAndReturnsEntireEscrow() {
    CardMarketListing listing = listing(201L, card(11L, TradingCardRarity.HYPER_RARE), 1_000L);
    CardMarketNegotiation negotiation = negotiation(401L, listing, buyer, 800L, 800L);
    when(negotiationRepository.findById(401L)).thenReturn(Optional.of(negotiation));
    when(listingRepository.findByIdForUpdate(201L)).thenReturn(Optional.of(listing));
    when(negotiationRepository.findByIdForUpdate(401L)).thenReturn(Optional.of(negotiation));

    var response = service.reject(1L, 401L, "reject-key");

    assertThat(response.status()).isEqualTo(CardMarketNegotiationStatus.REJECTED);
    assertThat(response.escrowedPaidPoint()).isZero();
    verify(pointPort).releaseOffer(2L, 800L, 401L);
  }

  private CardMarketListing listing(Long id, TradingCard card, long price) {
    return withId(
        CardMarketListing.builder()
            .seller(seller)
            .card(card)
            .assetType(CardMarketAssetType.HYPER_RARE)
            .askingPrice(price)
            .status(CardMarketListingStatus.OPEN)
            .expiresAt(NOW.plusDays(7))
            .version(0L)
            .createdAt(NOW)
            .updatedAt(NOW)
            .build(),
        id);
  }

  private CardMarketNegotiation negotiation(
      Long id, CardMarketListing listing, User offerBuyer, long price, long escrow) {
    return withId(
        CardMarketNegotiation.builder()
            .listing(listing)
            .buyer(offerBuyer)
            .status(CardMarketNegotiationStatus.NEGOTIATING)
            .turn(CardMarketParticipantType.SELLER)
            .currentProposerType(CardMarketParticipantType.BUYER)
            .currentPrice(price)
            .escrowedPaidPoint(escrow)
            .sellerCounterCount(0)
            .nextSequence(2)
            .expiresAt(NOW.plusHours(24))
            .version(0L)
            .createdAt(NOW)
            .updatedAt(NOW)
            .build(),
        id);
  }

  private CardMarketProposal proposal(
      CardMarketNegotiation negotiation,
      User proposer,
      CardMarketParticipantType proposerType,
      int sequence,
      long price) {
    return CardMarketProposal.builder()
        .negotiation(negotiation)
        .proposer(proposer)
        .proposerType(proposerType)
        .sequenceNo(sequence)
        .proposedPrice(price)
        .createdAt(NOW)
        .build();
  }

  private TradingCard card(Long id, TradingCardRarity rarity) {
    return withId(
        TradingCard.builder()
            .code("CARD_" + id)
            .name("테스트 카드 " + id)
            .rarity(rarity)
            .imageKey("cards/" + id + "/image.png")
            .drawWeight(1)
            .displayOrder(id.intValue())
            .status(TradingCardStatus.ACTIVE)
            .build(),
        id);
  }

  private User user(Long id, String nickname) {
    User user = mock(User.class);
    lenient().when(user.getId()).thenReturn(id);
    lenient().when(user.getNickname()).thenReturn(nickname);
    return user;
  }

  private <T> T withId(T entity, Long id) {
    ReflectionTestUtils.setField(entity, "id", id);
    return entity;
  }
}
