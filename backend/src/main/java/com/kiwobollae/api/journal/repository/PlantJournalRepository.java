package com.kiwobollae.api.journal.repository;

import com.kiwobollae.api.journal.entity.PlantJournal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlantJournalRepository extends JpaRepository<PlantJournal, Long> {

	@Modifying
	@Query("delete from PlantJournal j where j.plantProfile.id = :profileId")
	int deleteAllByProfileId(@Param("profileId") Long profileId);

	// 목록: 전체/프로필별/날짜별(null이면 미적용), soft delete 제외. 정렬·페이지는 Pageable로 지정한다.
	// join fetch(ToOne)라 count 쿼리는 별도로 두어야 페이지네이션이 올바르게 동작한다.
	@Query(value = "select j from PlantJournal j join fetch j.plantProfile "
			+ "where j.user.id = :userId and j.deletedAt is null "
			+ "and (:profileId is null or j.plantProfile.id = :profileId) "
			+ "and (:startDate is null or j.writtenDate >= :startDate) "
			+ "and (:endDate is null or j.writtenDate <= :endDate)",
			countQuery = "select count(j) from PlantJournal j "
			+ "where j.user.id = :userId and j.deletedAt is null "
			+ "and (:profileId is null or j.plantProfile.id = :profileId) "
			+ "and (:startDate is null or j.writtenDate >= :startDate) "
			+ "and (:endDate is null or j.writtenDate <= :endDate)")
	Page<PlantJournal> search(@Param("userId") Long userId, @Param("profileId") Long profileId,
			@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, Pageable pageable);

	@Query("select j from PlantJournal j join fetch j.plantProfile "
			+ "where j.id = :id and j.user.id = :userId and j.deletedAt is null")
	Optional<PlantJournal> findOwnedActive(@Param("id") Long id, @Param("userId") Long userId);

	boolean existsByIdAndDeletedAtIsNull(Long id);

	boolean existsByPlantProfileIdAndWrittenDateAndDeletedAtIsNull(Long plantProfileId, LocalDate writtenDate);

	// "오늘 일지 안 쓴 것만 보기" 필터용 — 월별 페이지 조회로 이걸 흉내 내면(size 상한 있는 페이지를
	// 넘겨받아 클라이언트에서 오늘 날짜만 걸러내는 방식) 그 달 일지가 페이지 크기를 넘는 사용자에게서
	// 최신 작성분이 페이지 밖으로 밀려날 수 있어 부정확해진다. 날짜 하나로 바로 걸러서 이 문제 자체를
	// 없앤다.
	@Query("select distinct j.plantProfile.id from PlantJournal j "
			+ "where j.user.id = :userId and j.writtenDate = :writtenDate and j.deletedAt is null")
	List<Long> findDistinctProfileIdsByUserIdAndWrittenDate(@Param("userId") Long userId, @Param("writtenDate") LocalDate writtenDate);
}
