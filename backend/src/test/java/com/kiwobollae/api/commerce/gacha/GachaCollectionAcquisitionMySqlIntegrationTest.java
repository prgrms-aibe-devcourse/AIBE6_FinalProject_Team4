package com.kiwobollae.api.commerce.gacha;

import static com.kiwobollae.api.commerce.gacha.GachaTimeZone.KST;
import static org.assertj.core.api.Assertions.assertThat;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.entity.enums.AuthProvider;
import com.kiwobollae.api.auth.entity.enums.UserRole;
import com.kiwobollae.api.auth.entity.enums.UserStatus;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.commerce.gacha.entity.TradingCard;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardStatus;
import com.kiwobollae.api.commerce.gacha.repository.TradingCardRepository;
import com.kiwobollae.api.commerce.gacha.repository.UserCardCollectionRepository;
import com.kiwobollae.api.commerce.gacha.service.GachaCollectionAcquisitionService;
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
class GachaCollectionAcquisitionMySqlIntegrationTest {

  @Autowired private UserRepository userRepository;
  @Autowired private TradingCardRepository tradingCardRepository;
  @Autowired private UserCardCollectionRepository collectionRepository;
  @Autowired private GachaCollectionAcquisitionService acquisitionService;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void concurrentAcquisitionsKeepEachDrawSnapshotOrdered() throws Exception {
    User user =
        userRepository.saveAndFlush(
            User.builder()
                .email("gacha-concurrency@example.test")
                .password("encoded-password")
                .nickname("gacha-concurrency")
                .name("가챠동시성테스트")
                .provider(AuthProvider.LOCAL)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build());
    TradingCard card =
        tradingCardRepository
            .findAllByStatusOrderByDisplayOrderAsc(TradingCardStatus.ACTIVE)
            .getFirst();
    int initialCount = collectionRepository.findOwnedCount(user.getId(), card.getId()).orElse(0);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      Future<Integer> first =
          executor.submit(() -> acquireAfterBarrier(user.getId(), card.getId(), ready, start));
      Future<Integer> second =
          executor.submit(() -> acquireAfterBarrier(user.getId(), card.getId(), ready, start));
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();

      assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(initialCount + 1, initialCount + 2);
      assertThat(collectionRepository.findOwnedCount(user.getId(), card.getId()))
          .contains(initialCount + 2);
    } finally {
      start.countDown();
      executor.shutdownNow();
    }
  }

  private int acquireAfterBarrier(
      Long userId, Long cardId, CountDownLatch ready, CountDownLatch start) throws Exception {
    ready.countDown();
    if (!start.await(5, TimeUnit.SECONDS)) {
      throw new IllegalStateException("Concurrent acquisition did not start in time.");
    }
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    Integer result =
        transaction.execute(
            status -> acquisitionService.acquireNormal(userId, cardId, LocalDateTime.now(KST)));
    if (result == null) {
      throw new IllegalStateException("Concurrent acquisition returned no count.");
    }
    return result;
  }
}
