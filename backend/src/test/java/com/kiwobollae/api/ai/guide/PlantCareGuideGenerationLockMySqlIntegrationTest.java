package com.kiwobollae.api.ai.guide;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "spring.datasource.url=jdbc:mysql://localhost:3306/kiwobollae_ai_test"
          + "?createDatabaseIfNotExist=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
class PlantCareGuideGenerationLockMySqlIntegrationTest {

  private static final long WAIT_SECONDS = 10L;
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 6, 10, 0);
  private static final Duration LEASE_DURATION = Duration.ofMinutes(1);
  private static final PlantCareGuideGenerationKey KEY =
      new PlantCareGuideGenerationKey("청상추", 1, "a".repeat(64));

  @Autowired private PlantCareGuideGenerationLockStore lockStore;

  @Autowired private PlantCareGuideGenerationLockRepository repository;

  @BeforeEach
  void resetLocks() {
    repository.deleteAllInBatch();
  }

  @Test
  void allowsOnlyOneConcurrentOwnerForTheSameCacheKey() throws Exception {
    int threads = 20;
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    List<Callable<Boolean>> calls =
        IntStream.range(0, threads)
            .mapToObj(
                ignored ->
                    (Callable<Boolean>)
                        () -> {
                          ready.countDown();
                          start.await(WAIT_SECONDS, TimeUnit.SECONDS);
                          return lockStore.tryAcquire(KEY, NOW, LEASE_DURATION).isPresent();
                        })
            .toList();
    List<Future<Boolean>> futures = calls.stream().map(executor::submit).toList();

    try {
      assertThat(ready.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
      start.countDown();

      long acquired = 0;
      List<Throwable> failures = new ArrayList<>();
      for (Future<Boolean> future : futures) {
        try {
          if (future.get(WAIT_SECONDS, TimeUnit.SECONDS)) {
            acquired++;
          }
        } catch (ExecutionException exception) {
          failures.add(exception.getCause());
        }
      }

      assertThat(failures).isEmpty();
      assertThat(acquired).isEqualTo(1);
      assertThat(repository.count()).isEqualTo(1);
    } finally {
      executor.shutdown();
      assertThat(executor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void recoversExpiredLeaseWithoutLettingThePreviousOwnerDeleteIt() {
    PlantCareGuideGenerationLockStore.Lease first =
        lockStore.tryAcquire(KEY, NOW, LEASE_DURATION).orElseThrow();

    assertThat(lockStore.tryAcquire(KEY, NOW.plusSeconds(59), LEASE_DURATION)).isEmpty();

    PlantCareGuideGenerationLockStore.Lease recovered =
        lockStore.tryAcquire(KEY, NOW.plus(LEASE_DURATION), LEASE_DURATION).orElseThrow();

    lockStore.release(first);
    assertThat(repository.count()).isEqualTo(1);

    lockStore.release(recovered);
    assertThat(repository.count()).isZero();
  }
}
