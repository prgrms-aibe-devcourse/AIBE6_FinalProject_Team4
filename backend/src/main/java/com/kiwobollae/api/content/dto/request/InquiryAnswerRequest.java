package com.kiwobollae.api.content.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InquiryAnswerRequest(
		@NotBlank @Size(max = 2000) String answerContent
) {
}
