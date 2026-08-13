package com.kiwobollae.api.species.entity;

import com.kiwobollae.api.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "plant_species", uniqueConstraints = @UniqueConstraint(name = "uq_plant_species_name", columnNames = "name"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PlantSpecies extends BaseTimeEntity {

	@Column(nullable = false, length = 100)
	private String name;

	@Column(length = 50)
	private String category;

	@Column(name = "care_guide", length = 500)
	private String careGuide;

	public void updateInfo(String name, String category, String careGuide) {
		this.name = name;
		if (category != null) {
			this.category = category;
		}
		if (careGuide != null) {
			this.careGuide = careGuide;
		}
	}
}
