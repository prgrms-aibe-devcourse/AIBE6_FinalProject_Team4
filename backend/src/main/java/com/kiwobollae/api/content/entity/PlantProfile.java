package com.kiwobollae.api.content.entity;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.content.entity.enums.PlantStatus;
import com.kiwobollae.api.global.common.BaseEntity;
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
}
