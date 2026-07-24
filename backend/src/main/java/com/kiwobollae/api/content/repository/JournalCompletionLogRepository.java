package com.kiwobollae.api.content.repository;

import com.kiwobollae.api.content.entity.JournalCompletionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JournalCompletionLogRepository extends JpaRepository<JournalCompletionLog, Long> {

	@Modifying
	@Query("update JournalCompletionLog c "
			+ "set c.plantNicknameSnapshot = :nickname, c.plantProfile = null, c.plantJournal = null "
			+ "where c.plantProfile.id = :profileId")
	int detachByProfileId(@Param("profileId") Long profileId, @Param("nickname") String nickname);
}
