package com.kiwobollae.api.board.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BoardPostUpdateRequest(
		@NotBlank @Size(max = 100) String title,
		@NotBlank @Size(max = 2000) String content,
		@Size(max = 1) List<String> imageUrls
) {
	public List<String> imageUrls() {
		return imageUrls == null ? List.of() : imageUrls;
	}
}
