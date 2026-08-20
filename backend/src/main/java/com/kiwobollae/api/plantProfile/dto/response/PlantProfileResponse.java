package com.kiwobollae.api.plantProfile.dto.response;

import com.kiwobollae.api.plantProfile.entity.PlantProfile;
import com.kiwobollae.api.plantProfile.entity.enums.PlantStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PlantProfileResponse(
		Long id,
		Long userId,
		String speciesName,
		String nickname,
		LocalDate startDate,
		String thumbnailUrl,
		PlantStatus status,
		LocalDateTime createdAt
) {
	public static PlantProfileResponse from(PlantProfile plantProfile) {
		return new PlantProfileResponse(
				plantProfile.getId(),
				plantProfile.getUser().getId(),
				plantProfile.getSpeciesName(),
				plantProfile.getPlantName(),
				plantProfile.getStartDate(),
				plantProfile.getPlantImage(),
				plantProfile.getStatus(),
				plantProfile.getCreatedAt()
		);
	}
}
