package com.kiwobollae.api.ai.guide;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class PlantCareGuideGenerationLockStoreTest {

  private static final PlantCareGuideGenerationKey KEY =
      new PlantCareGuideGenerationKey("청상추", 1, "a".repeat(64));
  private static final PlantCareGuideGenerationKey OTHER_KEY =
      new PlantCareGuideGenerationKey("방울토마토", 1, "b".repeat(64));

  private final PlantCareGuideGenerationLockStore store = new PlantCareGuideGenerationLockStore();

  @Test
  void grantsLeaseWhenNoOneIsGeneratingTheSameKey() {
    assertThat(store.tryAcquire(KEY)).isPresent();
  }

  // 이 기능의 존재 이유. 두 번째 요청이 선점하지 못해야 AI 호출이 한 번만 나간다.
  @Test
  void refusesSecondLeaseWhileTheFirstIsStillHeld() {
    store.tryAcquire(KEY);

    assertThat(store.tryAcquire(KEY)).isEmpty();
  }

  // 락은 캐시 키 단위다. 다른 종의 생성까지 막으면 한 번에 한 종씩만 생성된다.
  @Test
  void doesNotBlockAnotherKey() {
    store.tryAcquire(KEY);

    assertThat(store.tryAcquire(OTHER_KEY)).isPresent();
  }

  @Test
  void allowsReacquireAfterRelease() {
    PlantCareGuideGenerationLockStore.Lease lease = store.tryAcquire(KEY).orElseThrow();

    store.release(lease);

    assertThat(store.tryAcquire(KEY)).isPresent();
  }

  // 만료라는 안전망이 없으므로 남의 선점을 지우면 그 키가 영영 두 번 생성될 수 있다.
  @Test
  void releaseDoesNothingWhenTheLeaseIsNoLongerOwned() {
    PlantCareGuideGenerationLockStore.Lease stale = store.tryAcquire(KEY).orElseThrow();
    store.release(stale);
    store.tryAcquire(KEY).orElseThrow(); // 새 소유자가 잡았다

    store.release(stale); // 옛 소유자가 뒤늦게 반납해도

    assertThat(store.tryAcquire(KEY)).isEmpty(); // 새 소유자의 선점은 그대로 살아 있다
  }

  // 같은 순간 몰려도 정확히 하나만 통과해야 한다. putIfAbsent의 원자성이 이 보장의 전부다.
  @Test
  void grantsExactlyOneLeaseUnderConcurrentAttempts() throws Exception {
    int threads = 16;
    CyclicBarrier startTogether = new CyclicBarrier(threads);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      List<Callable<Boolean>> attempts =
          java.util.Collections.nCopies(
              threads,
              () -> {
                startTogether.await(5, TimeUnit.SECONDS);
                return store.tryAcquire(KEY).isPresent();
              });

      List<Future<Boolean>> results = pool.invokeAll(attempts);

      long granted = 0;
      for (Future<Boolean> result : results) {
        if (result.get()) {
          granted++;
        }
      }
      assertThat(granted).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  // 반납하면 엔트리가 남지 않아야 한다. 종 수만큼 쌓이면 그게 곧 누수다.
  @Test
  void doesNotRetainEntriesAfterRelease() {
    for (int i = 0; i < 100; i++) {
      PlantCareGuideGenerationKey key =
          new PlantCareGuideGenerationKey("종" + i, 1, String.valueOf(i).repeat(2));
      store.release(store.tryAcquire(key).orElseThrow());
    }

    Optional<PlantCareGuideGenerationLockStore.Lease> lease = store.tryAcquire(KEY);

    assertThat(lease).isPresent();
    assertThat(inFlightSize()).isEqualTo(1);
  }

  private int inFlightSize() {
    try {
      var field = PlantCareGuideGenerationLockStore.class.getDeclaredField("inFlight");
      field.setAccessible(true);
      return ((java.util.Map<?, ?>) field.get(store)).size();
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
