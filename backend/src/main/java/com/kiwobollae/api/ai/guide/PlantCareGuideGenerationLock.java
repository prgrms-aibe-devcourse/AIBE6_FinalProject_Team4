package com.kiwobollae.api.ai.guide;

import com.kiwobollae.api.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 실제 AI 호출 전에 잡는 짧은 생성 lease.
 *
 * <p>캐시 본문은 생성이 끝난 뒤에야 유효하므로 {@link PlantCareGuideCache}에 PROCESSING 행을 억지로 넣지 않는다. 별도 lease 행을 먼저
 * 선점해 캐시 미스 경쟁에서 단 한 요청만 외부 호출을 하게 한다.
 */
@Entity
@Table(
    name = "ai_plant_care_guide_generation_locks",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_ai_plant_care_guide_generation_lock",
          columnNames = {"species_name", "guide_version", "source_context_hash"})
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlantCareGuideGenerationLock extends BaseEntity {

  @Column(name = "species_name", nullable = false, length = 100)
  private String speciesName;

  @Column(name = "guide_version", nullable = false)
  private int guideVersion;

  @Column(name = "source_context_hash", nullable = false, length = 64)
  private String sourceContextHash;

  @Column(name = "locked_until", nullable = false)
  private LocalDateTime lockedUntil;

  /** lease 소유자를 식별한다. 영향 행 수 설정에 의존하지 않고 실제 선점 성공 여부를 확인하는 데 쓴다. */
  @Column(name = "owner_token", nullable = false, length = 36)
  private String ownerToken;
}
