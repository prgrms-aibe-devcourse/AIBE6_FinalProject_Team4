package com.kiwobollae.api.content.repository;

import com.kiwobollae.api.content.entity.PlantProfile;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlantProfileRepository extends JpaRepository<PlantProfile, Long> {

	@Query("select p from PlantProfile p join fetch p.species "
			+ "where p.user.id = :userId order by p.createdAt desc")
	List<PlantProfile> findAllByUserId(@Param("userId") Long userId);

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

	// 읽었던 지급 시각과 현재 값이 같을 때만 원자적으로 해제한다(다른 요청이 먼저 처리했으면 0건).
	@Modifying
	@Query("update PlantProfile p set p.journalRewardGrantedAt = null "
			+ "where p.id = :id and p.journalRewardGrantedAt = :expectedGrantedAt")
	int clearJournalRewardIfMatches(@Param("id") Long id, @Param("expectedGrantedAt") LocalDateTime expectedGrantedAt);
}
