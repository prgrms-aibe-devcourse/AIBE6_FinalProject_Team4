package com.kiwobollae.api.content.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.kiwobollae.api.content.entity.PlantProfile;
import com.kiwobollae.api.content.entity.PlantTimelapse;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PlantTimelapseResponseTest {

	@Test
	void fromMapsCompletedTimelapse() {
		PlantTimelapse timelapse = PlantTimelapse.create(PlantProfile.builder().build(), LocalDateTime.of(2026, 8, 10, 9, 0));
		timelapse.complete("/api/v1/plants/timelapse-videos/7/abc.mp4", LocalDateTime.of(2026, 8, 10, 9, 5));

		PlantTimelapseResponse response = PlantTimelapseResponse.from(timelapse);

		assertThat(response.status()).isEqualTo("COMPLETED");
		assertThat(response.videoUrl()).isEqualTo("/api/v1/plants/timelapse-videos/7/abc.mp4");
		assertThat(response.failReason()).isNull();
	}

	@Test
	void noneReturnsSyntheticNoneStatus() {
		PlantTimelapseResponse response = PlantTimelapseResponse.none();

		assertThat(response.status()).isEqualTo("NONE");
		assertThat(response.videoUrl()).isNull();
		assertThat(response.requestedAt()).isNull();
	}
}
