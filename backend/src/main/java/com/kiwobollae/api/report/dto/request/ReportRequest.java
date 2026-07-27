package com.kiwobollae.api.report.dto.request;

import com.kiwobollae.api.report.entity.enums.ReportTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportRequest(
		@NotNull ReportTargetType targetType,
		@NotNull Long targetId,
		@NotBlank @Size(max = 200) String reason
) {
}
