package com.kiwobollae.api.content.dto.response;

import com.kiwobollae.api.content.entity.JournalCompletionLog;
import java.time.LocalDate;

public record JournalCompletionLogResponse(
		Long id,
		Long userId,
		Long plantProfileId,
		Long plantJournalId,
		LocalDate completionDate,
		String plantNicknameSnapshot
) {
	public static JournalCompletionLogResponse from(JournalCompletionLog journalCompletionLog) {
		return new JournalCompletionLogResponse(
				journalCompletionLog.getId(),
				journalCompletionLog.getUser().getId(),
				journalCompletionLog.getPlantProfile() != null ? journalCompletionLog.getPlantProfile().getId() : null,
				journalCompletionLog.getPlantJournal() != null ? journalCompletionLog.getPlantJournal().getId() : null,
				journalCompletionLog.getCompletionDate(),
				journalCompletionLog.getPlantNicknameSnapshot()
		);
	}
}
