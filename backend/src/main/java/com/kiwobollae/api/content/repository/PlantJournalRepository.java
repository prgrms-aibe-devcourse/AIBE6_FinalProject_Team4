package com.kiwobollae.api.content.repository;

import com.kiwobollae.api.content.entity.PlantJournal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlantJournalRepository extends JpaRepository<PlantJournal, Long> {

	@Modifying
	@Query("delete from PlantJournal j where j.plantProfile.id = :profileId")
	int deleteAllByProfileId(@Param("profileId") Long profileId);

	@Query("select j from PlantJournal j join fetch j.plantProfile "
			+ "where j.user.id = :userId and j.deletedAt is null "
			+ "and (:profileId is null or j.plantProfile.id = :profileId) "
			+ "and (:startDate is null or j.writtenDate >= :startDate) "
			+ "and (:endDate is null or j.writtenDate <= :endDate) "
			+ "order by j.writtenDate desc, j.id desc")
	List<PlantJournal> search(@Param("userId") Long userId, @Param("profileId") Long profileId,
			@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

	@Query("select j from PlantJournal j join fetch j.plantProfile "
			+ "where j.id = :id and j.user.id = :userId and j.deletedAt is null")
	Optional<PlantJournal> findOwnedActive(@Param("id") Long id, @Param("userId") Long userId);
}
