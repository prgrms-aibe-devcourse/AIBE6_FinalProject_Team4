package com.kiwobollae.api.plantProfile.repository;

import com.kiwobollae.api.plantProfile.entity.PlantProfile;
import com.kiwobollae.api.plantProfile.entity.enums.PlantStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlantProfileRepository extends JpaRepository<PlantProfile, Long> {

	@Query("select p from PlantProfile p join fetch p.species "
			+ "where p.user.id = :userId order by p.createdAt desc")
	List<PlantProfile> findAllByUserId(@Param("userId") Long userId);

	// 상태 우선순위(재배중 → 재배완료 → 실패)로 먼저 정렬하고, Pageable의 정렬(기본 createdAt desc)이 뒤이어
	// 그룹 내 정렬 기준으로 적용된다(Spring Data JPA가 기존 order by 뒤에 이어붙인다).
	@Query(value = "select p from PlantProfile p join fetch p.species "
			+ "where p.user.id = :userId and (:status is null or p.status = :status) "
			+ "order by case when p.status = com.kiwobollae.api.plantProfile.entity.enums.PlantStatus.GROWING then 0 "
			+ "when p.status = com.kiwobollae.api.plantProfile.entity.enums.PlantStatus.HARVESTED then 1 else 2 end",
			countQuery = "select count(p) from PlantProfile p "
			+ "where p.user.id = :userId and (:status is null or p.status = :status)")
	Page<PlantProfile> findAllByUserIdAndStatus(@Param("userId") Long userId,
			@Param("status") PlantStatus status, Pageable pageable);

	@Query("select p from PlantProfile p join fetch p.species "
			+ "where p.id = :id and p.user.id = :userId")
	Optional<PlantProfile> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

	boolean existsByIdAndUserId(Long id, Long userId);

	// 이미지 URL이 어떤 프로필의 대표 사진으로 아직 쓰이고 있는지 확인한다 — S3 정리 전 안전 장치.
	boolean existsByPlantImage(String plantImage);
}
