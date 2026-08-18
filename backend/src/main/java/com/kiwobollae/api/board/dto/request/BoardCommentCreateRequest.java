package com.kiwobollae.api.board.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BoardCommentCreateRequest(
		@NotBlank @Size(max = 500) String content,
		Long parentCommentId
) {
}
