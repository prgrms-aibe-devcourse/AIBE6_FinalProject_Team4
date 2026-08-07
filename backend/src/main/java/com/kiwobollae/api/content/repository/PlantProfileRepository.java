package com.kiwobollae.api.content.repository;

import com.kiwobollae.api.content.entity.PlantProfile;
import com.kiwobollae.api.content.entity.enums.PlantStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlantProfileRepository extends JpaRepository<PlantProfile, Long> {

	@Query("select p from PlantProfile p join fetch p.species "
			+ "where p.user.id = :userId order by p.createdAt desc")
	List<PlantProfile> findAllByUserId(@Param("userId") Long userId);

	@Query(value = "select p from PlantProfile p join fetch p.species "
			+ "where p.user.id = :userId and (:status is null or p.status = :status)",
			countQuery = "select count(p) from PlantProfile p "
			+ "where p.user.id = :userId and (:status is null or p.status = :status)")
	Page<PlantProfile> findAllByUserIdAndStatus(@Param("userId") Long userId,
			@Param("status") PlantStatus status, Pageable pageable);

	@Query("select p from PlantProfile p join fetch p.species "
			+ "where p.id = :id and p.user.id = :userId")
	Optional<PlantProfile> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

	// 오늘 아직 지급 안 된 경우(null이거나 오늘 이전)에만 원자적으로 클레임한다(매일 리셋).
	// 동시 요청 중 하나만 성공(1건 갱신)한다.
	@Modifying
	@Query("update PlantProfile p set p.journalRewardGrantedAt = :now "
			+ "where p.id = :id and (p.journalRewardGrantedAt is null or p.journalRewardGrantedAt < :startOfToday)")
	int claimJournalReward(@Param("id") Long id, @Param("now") LocalDateTime now,
			@Param("startOfToday") LocalDateTime startOfToday);

	// 이미지 URL이 어떤 프로필의 대표 사진으로 아직 쓰이고 있는지 확인한다 — S3 정리 전 안전 장치.
	boolean existsByPlantImage(String plantImage);
}
