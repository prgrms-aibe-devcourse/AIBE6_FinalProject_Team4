package com.kiwobollae.api.content.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record PlantProfileRequest(
		@NotNull Long speciesId,
		@NotBlank @Size(max = 50) String nickname,
		@NotNull LocalDate startDate,
		@Size(max = 500) String thumbnailUrl
) {
}
