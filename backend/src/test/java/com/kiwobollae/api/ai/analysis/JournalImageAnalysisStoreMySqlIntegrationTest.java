package com.kiwobollae.api.ai.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.kiwobollae.api.ai.analysis.JournalImageAnalysisStore.Claim;
import com.kiwobollae.api.ai.analysis.JournalImageAnalysisStore.ClaimStatus;
import com.kiwobollae.api.ai.knowledge.PlantCareEvidenceStatus;
import com.kiwobollae.api.ai.knowledge.PlantCareEvidenceScope;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
class JournalImageAnalysisStoreMySqlIntegrationTest {

  private static final Long JOURNAL_ID = 31L;
  private static final String IMAGE_HASH = "a".repeat(64);

  @Autowired private JournalImageAnalysisStore store;
  @Autowired private JournalImageAnalysisRepository repository;

  @BeforeEach
  void clearAnalyses() {
    repository.deleteAllInBatch();
  }

  @Test
  void onlyOneConcurrentRequestClaimsSameJournalImage() throws Exception {
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<Claim> first = executor.submit(() -> claimAfterSignal(ready, start));
      Future<Claim> second = executor.submit(() -> claimAfterSignal(ready, start));
      ready.await();
      start.countDown();

      List<ClaimStatus> statuses = List.of(first.get().status(), second.get().status());

      assertThat(statuses).containsExactlyInAnyOrder(ClaimStatus.OWNER, ClaimStatus.IN_PROGRESS);
      assertThat(repository.count()).isEqualTo(1);
    }
  }

  @Test
  void completedResultIsReplayedWithoutNewClaim() {
    Claim owner = store.claim(JOURNAL_ID, IMAGE_HASH);
    LocalDateTime completedAt = LocalDateTime.of(2026, 8, 13, 11, 0);
    JournalImageAnalysis completed =
        store.complete(
            JOURNAL_ID,
            IMAGE_HASH,
            owner.claimToken(),
            "{\"summary\":\"stored\"}",
            "vision-model",
            PlantCareEvidenceStatus.GENERAL_FALLBACK,
            PlantCareEvidenceScope.NONE,
            null,
            "a".repeat(64),
            "[]",
            completedAt);

    Claim replay = store.claim(JOURNAL_ID, IMAGE_HASH);

    assertThat(completed).isNotNull();
    assertThat(replay.status()).isEqualTo(ClaimStatus.COMPLETED);
    assertThat(replay.completed().getResultJson()).contains("stored");
    assertThat(replay.completed().getEvidenceStatus())
        .isEqualTo(PlantCareEvidenceStatus.GENERAL_FALLBACK);
    assertThat(replay.completed().getEvidenceScope()).isEqualTo(PlantCareEvidenceScope.NONE);
    assertThat(replay.completed().getSourceContextHash()).isEqualTo("a".repeat(64));
    assertThat(repository.count()).isEqualTo(1);
  }

  @Test
  void failedAttemptCanBeClaimedAgainWithDifferentToken() {
    Claim first = store.claim(JOURNAL_ID, IMAGE_HASH);
    store.fail(JOURNAL_ID, IMAGE_HASH, first.claimToken());

    Claim retry = store.claim(JOURNAL_ID, IMAGE_HASH);

    assertThat(retry.status()).isEqualTo(ClaimStatus.OWNER);
    assertThat(retry.claimToken()).isNotEqualTo(first.claimToken());
    assertThat(repository.count()).isEqualTo(1);
  }

  private Claim claimAfterSignal(CountDownLatch ready, CountDownLatch start) throws Exception {
    ready.countDown();
    start.await();
    return store.claim(JOURNAL_ID, IMAGE_HASH);
  }
}
