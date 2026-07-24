package com.kiwobollae.api.content.dto.response;

import com.kiwobollae.api.content.entity.PlantJournal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PlantJournalResponse(
		Long id,
		Long plantProfileId,
		String plantProfileNickname,
		Long userId,
		String content,
		String imageUrl,
		String imageHash,
		LocalDate writtenDate,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		LocalDateTime deletedAt
) {
	public static PlantJournalResponse from(PlantJournal plantJournal) {
		return new PlantJournalResponse(
				plantJournal.getId(),
				plantJournal.getPlantProfile().getId(),
				plantJournal.getPlantProfile().getPlantName(),
				plantJournal.getUser().getId(),
				plantJournal.getContent(),
				plantJournal.getImageUrl(),
				plantJournal.getImageHash(),
				plantJournal.getWrittenDate(),
				plantJournal.getCreatedAt(),
				plantJournal.getUpdatedAt(),
				plantJournal.getDeletedAt()
		);
	}
}
