package com.kiwobollae.api.commerce.entity;

import com.kiwobollae.api.commerce.entity.enums.ActiveStatus;
import com.kiwobollae.api.commerce.entity.enums.ProductCategory;
import com.kiwobollae.api.content.entity.PlantSpecies;
import com.kiwobollae.api.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "products", indexes = {
		@Index(name = "idx_products_status_category_created_at", columnList = "status, category, created_at")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Product extends BaseTimeEntity {

	@Column(nullable = false, length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ProductCategory category;

	@Column(name = "point_price", nullable = false)
	private Long pointPrice;

	@Column(nullable = false)
	private Integer stock;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "plant_species_id")
	private PlantSpecies plant;

	@Column(length = 2000)
	private String description;

	@Column(name = "image_url", length = 500)
	private String imageUrl;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ActiveStatus status;
}
