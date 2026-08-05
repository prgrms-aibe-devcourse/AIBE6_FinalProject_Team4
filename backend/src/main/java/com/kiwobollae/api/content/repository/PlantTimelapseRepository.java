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

	// 프로필 삭제 시 S3 정리용으로 videoUrl 값만 필요할 때 쓴다 — 엔티티를 통째로 로드하면 이후
	// bulk delete로 DB 행이 지워져도 영속성 컨텍스트엔 그대로 남아, 다른 쿼리가 유발하는
	// auto-flush 시점에 Hibernate가 이 엔티티의 plantProfile 연관관계를 검증하다 같은 트랜잭션에서
	// remove()된 PlantProfile을 transient로 오인해 TransientPropertyValueException을 던진다.
	@Query("select t.videoUrl from PlantTimelapse t where t.plantProfile.id = :profileId")
	Optional<String> findVideoUrlByPlantProfileId(@Param("profileId") Long profileId);

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
