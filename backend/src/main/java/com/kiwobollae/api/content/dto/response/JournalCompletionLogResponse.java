package com.kiwobollae.api.content.dto.response;

import com.kiwobollae.api.content.entity.JournalCompletionLog;
import java.time.LocalDate;

public record JournalCompletionLogResponse(
		Long id,
		Long userId,
		Long plantProfileId,
		Long plantJournalId,
		LocalDate completionDate
) {
	public static JournalCompletionLogResponse from(JournalCompletionLog journalCompletionLog) {
		return new JournalCompletionLogResponse(
				journalCompletionLog.getId(),
				journalCompletionLog.getUser().getId(),
				journalCompletionLog.getPlantProfile().getId(),
				journalCompletionLog.getPlantJournal().getId(),
				journalCompletionLog.getCompletionDate()
		);
	}
}
