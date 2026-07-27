package com.kiwobollae.api.report.dto.request;

import jakarta.validation.constraints.Size;

public record ReportActionRequest(
		@Size(max = 50) String actionType,
		@Size(max = 500) String actionDetail
) {
}
