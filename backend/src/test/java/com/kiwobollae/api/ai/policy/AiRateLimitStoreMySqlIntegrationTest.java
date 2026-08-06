package com.kiwobollae.api.ai.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.kiwobollae.api.ai.config.AiPolicyProperties;
import com.kiwobollae.api.global.exception.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
class AiRateLimitStoreMySqlIntegrationTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final long WAIT_SECONDS = 10L;
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 5, 10, 0);
  private static final Duration WINDOW = Duration.ofMinutes(1);

  @Autowired private AiRateLimitStore rateLimitStore;

  @Autowired private AiRateLimitWindowRepository repository;

  @Autowired private AiGlobalRateLimitWindowRepository globalRepository;

  @BeforeEach
  void resetWindows() {
    repository.deleteAllInBatch();
    globalRepository.deleteAllInBatch();
  }

  @Test
  void allowsUpToTheLimitThenReportsRetryAfter() {
    assertThat(consume(1L, AiFeature.PLANT_CHAT, NOW)).isEmpty();
    assertThat(consume(1L, AiFeature.PLANT_CHAT, NOW)).isEmpty();

    // 한도(2)를 다 쓴 뒤에는 창이 끝날 때까지 남은 시간이 나온다.
    assertThat(consume(1L, AiFeature.PLANT_CHAT, NOW)).contains(60L);
    assertThat(consume(1L, AiFeature.PLANT_CHAT, NOW.plusSeconds(45))).contains(15L);
  }

  @Test
  void countsSeparatelyPerUserAndFeature() {
    consume(1L, AiFeature.PLANT_CHAT, NOW);
    consume(1L, AiFeature.PLANT_CHAT, NOW);

    assertThat(consume(1L, AiFeature.JOURNAL_GUIDE, NOW)).isEmpty();
    assertThat(consume(2L, AiFeature.PLANT_CHAT, NOW)).isEmpty();
    assertThat(consume(1L, AiFeature.PLANT_CHAT, NOW)).isPresent();
  }

  @Test
  void enforcesGlobalLimitAcrossUsersAndFeatures() {
    assertThat(consume(1L, AiFeature.PLANT_CHAT, NOW, 2)).isEmpty();
    assertThat(consume(2L, AiFeature.JOURNAL_GUIDE, NOW, 2)).isEmpty();

    // 사용자와 기능을 바꿔도 전역 예산은 하나다. 이 요청의 사용자별 카운터 증가는 트랜잭션
    // 롤백으로 남지 않아, 전역 예산이 다시 열렸을 때 정상적으로 첫 호출을 할 수 있다.
    assertThat(consume(3L, AiFeature.JOURNAL_IMAGE_ANALYSIS, NOW, 2)).contains(60L);
    assertThat(globalRepository.findById(AiGlobalRateLimitWindow.GLOBAL_WINDOW_ID))
        .get()
        .satisfies(window -> assertThat(window.getConsumed()).isEqualTo(2));
    assertThat(repository.findByUserIdAndFeature(3L, AiFeature.JOURNAL_IMAGE_ANALYSIS)).isEmpty();
  }

  @Test
  void enforcesGlobalLimitAtomicallyForConcurrentUsers() throws Exception {
    int threads = 20;
    int globalLimit = 5;
    AiRequestGuard guard =
        new AiRequestGuard(
            new AiPolicyProperties(
                2000,
                new AiPolicyProperties.RateLimit(100, WINDOW),
                new AiPolicyProperties.RateLimit(globalLimit, WINDOW)),
            rateLimitStore,
            Clock.fixed(NOW.atZone(KST).toInstant(), KST));

    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    List<Callable<Boolean>> calls =
        IntStream.range(0, threads)
            .mapToObj(
                index ->
                    (Callable<Boolean>)
                        () -> {
                          ready.countDown();
                          start.await(WAIT_SECONDS, TimeUnit.SECONDS);
                          try {
                            guard.checkRateLimit((long) index + 1, AiFeature.PLANT_CARE_GUIDE);
                            return true;
                          } catch (BusinessException exception) {
                            return false;
                          }
                        })
            .toList();
    List<Future<Boolean>> futures = calls.stream().map(executor::submit).toList();

    try {
      assertThat(ready.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
      start.countDown();

      long allowed = 0;
      List<Throwable> failures = new ArrayList<>();
      for (Future<Boolean> future : futures) {
        try {
          if (future.get(WAIT_SECONDS, TimeUnit.SECONDS)) {
            allowed++;
          }
        } catch (ExecutionException exception) {
          failures.add(exception.getCause());
        }
      }

      assertThat(failures).isEmpty();
      assertThat(allowed).isEqualTo(globalLimit);
      assertThat(globalRepository.findById(AiGlobalRateLimitWindow.GLOBAL_WINDOW_ID))
          .get()
          .satisfies(window -> assertThat(window.getConsumed()).isEqualTo(globalLimit));
    } finally {
      executor.shutdown();
      assertThat(executor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void opensNewWindowAfterPreviousOneExpires() {
    consume(1L, AiFeature.PLANT_CHAT, NOW);
    consume(1L, AiFeature.PLANT_CHAT, NOW);
    assertThat(consume(1L, AiFeature.PLANT_CHAT, NOW)).isPresent();

    LocalDateTime afterWindow = NOW.plus(WINDOW);

    assertThat(consume(1L, AiFeature.PLANT_CHAT, afterWindow)).isEmpty();
    assertThat(repository.findByUserIdAndFeature(1L, AiFeature.PLANT_CHAT))
        .get()
        .satisfies(
            window -> {
              assertThat(window.getConsumed()).isEqualTo(1);
              assertThat(window.getWindowStartedAt()).isEqualTo(afterWindow);
            });
  }

  // 이 카운터를 DB로 옮긴 이유. 소비 상태가 전부 행에 있어야 컨테이너가 교체돼도(Blue/Green 배포)
  // 카운터가 0으로 리셋되지 않는다. 프로세스 메모리에만 남는 상태가 있으면 이 검증이 깨진다.
  @Test
  void keepsAllConsumptionStateInTheRow() {
    consume(1L, AiFeature.PLANT_CHAT, NOW);
    consume(1L, AiFeature.PLANT_CHAT, NOW);

    assertThat(repository.findByUserIdAndFeature(1L, AiFeature.PLANT_CHAT))
        .get()
        .satisfies(
            window -> {
              assertThat(window.getConsumed()).isEqualTo(2);
              assertThat(window.getWindowStartedAt()).isEqualTo(NOW);
            });
  }

  // 창이 이미 열린 뒤(정상 경로)에는 조건부 UPDATE 하나만 남으므로, 동시 요청이 정확히 한도까지만
  // 통과해야 한다. 창 생성 경합과 섞이지 않게 미리 한 번 소비해 창을 만들어 둔다.
  @Test
  void enforcesLimitAtomicallyForConcurrentRequests() throws Exception {
    int threads = 20;
    int limit = 5;
    rateLimitStore.consume(1L, AiFeature.PLANT_CHAT, NOW, WINDOW, limit, WINDOW, 100);

    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(threads);

    List<Callable<Boolean>> calls =
        IntStream.range(0, threads)
            .mapToObj(
                index ->
                    (Callable<Boolean>)
                        () -> {
                          ready.countDown();
                          start.await(WAIT_SECONDS, TimeUnit.SECONDS);
                          try {
                            rateLimitStore.consume(
                                1L, AiFeature.PLANT_CHAT, NOW, WINDOW, limit, WINDOW, 100);
                            return true;
                          } catch (AiQuotaExceededException exception) {
                            return false;
                          }
                        })
            .toList();

    List<Future<Boolean>> futures = calls.stream().map(executor::submit).toList();
    try {
      assertThat(ready.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
      start.countDown();

      // 예외가 나도 모든 future를 끝까지 회수한다. 중간에 빠져나가면 남은 스레드의 트랜잭션이
      // 다음 테스트의 정리 쿼리와 충돌해, 원인이 이 테스트가 아닌 곳에서 터진다.
      long allowed = 0;
      List<Throwable> failures = new ArrayList<>();
      for (Future<Boolean> future : futures) {
        try {
          if (future.get(WAIT_SECONDS, TimeUnit.SECONDS)) {
            allowed++;
          }
        } catch (ExecutionException exception) {
          failures.add(exception.getCause());
        }
      }

      assertThat(failures).isEmpty();
      // 미리 1건을 소비했으므로 동시 요청은 남은 4건만 통과한다.
      assertThat(allowed).isEqualTo(limit - 1);
      assertThat(repository.findByUserIdAndFeature(1L, AiFeature.PLANT_CHAT))
          .get()
          .satisfies(window -> assertThat(window.getConsumed()).isEqualTo(limit));
    } finally {
      executor.shutdown();
      assertThat(executor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }
  }

  // 창이 없는 상태에서 동시 요청이 몰리면 창을 만드는 INSERT와 소비 UPDATE가 인덱스를 반대 순서로
  // 잠가 드물게 데드락이 난다. 가드의 재시도가 이를 흡수해 한도가 정확히 지켜지는지 본다.
  @Test
  void absorbsContentionWhileWindowIsCreatedConcurrently() throws Exception {
    int threads = 20;
    int limit = 5;
    AiRequestGuard guard =
        new AiRequestGuard(
            new AiPolicyProperties(
                2000,
                new AiPolicyProperties.RateLimit(limit, WINDOW),
                new AiPolicyProperties.RateLimit(100, WINDOW)),
            rateLimitStore,
            Clock.fixed(NOW.atZone(KST).toInstant(), KST));

    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(threads);

    List<Callable<Boolean>> calls =
        IntStream.range(0, threads)
            .mapToObj(
                index ->
                    (Callable<Boolean>)
                        () -> {
                          ready.countDown();
                          start.await(WAIT_SECONDS, TimeUnit.SECONDS);
                          try {
                            guard.checkRateLimit(1L, AiFeature.PLANT_CHAT);
                            return true;
                          } catch (BusinessException exception) {
                            return false;
                          }
                        })
            .toList();

    List<Future<Boolean>> futures = calls.stream().map(executor::submit).toList();
    try {
      assertThat(ready.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
      start.countDown();

      long allowed = 0;
      List<Throwable> failures = new ArrayList<>();
      for (Future<Boolean> future : futures) {
        try {
          if (future.get(WAIT_SECONDS, TimeUnit.SECONDS)) {
            allowed++;
          }
        } catch (ExecutionException exception) {
          failures.add(exception.getCause());
        }
      }

      assertThat(failures).isEmpty();
      assertThat(allowed).isEqualTo(limit);
    } finally {
      executor.shutdown();
      assertThat(executor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }
  }

  private Optional<Long> consume(Long userId, AiFeature feature, LocalDateTime now) {
    return consume(userId, feature, now, 100);
  }

  private Optional<Long> consume(
      Long userId, AiFeature feature, LocalDateTime now, int globalMaxRequests) {
    try {
      rateLimitStore.consume(userId, feature, now, WINDOW, 2, WINDOW, globalMaxRequests);
      return Optional.empty();
    } catch (AiQuotaExceededException exception) {
      return Optional.of(exception.retryAfterSeconds());
    }
  }
}
