package com.kiwobollae.api.content.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlantSpeciesRequest(
		@NotBlank @Size(max = 100) String name,
		@Size(max = 50) String category,
		@Size(max = 500) String careGuide
) {
}
