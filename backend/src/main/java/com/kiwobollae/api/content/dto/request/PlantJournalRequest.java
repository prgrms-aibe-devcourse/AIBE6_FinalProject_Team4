package com.kiwobollae.api.content.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PlantJournalRequest(
		@NotNull Long plantProfileId,
		@Size(max = 2000) String content,
		@NotBlank @Size(max = 500) String imageUrl
) {
}
