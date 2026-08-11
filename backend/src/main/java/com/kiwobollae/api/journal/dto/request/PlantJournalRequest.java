package com.kiwobollae.api.journal.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PlantJournalRequest(
		@NotNull Long plantProfileId,
		@Size(max = 2000) String content,
		@NotNull @Size(min = 1, max = 3) @Valid List<JournalImageRequest> images
) {
}
