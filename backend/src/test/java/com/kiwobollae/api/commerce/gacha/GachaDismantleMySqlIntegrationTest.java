package com.kiwobollae.api.commerce.gacha;

import static com.kiwobollae.api.commerce.gacha.GachaTimeZone.KST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.entity.enums.AuthProvider;
import com.kiwobollae.api.auth.entity.enums.UserRole;
import com.kiwobollae.api.auth.entity.enums.UserStatus;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.commerce.gacha.dto.GachaDismantleRequest;
import com.kiwobollae.api.commerce.gacha.entity.TradingCard;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardStatus;
import com.kiwobollae.api.commerce.gacha.repository.TradingCardRepository;
import com.kiwobollae.api.commerce.gacha.repository.UserCardCollectionRepository;
import com.kiwobollae.api.commerce.gacha.repository.UserCardCosmeticRepository;
import com.kiwobollae.api.commerce.gacha.repository.UserCardShardWalletRepository;
import com.kiwobollae.api.commerce.gacha.service.GachaCollectionAcquisitionService;
import com.kiwobollae.api.commerce.gacha.service.GachaCosmeticService;
import com.kiwobollae.api.commerce.gacha.service.GachaDismantleService;
import com.kiwobollae.api.global.exception.BusinessException;
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
      "spring.datasource.url=jdbc:mysql://localhost:3306/kiwobollae_gacha_test"
          + "?createDatabaseIfNotExist=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "app.seed.gacha.enabled=true",
      "app.seed.charge-product.enabled=false",
      "app.seed.product.enabled=false",
      "app.seed.card.enabled=false"
    })
class GachaDismantleMySqlIntegrationTest {

  @Autowired private UserRepository userRepository;
  @Autowired private TradingCardRepository cardRepository;
  @Autowired private UserCardCollectionRepository collectionRepository;
  @Autowired private UserCardShardWalletRepository walletRepository;
  @Autowired private UserCardCosmeticRepository cosmeticRepository;
  @Autowired private GachaCollectionAcquisitionService acquisitionService;
  @Autowired private GachaDismantleService dismantleService;
  @Autowired private GachaCosmeticService cosmeticService;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void concurrentDismantlesAllowOnlyOneRequestAndKeepOneCard() throws Exception {
    User user =
        userRepository.saveAndFlush(
            User.builder()
                .email("gacha-dismantle@example.test")
                .password("encoded-password")
                .nickname("gacha-dismantle")
                .name("가챠분해테스트")
                .provider(AuthProvider.LOCAL)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build());
    TradingCard card =
        cardRepository
            .findAllByStatusAndRarityOrderByDisplayOrderAsc(
                TradingCardStatus.ACTIVE, TradingCardRarity.COMMON)
            .getFirst();
    LocalDateTime now = LocalDateTime.now(KST);
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              acquisitionService.acquireNormal(user.getId(), card.getId(), now);
              acquisitionService.acquireNormal(user.getId(), card.getId(), now);
              acquisitionService.acquireNormal(user.getId(), card.getId(), now);
            });

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<String> first =
          executor.submit(
              () -> dismantleAfterBarrier(user.getId(), card.getId(), "first", ready, start));
      Future<String> second =
          executor.submit(
              () -> dismantleAfterBarrier(user.getId(), card.getId(), "second", ready, start));
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();

      assertThat(List.of(first.get(8, TimeUnit.SECONDS), second.get(8, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("SUCCESS", "GACHA_CARD_KEEP_ONE_REQUIRED");
      assertThat(collectionRepository.findOwnedCount(user.getId(), card.getId())).contains(1);
      assertThat(walletRepository.findById(user.getId()))
          .hasValueSatisfying(
              wallet -> {
                assertThat(wallet.getBalance()).isEqualTo(2);
                assertThat(wallet.getLifetimeEarned()).isEqualTo(2);
                assertThat(wallet.getLifetimeSpent()).isZero();
              });
    } finally {
      start.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void concurrentCosmeticPurchasesSpendShardsAndUnlockOnlyOnce() throws Exception {
    User user = createUser("gacha-cosmetic@example.test", "gacha-cosmetic", "코스메틱테스트");
    TradingCard card =
        cardRepository
            .findAllByStatusAndRarityOrderByDisplayOrderAsc(
                TradingCardStatus.ACTIVE, TradingCardRarity.COMMON)
            .getFirst();
    LocalDateTime now = LocalDateTime.now(KST);
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              for (int count = 0; count < 31; count++) {
                acquisitionService.acquireNormal(user.getId(), card.getId(), now);
              }
            });
    dismantleService.dismantle(
        user.getId(),
        "cosmetic-shard-setup",
        new GachaDismantleRequest(List.of(new GachaDismantleRequest.Item(card.getId(), 30))));

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<String> first =
          executor.submit(() -> purchaseAfterBarrier(user.getId(), "purchase-first", ready, start));
      Future<String> second =
          executor.submit(
              () -> purchaseAfterBarrier(user.getId(), "purchase-second", ready, start));
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();

      assertThat(List.of(first.get(8, TimeUnit.SECONDS), second.get(8, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("SUCCESS", "GACHA_COSMETIC_ALREADY_OWNED");
      assertThat(cosmeticRepository.findAllByUser_Id(user.getId()))
          .singleElement()
          .satisfies(
              cosmetic ->
                  assertThat(cosmetic.getCosmeticCode()).isEqualTo("TITLE_SPROUT_COLLECTOR"));
      assertThat(walletRepository.findById(user.getId()))
          .hasValueSatisfying(
              wallet -> {
                assertThat(wallet.getBalance()).isZero();
                assertThat(wallet.getLifetimeEarned()).isEqualTo(30);
                assertThat(wallet.getLifetimeSpent()).isEqualTo(30);
              });
    } finally {
      start.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void invalidItemRollsBackEntireBatchDismantle() {
    User user = createUser("gacha-rollback@example.test", "gacha-rollback", "분해롤백테스트");
    TradingCard common =
        cardRepository
            .findAllByStatusAndRarityOrderByDisplayOrderAsc(
                TradingCardStatus.ACTIVE, TradingCardRarity.COMMON)
            .getFirst();
    TradingCard hyper =
        cardRepository
            .findAllByStatusAndRarityOrderByDisplayOrderAsc(
                TradingCardStatus.ACTIVE, TradingCardRarity.HYPER_RARE)
            .getFirst();
    LocalDateTime now = LocalDateTime.now(KST);
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              acquisitionService.acquireNormal(user.getId(), common.getId(), now);
              acquisitionService.acquireNormal(user.getId(), common.getId(), now);
              acquisitionService.acquireNormal(user.getId(), hyper.getId(), now);
              acquisitionService.acquireNormal(user.getId(), hyper.getId(), now);
            });

    assertThatThrownBy(
            () ->
                dismantleService.dismantle(
                    user.getId(),
                    "rollback-batch",
                    new GachaDismantleRequest(
                        List.of(
                            new GachaDismantleRequest.Item(common.getId(), 1),
                            new GachaDismantleRequest.Item(hyper.getId(), 1)))))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode().name())
                    .isEqualTo("GACHA_CARD_NOT_DISMANTLABLE"));

    assertThat(collectionRepository.findOwnedCount(user.getId(), common.getId())).contains(2);
    assertThat(collectionRepository.findOwnedCount(user.getId(), hyper.getId())).contains(2);
    assertThat(walletRepository.findById(user.getId())).isEmpty();
  }

  private String dismantleAfterBarrier(
      Long userId, Long cardId, String key, CountDownLatch ready, CountDownLatch start)
      throws Exception {
    ready.countDown();
    if (!start.await(5, TimeUnit.SECONDS)) {
      throw new IllegalStateException("Concurrent dismantle did not start in time.");
    }
    try {
      dismantleService.dismantle(
          userId,
          key,
          new GachaDismantleRequest(List.of(new GachaDismantleRequest.Item(cardId, 2))));
      return "SUCCESS";
    } catch (BusinessException exception) {
      return exception.getErrorCode().name();
    }
  }

  private String purchaseAfterBarrier(
      Long userId, String key, CountDownLatch ready, CountDownLatch start) throws Exception {
    ready.countDown();
    if (!start.await(5, TimeUnit.SECONDS)) {
      throw new IllegalStateException("Concurrent cosmetic purchase did not start in time.");
    }
    try {
      cosmeticService.purchase(userId, key, "TITLE_SPROUT_COLLECTOR");
      return "SUCCESS";
    } catch (BusinessException exception) {
      return exception.getErrorCode().name();
    }
  }

  private User createUser(String email, String nickname, String name) {
    return userRepository.saveAndFlush(
        User.builder()
            .email(email)
            .password("encoded-password")
            .nickname(nickname)
            .name(name)
            .provider(AuthProvider.LOCAL)
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .build());
  }
}
