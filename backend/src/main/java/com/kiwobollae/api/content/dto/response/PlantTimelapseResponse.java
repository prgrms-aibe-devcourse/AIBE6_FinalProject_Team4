package com.kiwobollae.api.content.dto.response;

import com.kiwobollae.api.content.entity.PlantTimelapse;
import java.time.LocalDateTime;

public record PlantTimelapseResponse(
		String status,
		String videoUrl,
		String failReason,
		LocalDateTime requestedAt,
		LocalDateTime completedAt
) {
	public static PlantTimelapseResponse from(PlantTimelapse timelapse) {
		return new PlantTimelapseResponse(
				timelapse.getStatus().name(),
				timelapse.getVideoUrl(),
				timelapse.getFailReason(),
				timelapse.getRequestedAt(),
				timelapse.getCompletedAt()
		);
	}

	public static PlantTimelapseResponse none() {
		return new PlantTimelapseResponse("NONE", null, null, null, null);
	}
}
