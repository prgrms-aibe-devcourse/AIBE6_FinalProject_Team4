package com.kiwobollae.api.ai.analysis;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JournalImageAnalysisRepository extends JpaRepository<JournalImageAnalysis, Long> {

  Optional<JournalImageAnalysis> findByJournalIdAndImageHash(Long journalId, String imageHash);

  List<JournalImageAnalysis> findAllByJournalIdAndStatusOrderByUpdatedAtDesc(
      Long journalId, JournalImageAnalysisStatus status);

  @Modifying(flushAutomatically = true)
  @Query(
      value =
          """
          insert into ai_journal_image_analyses
              (journal_id, image_hash, status, claim_token, created_at, updated_at)
          values (:journalId, :imageHash, 'PENDING', :claimToken, :now, :now)
          on duplicate key update id = id
          """,
      nativeQuery = true)
  int insertPendingIfAbsent(
      @Param("journalId") Long journalId,
      @Param("imageHash") String imageHash,
      @Param("claimToken") String claimToken,
      @Param("now") LocalDateTime now);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
          update ai_journal_image_analyses
             set status = 'PENDING', claim_token = :claimToken,
                 result_json = null, model = null, updated_at = :now
           where journal_id = :journalId
             and image_hash = :imageHash
             and (status = 'FAILED' or (status = 'PENDING' and updated_at < :staleBefore))
          """,
      nativeQuery = true)
  int reclaim(
      @Param("journalId") Long journalId,
      @Param("imageHash") String imageHash,
      @Param("claimToken") String claimToken,
      @Param("now") LocalDateTime now,
      @Param("staleBefore") LocalDateTime staleBefore);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
          update ai_journal_image_analyses
             set status = 'COMPLETED', result_json = :resultJson, model = :model,
                 claim_token = null, updated_at = :now
           where journal_id = :journalId
             and image_hash = :imageHash
             and status = 'PENDING'
             and claim_token = :claimToken
          """,
      nativeQuery = true)
  int complete(
      @Param("journalId") Long journalId,
      @Param("imageHash") String imageHash,
      @Param("claimToken") String claimToken,
      @Param("resultJson") String resultJson,
      @Param("model") String model,
      @Param("now") LocalDateTime now);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
          update ai_journal_image_analyses
             set status = 'FAILED', claim_token = null, updated_at = :now
           where journal_id = :journalId
             and image_hash = :imageHash
             and status = 'PENDING'
             and claim_token = :claimToken
          """,
      nativeQuery = true)
  int fail(
      @Param("journalId") Long journalId,
      @Param("imageHash") String imageHash,
      @Param("claimToken") String claimToken,
      @Param("now") LocalDateTime now);
}
