package com.kiwobollae.api.content.repository;

import com.kiwobollae.api.content.entity.JournalImage;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JournalImageRepository extends JpaRepository<JournalImage, Long> {

	@Modifying
	@Query("delete from JournalImage i where i.journal.id in "
			+ "(select j.id from PlantJournal j where j.plantProfile.id = :profileId)")
	int deleteAllByProfileId(@Param("profileId") Long profileId);

	// 프로필 삭제 시 S3 정리 대상 URL을 먼저 확보하기 위한 조회 — deleteAllByProfileId와 같은 조인 경로.
	@Query("select i from JournalImage i where i.journal.plantProfile.id = :profileId")
	List<JournalImage> findByProfileId(@Param("profileId") Long profileId);

	// 요청에 담긴 해시들 중 이미 저장된 것만 골라 반환한다 (건당 조회 대신 한 번에 IN 조회).
	@Query("select i.imageHash from JournalImage i "
			+ "where i.user.id = :userId and i.imageHash in :imageHashes and i.writtenDate = :writtenDate")
	List<String> findExistingHashes(@Param("userId") Long userId, @Param("imageHashes") Collection<String> imageHashes,
			@Param("writtenDate") LocalDate writtenDate);

	List<JournalImage> findByJournalId(Long journalId);

	List<JournalImage> findByJournalIdIn(Collection<Long> journalIds);

	@Modifying
	@Query("delete from JournalImage i where i.journal.id = :journalId")
	int deleteByJournalId(@Param("journalId") Long journalId);

	// 이미지 URL이 어떤 일지에 아직 쓰이고 있는지 확인한다 — S3 정리 전 안전 장치.
	boolean existsByImageUrl(String imageUrl);

	// 타임랩스 소스: 프로필의 대표이미지만, 작성일 오름차순.
	@Query("select i from JournalImage i where i.journal.plantProfile.id = :profileId "
			+ "and i.representative = true order by i.writtenDate asc")
	List<JournalImage> findRepresentativeByProfileIdOrderByWrittenDateAsc(@Param("profileId") Long profileId);
}
