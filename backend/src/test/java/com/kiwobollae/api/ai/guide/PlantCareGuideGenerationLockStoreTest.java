package com.kiwobollae.api.ai.guide;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlantCareGuideGenerationLockStoreTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 6, 10, 0);
  private static final Duration LEASE_DURATION = Duration.ofMinutes(1);
  private static final PlantCareGuideGenerationKey KEY =
      new PlantCareGuideGenerationKey("청상추", 1, "a".repeat(64));

  @Mock private PlantCareGuideGenerationLockRepository repository;

  @Test
  void grantsLeaseOnlyWhenTheDatabaseRecordsThisOwnerToken() {
    AtomicReference<String> ownerToken = new AtomicReference<>();
    given(
            repository.acquireIfAvailable(
                anyString(), anyInt(), anyString(), any(), any(), anyString()))
        .willAnswer(
            invocation -> {
              ownerToken.set(invocation.getArgument(5));
              return 1;
            });
    given(repository.findOwnerToken(KEY.speciesName(), KEY.guideVersion(), KEY.sourceContextHash()))
        .willAnswer(ignored -> Optional.of(ownerToken.get()));

    Optional<PlantCareGuideGenerationLockStore.Lease> lease =
        store().tryAcquire(KEY, NOW, LEASE_DURATION);

    assertThat(lease)
        .hasValueSatisfying(
            value -> {
              assertThat(value.key()).isEqualTo(KEY);
              assertThat(value.lockedUntil()).isEqualTo(NOW.plus(LEASE_DURATION));
              assertThat(value.ownerToken()).isEqualTo(ownerToken.get());
            });
  }

  @Test
  void refusesLeaseWhenAnotherOwnerIsRecorded() {
    given(
            repository.acquireIfAvailable(
                anyString(), anyInt(), anyString(), any(), any(), anyString()))
        .willReturn(0);
    given(repository.findOwnerToken(KEY.speciesName(), KEY.guideVersion(), KEY.sourceContextHash()))
        .willReturn(Optional.of("another-owner"));

    assertThat(store().tryAcquire(KEY, NOW, LEASE_DURATION)).isEmpty();
  }

  @Test
  void releasesOnlyTheLeaseOwnedByThisRequest() {
    PlantCareGuideGenerationLockStore.Lease lease =
        new PlantCareGuideGenerationLockStore.Lease(KEY, NOW.plus(LEASE_DURATION), "request-owner");

    store().release(lease);

    verify(repository)
        .deleteOwnedLease(
            KEY.speciesName(),
            KEY.guideVersion(),
            KEY.sourceContextHash(),
            NOW.plus(LEASE_DURATION),
            "request-owner");
  }

  private PlantCareGuideGenerationLockStore store() {
    return new PlantCareGuideGenerationLockStore(repository);
  }
}
