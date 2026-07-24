package com.kiwobollae.api.content.dto.response;

import com.kiwobollae.api.content.entity.PlantSpecies;
import java.time.LocalDateTime;

public record PlantSpeciesResponse(
		Long id,
		String name,
		String category,
		String careGuide,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {
	public static PlantSpeciesResponse from(PlantSpecies plantSpecies) {
		return new PlantSpeciesResponse(
				plantSpecies.getId(),
				plantSpecies.getName(),
				plantSpecies.getCategory(),
				plantSpecies.getCareGuide(),
				plantSpecies.getCreatedAt(),
				plantSpecies.getUpdatedAt()
		);
	}
}
