package com.kiwobollae.api.ai.guide;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlantCareGuideGenerationLockRepository
    extends JpaRepository<PlantCareGuideGenerationLock, Long> {

  /**
   * 키가 비었거나 기존 lease가 만료됐을 때만 새 lease를 선점한다.
   *
   * <p>한 문장의 upsert로 처리해 두 요청이 같은 캐시 미스를 읽어도 한 요청만 새 owner token을 기록한다. 영향 행 수는 JDBC 설정에 따라 달라질 수
   * 있으므로, 호출자는 기록된 owner token을 다시 확인해 실제 선점 성공 여부를 판단한다.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      nativeQuery = true,
      value =
          """
          INSERT INTO ai_plant_care_guide_generation_locks
              (species_name, guide_version, source_context_hash, locked_until, owner_token)
          VALUES (:speciesName, :guideVersion, :sourceContextHash, :lockedUntil, :ownerToken)
          ON DUPLICATE KEY UPDATE
              owner_token = CASE
                  WHEN locked_until <= :now THEN :ownerToken
                  ELSE owner_token
              END,
              locked_until = CASE
                  WHEN locked_until <= :now THEN :lockedUntil
                  ELSE locked_until
              END
          """)
  int acquireIfAvailable(
      @Param("speciesName") String speciesName,
      @Param("guideVersion") int guideVersion,
      @Param("sourceContextHash") String sourceContextHash,
      @Param("now") LocalDateTime now,
      @Param("lockedUntil") LocalDateTime lockedUntil,
      @Param("ownerToken") String ownerToken);

  @Query(
      """
      SELECT l.ownerToken
      FROM PlantCareGuideGenerationLock l
      WHERE l.speciesName = :speciesName
        AND l.guideVersion = :guideVersion
        AND l.sourceContextHash = :sourceContextHash
      """)
  Optional<String> findOwnerToken(
      @Param("speciesName") String speciesName,
      @Param("guideVersion") int guideVersion,
      @Param("sourceContextHash") String sourceContextHash);

  /** 자신이 선점한 lease만 지운다. 만료 뒤 새 소유자가 잡은 lease를 이전 요청이 지우지 않게 한다. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      DELETE FROM PlantCareGuideGenerationLock l
      WHERE l.speciesName = :speciesName
        AND l.guideVersion = :guideVersion
        AND l.sourceContextHash = :sourceContextHash
        AND l.lockedUntil = :lockedUntil
        AND l.ownerToken = :ownerToken
      """)
  int deleteOwnedLease(
      @Param("speciesName") String speciesName,
      @Param("guideVersion") int guideVersion,
      @Param("sourceContextHash") String sourceContextHash,
      @Param("lockedUntil") LocalDateTime lockedUntil,
      @Param("ownerToken") String ownerToken);
}
