package com.kiwobollae.api.content.repository;

import com.kiwobollae.api.content.entity.JournalImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JournalImageRepository extends JpaRepository<JournalImage, Long> {

	@Modifying
	@Query("delete from JournalImage i where i.journal.id in "
			+ "(select j.id from PlantJournal j where j.plantProfile.id = :profileId)")
	int deleteAllByProfileId(@Param("profileId") Long profileId);
}
