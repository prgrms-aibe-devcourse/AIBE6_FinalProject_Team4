package com.kiwobollae.api.content.dto.response;

import com.kiwobollae.api.content.entity.PlantProfile;
import com.kiwobollae.api.content.entity.enums.PlantStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PlantProfileResponse(
		Long id,
		Long userId,
		Long speciesId,
		String speciesName,
		String nickname,
		LocalDate startDate,
		String thumbnailUrl,
		PlantStatus status,
		LocalDateTime createdAt,
		LocalDateTime archivedAt
) {
	public static PlantProfileResponse from(PlantProfile plantProfile) {
		return new PlantProfileResponse(
				plantProfile.getId(),
				plantProfile.getUser().getId(),
				plantProfile.getSpecies().getId(),
				plantProfile.getSpecies().getName(),
				plantProfile.getPlantName(),
				plantProfile.getStartDate(),
				plantProfile.getPlantImage(),
				plantProfile.getStatus(),
				plantProfile.getCreatedAt(),
				plantProfile.getArchivedAt()
		);
	}
}
