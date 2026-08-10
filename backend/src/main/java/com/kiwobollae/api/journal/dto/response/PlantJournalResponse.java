package com.kiwobollae.api.journal.dto.response;

import com.kiwobollae.api.journal.entity.JournalImage;
import com.kiwobollae.api.journal.entity.PlantJournal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PlantJournalResponse(
		Long id,
		Long plantProfileId,
		String plantProfileNickname,
		Long userId,
		String content,
		LocalDate writtenDate,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		LocalDateTime deletedAt,
		List<JournalImageResponse> images,
		GachaRewardResponse gachaReward
) {
	public static PlantJournalResponse from(PlantJournal plantJournal, List<JournalImage> images) {
		return from(plantJournal, images, null);
	}

	public static PlantJournalResponse from(
			PlantJournal plantJournal, List<JournalImage> images, GachaRewardResponse gachaReward) {
		return new PlantJournalResponse(
				plantJournal.getId(),
				plantJournal.getPlantProfile().getId(),
				plantJournal.getPlantProfile().getPlantName(),
				plantJournal.getUser().getId(),
				plantJournal.getContent(),
				plantJournal.getWrittenDate(),
				plantJournal.getCreatedAt(),
				plantJournal.getUpdatedAt(),
				plantJournal.getDeletedAt(),
				images.stream().map(JournalImageResponse::from).toList(),
				gachaReward
		);
	}
}
