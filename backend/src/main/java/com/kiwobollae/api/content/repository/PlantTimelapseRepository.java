package com.kiwobollae.api.content.repository;

import com.kiwobollae.api.content.entity.PlantTimelapse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlantTimelapseRepository extends JpaRepository<PlantTimelapse, Long> {

	Optional<PlantTimelapse> findByPlantProfileId(Long plantProfileId);

	// PENDING -> PROCESSING 조건부 전이. 0을 반환하면 이미 처리 중이거나 대상이 아니라는 뜻이라 워커가 조용히 종료한다.
	@Modifying
	@Query("update PlantTimelapse t set t.status = com.kiwobollae.api.content.entity.enums.PlantTimelapseStatus.PROCESSING "
			+ "where t.plantProfile.id = :profileId "
			+ "and t.status = com.kiwobollae.api.content.entity.enums.PlantTimelapseStatus.PENDING")
	int claimForProcessing(@Param("profileId") Long profileId);

	// PENDING/PROCESSING -> FAILED 조건부 전이(복구 스케줄러 전용). 0을 반환하면 그 사이 정상
	// 워커가 이미 완료/실패 처리했다는 뜻이라 알림을 다시 보내지 않는다.
	// PENDING도 대상에 포함하는 이유: 비동기 실행 큐(timelapseTaskExecutor)가 가득 차서
	// RejectedExecutionException으로 작업 제출 자체가 거부되면 claim()이 호출조차 안 되고
	// PROCESSING으로 전이되지 않아 PENDING에 그대로 남는다 — 이 경우도 방치하면 사용자가
	// 눈치채지 못하는 한 무기한 "생성 중"으로 보인다.
	@Modifying
	@Query("update PlantTimelapse t set t.status = com.kiwobollae.api.content.entity.enums.PlantTimelapseStatus.FAILED, "
			+ "t.failReason = :reason, t.completedAt = :completedAt "
			+ "where t.plantProfile.id = :profileId "
			+ "and t.status in (com.kiwobollae.api.content.entity.enums.PlantTimelapseStatus.PENDING, "
			+ "com.kiwobollae.api.content.entity.enums.PlantTimelapseStatus.PROCESSING)")
	int failIfStillPendingOrProcessing(@Param("profileId") Long profileId, @Param("reason") String reason,
			@Param("completedAt") LocalDateTime completedAt);

	// 지정 시각 이전에 요청됐는데 아직도 PENDING이거나 PROCESSING인 행 — 워커가 죽었거나(fail()
	// 자체 실패, 서버 재시작), 큐가 가득 차 작업 제출 자체가 거부됐거나, 처리 중 멈춘 것으로
	// 간주해 복구 스케줄러가 정리 대상으로 삼는다.
	@Query("select t.plantProfile.id from PlantTimelapse t "
			+ "where t.status in (com.kiwobollae.api.content.entity.enums.PlantTimelapseStatus.PENDING, "
			+ "com.kiwobollae.api.content.entity.enums.PlantTimelapseStatus.PROCESSING) "
			+ "and t.requestedAt < :staleBefore "
			+ "order by t.requestedAt asc")
	List<Long> findStalePendingOrProcessingProfileIds(@Param("staleBefore") LocalDateTime staleBefore, Pageable pageable);

	@Modifying
	@Query("delete from PlantTimelapse t where t.plantProfile.id = :profileId")
	int deleteByPlantProfileId(@Param("profileId") Long profileId);
}
