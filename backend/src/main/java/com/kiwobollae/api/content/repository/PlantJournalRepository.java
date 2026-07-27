package com.kiwobollae.api.content.repository;

import com.kiwobollae.api.content.entity.PlantJournal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlantJournalRepository extends JpaRepository<PlantJournal, Long> {

	@Modifying
	@Query("delete from PlantJournal j where j.plantProfile.id = :profileId")
	int deleteAllByProfileId(@Param("profileId") Long profileId);
}
