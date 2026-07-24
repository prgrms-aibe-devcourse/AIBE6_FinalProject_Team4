package com.kiwobollae.api.content.dto.response;

import com.kiwobollae.api.content.entity.Report;
import com.kiwobollae.api.content.entity.enums.ReportStatus;
import com.kiwobollae.api.content.entity.enums.ReportTargetType;
import java.time.LocalDateTime;

public record ReportResponse(
		Long id,
		Long reporterId,
		String reporterName,
		ReportTargetType targetType,
		Long targetId,
		String reason,
		ReportStatus status,
		LocalDateTime createdAt,
		String actionType,
		String actionDetail,
		Long processedAdminId,
		String processedAdminName,
		LocalDateTime processedAt
) {
	public static ReportResponse from(Report report) {
		return new ReportResponse(
				report.getId(),
				report.getReporter().getId(),
				report.getReporter().getName(),
				report.getTargetType(),
				report.getTargetId(),
				report.getReason(),
				report.getStatus(),
				report.getCreatedAt(),
				report.getActionType(),
				report.getActionDetail(),
				report.getProcessedAdmin() != null ? report.getProcessedAdmin().getId() : null,
				report.getProcessedAdmin() != null ? report.getProcessedAdmin().getName() : null,
				report.getProcessedAt()
		);
	}
}
