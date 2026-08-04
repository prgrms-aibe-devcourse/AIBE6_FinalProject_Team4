package com.kiwobollae.api.content.repository;

import com.kiwobollae.api.content.entity.PlantTimelapse;
import java.util.Optional;
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

	@Modifying
	@Query("delete from PlantTimelapse t where t.plantProfile.id = :profileId")
	int deleteByPlantProfileId(@Param("profileId") Long profileId);
}
