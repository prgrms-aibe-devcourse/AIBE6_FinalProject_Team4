package com.kiwobollae.api.inquiry.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InquiryAnswerRequest(
		@NotBlank @Size(max = 1000) String answerContent
) {
}
