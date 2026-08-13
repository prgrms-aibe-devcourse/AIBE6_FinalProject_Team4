package com.kiwobollae.api.board.dto.request;

import com.kiwobollae.api.board.entity.enums.BoardCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BoardPostCreateRequest(
		@NotNull BoardCategory category,
		@NotBlank @Size(max = 100) String title,
		@NotBlank @Size(max = 2000) String content,
		Long journalId,
		@Size(max = 1) List<String> imageUrls
) {
	public List<String> imageUrls() {
		return imageUrls == null ? List.of() : imageUrls;
	}
}
