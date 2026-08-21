package com.kiwobollae.api.ai.analysis;

import com.kiwobollae.api.ai.knowledge.PlantCareEvidenceStatus;
import com.kiwobollae.api.ai.knowledge.PlantCareEvidenceScope;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JournalImageAnalysisStore {

  private static final Duration STALE_PENDING_AFTER = Duration.ofMinutes(2);

  private final JournalImageAnalysisRepository repository;
  private final Clock seoulClock;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Claim claim(Long journalId, String imageHash) {
    LocalDateTime now = LocalDateTime.now(seoulClock);
    String claimToken = UUID.randomUUID().toString();
    repository.insertPendingIfAbsent(journalId, imageHash, claimToken, now);
    JournalImageAnalysis current = findRequired(journalId, imageHash);

    if (claimToken.equals(current.getClaimToken())) {
      return Claim.owner(claimToken);
    }
    if (current.getStatus() == JournalImageAnalysisStatus.COMPLETED) {
      return Claim.completed(current);
    }

    int reclaimed =
        repository.reclaim(journalId, imageHash, claimToken, now, now.minus(STALE_PENDING_AFTER));
    if (reclaimed == 1) {
      return Claim.owner(claimToken);
    }

    current = findRequired(journalId, imageHash);
    return current.getStatus() == JournalImageAnalysisStatus.COMPLETED
        ? Claim.completed(current)
        : Claim.inProgress();
  }

  @Transactional(readOnly = true)
  public List<JournalImageAnalysis> findCompleted(Long journalId) {
    return repository.findAllByJournalIdAndStatusOrderByUpdatedAtDesc(
        journalId, JournalImageAnalysisStatus.COMPLETED);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public JournalImageAnalysis complete(
      Long journalId,
      String imageHash,
      String claimToken,
      String resultJson,
      String model,
      PlantCareEvidenceStatus evidenceStatus,
      PlantCareEvidenceScope evidenceScope,
      String evidenceSpeciesName,
      String sourceContextHash,
      String evidenceSourcesJson,
      LocalDateTime completedAt) {
    int updated =
        repository.complete(
            journalId,
            imageHash,
            claimToken,
            resultJson,
            model,
            evidenceStatus.name(),
            evidenceScope.name(),
            evidenceSpeciesName,
            sourceContextHash,
            evidenceSourcesJson,
            completedAt);
    if (updated != 1) {
      return null;
    }
    return findRequired(journalId, imageHash);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void fail(Long journalId, String imageHash, String claimToken) {
    repository.fail(journalId, imageHash, claimToken, LocalDateTime.now(seoulClock));
  }

  private JournalImageAnalysis findRequired(Long journalId, String imageHash) {
    return repository
        .findByJournalIdAndImageHash(journalId, imageHash)
        .orElseThrow(() -> new IllegalStateException("선점한 사진 분석 기록을 찾을 수 없습니다."));
  }

  public record Claim(ClaimStatus status, String claimToken, JournalImageAnalysis completed) {
    static Claim owner(String token) {
      return new Claim(ClaimStatus.OWNER, token, null);
    }

    static Claim completed(JournalImageAnalysis analysis) {
      return new Claim(ClaimStatus.COMPLETED, null, analysis);
    }

    static Claim inProgress() {
      return new Claim(ClaimStatus.IN_PROGRESS, null, null);
    }
  }

  public enum ClaimStatus {
    OWNER,
    COMPLETED,
    IN_PROGRESS
  }
}
