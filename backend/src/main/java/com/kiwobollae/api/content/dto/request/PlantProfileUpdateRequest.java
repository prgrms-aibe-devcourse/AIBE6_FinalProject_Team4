package com.kiwobollae.api.content.dto.request;

import com.kiwobollae.api.content.entity.enums.PlantStatus;
import jakarta.validation.constraints.Size;

public record PlantProfileUpdateRequest(
		@Size(max = 50) String nickname,
		@Size(max = 500) String thumbnailUrl,
		PlantStatus status
) {
}
