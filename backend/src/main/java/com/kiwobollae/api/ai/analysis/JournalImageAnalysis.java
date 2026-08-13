package com.kiwobollae.api.ai.analysis;

import com.kiwobollae.api.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "ai_journal_image_analyses",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_ai_journal_image_analysis_journal_hash",
            columnNames = {"journal_id", "image_hash"}),
    indexes =
        @Index(
            name = "idx_ai_journal_image_analysis_journal_status",
            columnList = "journal_id, status"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class JournalImageAnalysis extends BaseEntity {

  /** 일지 삭제·사진 교체 뒤에도 과거 분석 이력을 남기기 위한 논리 참조다. */
  @Column(name = "journal_id", nullable = false)
  private Long journalId;

  @Column(name = "image_hash", nullable = false, length = 64)
  private String imageHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private JournalImageAnalysisStatus status;

  @Column(name = "result_json", columnDefinition = "LONGTEXT")
  private String resultJson;

  @Column(length = 100)
  private String model;

  @Column(name = "claim_token", length = 36)
  private String claimToken;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
