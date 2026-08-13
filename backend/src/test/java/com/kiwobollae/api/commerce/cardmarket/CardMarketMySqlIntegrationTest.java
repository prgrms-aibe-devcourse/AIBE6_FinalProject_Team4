package com.kiwobollae.api.commerce.cardmarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.entity.enums.AuthProvider;
import com.kiwobollae.api.auth.entity.enums.UserRole;
import com.kiwobollae.api.auth.entity.enums.UserStatus;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketListingCreateRequest;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketProposalRequest;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketListingStatus;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketMessageCode;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketNegotiationStatus;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketListingRepository;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketNegotiationRepository;
import com.kiwobollae.api.commerce.cardmarket.repository.CardMarketTradeRepository;
import com.kiwobollae.api.commerce.cardmarket.service.CardMarketCommandService;
import com.kiwobollae.api.commerce.gacha.entity.TradingCard;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardStatus;
import com.kiwobollae.api.commerce.gacha.repository.TradingCardRepository;
import com.kiwobollae.api.commerce.gacha.repository.UserCardCollectionRepository;
import com.kiwobollae.api.commerce.gacha.service.GachaCollectionAcquisitionService;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.point.entity.enums.CurrencyType;
import com.kiwobollae.api.point.entity.enums.PointRefType;
import com.kiwobollae.api.point.entity.enums.PointTxType;
import com.kiwobollae.api.point.repository.PointTransactionRepository;
import com.kiwobollae.api.point.service.WalletService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ActiveProfiles({"test", "local"})
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "spring.datasource.url=jdbc:mysql://localhost:3306/kiwobollae_card_market_test"
          + "?createDatabaseIfNotExist=true&serverTimezone=Asia%2FSeoul&characterEncoding=UTF-8",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "app.seed.gacha.enabled=true",
      "app.seed.charge-product.enabled=false",
      "app.seed.product.enabled=false",
      "app.seed.card.enabled=false"
    })
class CardMarketMySqlIntegrationTest {

  @Autowired private UserRepository userRepository;
  @Autowired private TradingCardRepository cardRepository;
  @Autowired private UserCardCollectionRepository collectionRepository;
  @Autowired private CardMarketListingRepository listingRepository;
  @Autowired private CardMarketNegotiationRepository negotiationRepository;
  @Autowired private CardMarketTradeRepository tradeRepository;
  @Autowired private PointTransactionRepository pointTransactionRepository;
  @Autowired private GachaCollectionAcquisitionService acquisitionService;
  @Autowired private WalletService walletService;
  @Autowired private CardMarketCommandService commandService;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void ownOfferIsReleasedBeforeBuyNowAndTradeSettlesAtomically() {
    User seller = createUser("market-seller-one@example.test", "market-seller-one");
    User buyer = createUser("market-buyer-one@example.test", "market-buyer-one");
    TradingCard hyper = hyperCard();
    acquire(seller, hyper, 2);
    addPaidPoint(buyer, 1_000L, 9_001L);

    var listing =
        commandService.createListing(
            seller.getId(),
            "market-listing-one",
            new CardMarketListingCreateRequest(hyper.getId(), null, 1_000L));
    var offer =
        commandService.createNegotiation(
            buyer.getId(),
            listing.id(),
            "market-offer-one",
            new CardMarketProposalRequest(900L, CardMarketMessageCode.READY_TO_BUY));

    assertThat(walletService.getWallet(buyer.getId()).paidPoint()).isEqualTo(100L);

    var trade = commandService.buyNow(buyer.getId(), listing.id(), "market-buy-one");

    assertThat(trade.tradePrice()).isEqualTo(1_000L);
    assertThat(trade.feePoint()).isEqualTo(200L);
    assertThat(trade.sellerReceivedPoint()).isEqualTo(800L);
    assertThat(walletService.getWallet(buyer.getId()).paidPoint()).isZero();
    assertThat(walletService.getWallet(buyer.getId()).freePoint()).isZero();
    assertThat(walletService.getWallet(seller.getId()).paidPoint()).isEqualTo(800L);
    assertThat(collectionRepository.findOwnedCount(seller.getId(), hyper.getId())).contains(1);
    assertThat(collectionRepository.findOwnedCount(buyer.getId(), hyper.getId())).contains(1);
    assertThat(listingRepository.findById(listing.id()))
        .hasValueSatisfying(
            saved -> assertThat(saved.getStatus()).isEqualTo(CardMarketListingStatus.SOLD));
    assertThat(negotiationRepository.findById(offer.id()))
        .hasValueSatisfying(
            saved ->
                assertThat(saved.getStatus())
                    .isEqualTo(CardMarketNegotiationStatus.LISTING_CLOSED));
    assertThat(tradeRepository.findById(trade.id())).isPresent();
    assertThat(pointTransactionRepository.findAll())
        .filteredOn(transaction -> transaction.getRefId() != null)
        .extracting(transaction -> transaction.getType())
        .contains(
            PointTxType.MARKET_ESCROW,
            PointTxType.MARKET_RELEASE,
            PointTxType.MARKET_PURCHASE,
            PointTxType.MARKET_SALE);
  }

  @Test
  void insufficientPaidPointRollsBackTradeAndCardTransfer() {
    User seller = createUser("market-seller-two@example.test", "market-seller-two");
    User buyer = createUser("market-buyer-two@example.test", "market-buyer-two");
    TradingCard hyper = hyperCard();
    acquire(seller, hyper, 2);
    addPaidPoint(buyer, 99L, 9_002L);
    var listing =
        commandService.createListing(
            seller.getId(),
            "market-listing-two",
            new CardMarketListingCreateRequest(hyper.getId(), null, 1_000L));
    long tradeCountBefore = tradeRepository.count();

    assertThatThrownBy(
            () -> commandService.buyNow(buyer.getId(), listing.id(), "market-buy-two"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.POINT_INSUFFICIENT_BALANCE));

    assertThat(tradeRepository.count()).isEqualTo(tradeCountBefore);
    assertThat(listingRepository.findById(listing.id()))
        .hasValueSatisfying(
            saved -> assertThat(saved.getStatus()).isEqualTo(CardMarketListingStatus.OPEN));
    assertThat(collectionRepository.findOwnedCount(seller.getId(), hyper.getId())).contains(1);
    assertThat(collectionRepository.findOwnedCount(buyer.getId(), hyper.getId())).isEmpty();
    assertThat(walletService.getWallet(buyer.getId()).paidPoint()).isEqualTo(99L);
    assertThat(walletService.getWallet(seller.getId()).paidPoint()).isZero();
  }

  @Test
  void concurrentBuyNowAllowsExactlyOneBuyerAndOneSettlement() throws Exception {
    User seller = createUser("market-seller-three@example.test", "market-seller-three");
    User firstBuyer = createUser("market-buyer-three-a@example.test", "market-buyer-three-a");
    User secondBuyer = createUser("market-buyer-three-b@example.test", "market-buyer-three-b");
    TradingCard hyper = hyperCard();
    acquire(seller, hyper, 2);
    addPaidPoint(firstBuyer, 1_000L, 9_003L);
    addPaidPoint(secondBuyer, 1_000L, 9_004L);
    var listing =
        commandService.createListing(
            seller.getId(),
            "market-listing-three",
            new CardMarketListingCreateRequest(hyper.getId(), null, 1_000L));
    long tradeCountBefore = tradeRepository.count();
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<String> first =
          executor.submit(
              () ->
                  buyAfterBarrier(
                      firstBuyer.getId(), listing.id(), "market-buy-three-a", ready, start));
      Future<String> second =
          executor.submit(
              () ->
                  buyAfterBarrier(
                      secondBuyer.getId(), listing.id(), "market-buy-three-b", ready, start));
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();

      assertThat(List.of(first.get(8, TimeUnit.SECONDS), second.get(8, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("SUCCESS", "CARD_MARKET_LISTING_NOT_OPEN");
      assertThat(tradeRepository.count()).isEqualTo(tradeCountBefore + 1);
      assertThat(walletService.getWallet(seller.getId()).paidPoint()).isEqualTo(800L);
      assertThat(
              walletService.getWallet(firstBuyer.getId()).paidPoint()
                  + walletService.getWallet(secondBuyer.getId()).paidPoint())
          .isEqualTo(1_000L);
      int firstOwned =
          collectionRepository.findOwnedCount(firstBuyer.getId(), hyper.getId()).orElse(0);
      int secondOwned =
          collectionRepository.findOwnedCount(secondBuyer.getId(), hyper.getId()).orElse(0);
      assertThat(firstOwned + secondOwned).isEqualTo(1);
      assertThat(collectionRepository.findOwnedCount(seller.getId(), hyper.getId())).contains(1);
    } finally {
      start.countDown();
      executor.shutdownNow();
    }
  }

  private User createUser(String email, String nickname) {
    User user =
        userRepository.saveAndFlush(
            User.builder()
                .email(email)
                .password("encoded-password")
                .nickname(nickname)
                .name("거래소 테스트")
                .provider(AuthProvider.LOCAL)
                .role(UserRole.USER)
                .level(1)
                .status(UserStatus.ACTIVE)
                .build());
    walletService.createWallet(user);
    return user;
  }

  private TradingCard hyperCard() {
    return cardRepository
        .findAllByStatusAndRarityOrderByDisplayOrderAsc(
            TradingCardStatus.ACTIVE, TradingCardRarity.HYPER_RARE)
        .getFirst();
  }

  private void acquire(User user, TradingCard card, int count) {
    LocalDateTime now = LocalDateTime.now();
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              for (int sequence = 0; sequence < count; sequence++) {
                acquisitionService.acquireNormal(user.getId(), card.getId(), now);
              }
            });
  }

  private void addPaidPoint(User user, long amount, long referenceId) {
    walletService.applyDelta(
        user.getId(),
        PointTxType.ADMIN_ADJUST,
        CurrencyType.PAID,
        amount,
        PointRefType.ADMIN,
        referenceId);
  }

  private String buyAfterBarrier(
      Long buyerUserId,
      Long listingId,
      String idempotencyKey,
      CountDownLatch ready,
      CountDownLatch start)
      throws Exception {
    ready.countDown();
    if (!start.await(5, TimeUnit.SECONDS)) {
      throw new IllegalStateException("Concurrent purchase did not start in time.");
    }
    try {
      commandService.buyNow(buyerUserId, listingId, idempotencyKey);
      return "SUCCESS";
    } catch (BusinessException exception) {
      return exception.getErrorCode().name();
    }
  }
}
