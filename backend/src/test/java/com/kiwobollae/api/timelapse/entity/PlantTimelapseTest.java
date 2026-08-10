package com.kiwobollae.api.timelapse.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.kiwobollae.api.plantProfile.entity.PlantProfile;
import com.kiwobollae.api.timelapse.entity.enums.PlantTimelapseStatus;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class PlantTimelapseTest {

	@Test
	void createStartsAsPending() {
		PlantProfile profile = PlantProfile.builder().build();
		LocalDateTime now = LocalDateTime.of(2026, 8, 10, 9, 0);

		PlantTimelapse timelapse = PlantTimelapse.create(profile, now);

		assertThat(timelapse.getStatus()).isEqualTo(PlantTimelapseStatus.PENDING);
		assertThat(timelapse.getRequestedAt()).isEqualTo(now);
		assertThat(timelapse.getVideoUrl()).isNull();
		assertThat(timelapse.getCompletedAt()).isNull();
	}

	@Test
	void completeSetsVideoUrlAndClearsFailReason() {
		PlantTimelapse timelapse = PlantTimelapse.create(PlantProfile.builder().build(), LocalDateTime.now());
		LocalDateTime completedAt = LocalDateTime.of(2026, 8, 10, 9, 5);

		timelapse.complete("/api/v1/plants/timelapse-videos/7/abc.mp4", completedAt);

		assertThat(timelapse.getStatus()).isEqualTo(PlantTimelapseStatus.COMPLETED);
		assertThat(timelapse.getVideoUrl()).isEqualTo("/api/v1/plants/timelapse-videos/7/abc.mp4");
		assertThat(timelapse.getFailReason()).isNull();
		assertThat(timelapse.getCompletedAt()).isEqualTo(completedAt);
	}

	@Test
	void failSetsFailReason() {
		PlantTimelapse timelapse = PlantTimelapse.create(PlantProfile.builder().build(), LocalDateTime.now());
		LocalDateTime completedAt = LocalDateTime.of(2026, 8, 10, 9, 5);

		timelapse.fail("ffmpeg exited with code 1", completedAt);

		assertThat(timelapse.getStatus()).isEqualTo(PlantTimelapseStatus.FAILED);
		assertThat(timelapse.getFailReason()).isEqualTo("ffmpeg exited with code 1");
		assertThat(timelapse.getCompletedAt()).isEqualTo(completedAt);
	}

	@Test
	void restartResetsToPendingAndClearsPreviousResult() {
		PlantTimelapse timelapse = PlantTimelapse.create(PlantProfile.builder().build(), LocalDateTime.now());
		timelapse.complete("/api/v1/plants/timelapse-videos/7/old.mp4", LocalDateTime.now());
		LocalDateTime restartedAt = LocalDateTime.of(2026, 8, 11, 9, 0);

		timelapse.restart(restartedAt);

		assertThat(timelapse.getStatus()).isEqualTo(PlantTimelapseStatus.PENDING);
		assertThat(timelapse.getVideoUrl()).isNull();
		assertThat(timelapse.getFailReason()).isNull();
		assertThat(timelapse.getCompletedAt()).isNull();
		assertThat(timelapse.getRequestedAt()).isEqualTo(restartedAt);
	}
}
