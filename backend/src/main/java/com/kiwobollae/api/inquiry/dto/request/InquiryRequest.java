package com.kiwobollae.api.inquiry.dto.request;

import com.kiwobollae.api.inquiry.entity.enums.InquiryCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InquiryRequest(
		@NotNull InquiryCategory category,
		@NotBlank @Size(max = 100) String title,
		@NotBlank @Size(max = 1000) String content
) {
}
