package com.kiwobollae.api.timelapse.entity;

import com.kiwobollae.api.plantProfile.entity.PlantProfile;
import com.kiwobollae.api.timelapse.entity.enums.PlantTimelapseStatus;
import com.kiwobollae.api.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "plant_timelapse",
		uniqueConstraints = @UniqueConstraint(name = "uq_plant_timelapse_plant_profile_id", columnNames = "plant_profile_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PlantTimelapse extends BaseEntity {

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "plant_profile_id", nullable = false)
	private PlantProfile plantProfile;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PlantTimelapseStatus status;

	@Column(name = "video_url", length = 500)
	private String videoUrl;

	@Column(name = "fail_reason", length = 500)
	private String failReason;

	@Column(name = "requested_at", nullable = false)
	private LocalDateTime requestedAt;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	public static PlantTimelapse create(PlantProfile plantProfile, LocalDateTime requestedAt) {
		return PlantTimelapse.builder()
				.plantProfile(plantProfile)
				.status(PlantTimelapseStatus.PENDING)
				.requestedAt(requestedAt)
				.build();
	}

	public void restart(LocalDateTime requestedAt) {
		this.status = PlantTimelapseStatus.PENDING;
		this.videoUrl = null;
		this.failReason = null;
		this.requestedAt = requestedAt;
		this.completedAt = null;
	}

	public void complete(String videoUrl, LocalDateTime completedAt) {
		this.status = PlantTimelapseStatus.COMPLETED;
		this.videoUrl = videoUrl;
		this.failReason = null;
		this.completedAt = completedAt;
	}

	public void fail(String failReason, LocalDateTime completedAt) {
		this.status = PlantTimelapseStatus.FAILED;
		this.failReason = failReason;
		this.completedAt = completedAt;
	}
}
