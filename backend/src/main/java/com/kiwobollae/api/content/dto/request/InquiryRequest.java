package com.kiwobollae.api.content.dto.request;

import com.kiwobollae.api.content.entity.enums.InquiryCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InquiryRequest(
		@NotNull InquiryCategory category,
		@NotBlank @Size(max = 200) String title,
		@NotBlank @Size(max = 2000) String content
) {
}
