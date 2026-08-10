package com.kiwobollae.api.journal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JournalImageRequest(
		@NotBlank @Size(max = 500) String imageUrl,
		@NotBlank @Size(max = 64) String imageHash,
		boolean representative
) {
}
