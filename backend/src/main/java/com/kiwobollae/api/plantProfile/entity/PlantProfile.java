package com.kiwobollae.api.plantProfile.entity;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.plantProfile.entity.enums.PlantStatus;
import com.kiwobollae.api.global.common.BaseEntity;
import com.kiwobollae.api.species.entity.PlantSpecies;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "plant_profile", indexes = {
		@Index(name = "idx_plant_profile_user_id", columnList = "user_id"),
		@Index(name = "idx_plant_profile_species_id", columnList = "specie_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PlantProfile extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "specie_id", nullable = false)
	private PlantSpecies species;

	@Column(name = "plant_name", nullable = false, length = 50)
	private String plantName;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "plant_image", length = 500)
	private String plantImage;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PlantStatus status;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	// null이면 미지급(재지급 가능), 값이 있으면 현재 지급 중인 일지 완료 보상이 있음을 뜻한다.
	@Column(name = "journal_reward_granted_at")
	private LocalDateTime journalRewardGrantedAt;

	public static PlantProfile create(User user, PlantSpecies species,
			String plantName, LocalDate startDate, String plantImage) {
		return PlantProfile.builder()
				.user(user)
				.species(species)
				.plantName(plantName)
				.startDate(startDate)
				.plantImage(plantImage)
				.status(PlantStatus.GROWING)
				.createdAt(LocalDateTime.now())
				.build();
	}

	public void updateProfile(String plantName, String plantImage, PlantStatus status) {
		if (plantName != null) {
			this.plantName = plantName;
		}
		if (plantImage != null) {
			this.plantImage = plantImage;
		}
		if (status != null) {
			this.status = status;
		}
	}

	// updateProfile()은 null을 "값 유지"로 해석하므로 대표사진을 비우는 용도로 쓸 수 없다 —
	// 대표사진을 제공하던 일지가 삭제돼 대체할 사진이 없을 때 전용으로 쓴다.
	public void clearPlantImage() {
		this.plantImage = null;
	}
}
