package com.kiwobollae.api.commerce.entity;

import com.kiwobollae.api.commerce.entity.enums.ProductCategory;
import com.kiwobollae.api.commerce.entity.enums.ProductStatus;
import com.kiwobollae.api.global.common.BaseTimeEntity;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "products",
		indexes = {
				@Index(name = "idx_products_status_category_created_at", columnList = "status, category, created_at")
		},
		check = {
				@CheckConstraint(name = "chk_products_point_price_by_category",
						constraint = "((category IN ('KIT', 'SEEDLING', 'GACHA_PACK') AND point_price IS NOT NULL AND point_price >= 0) "
								+ "OR (category = 'EXCHANGE' AND point_price IS NULL))")
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

	@Column(name = "point_price")
	private Long pointPrice;

	@Column(nullable = false)
	private Integer stock;

	// SEEDLING 상품에서만 사용하는 자유텍스트 종 이름. 관리자가 관리하던 종 카탈로그가 사라져 FK 없이 문자열로 보관한다.
	@Column(name = "species_name", length = 100)
	private String speciesName;

	@Column(length = 2000)
	private String description;

	@Column(name = "image_url", length = 500)
	private String imageUrl;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ProductStatus status;

	public void updateInfo(
			String name,
			ProductCategory category,
			Long pointPrice,
			Integer stock,
			String speciesName,
			String description,
			String imageUrl
	) {
		this.name = name;
		this.category = category;
		this.pointPrice = pointPrice;
		this.stock = stock;
		this.speciesName = speciesName;
		this.description = description;
		this.imageUrl = imageUrl;
	}

	public void updateImage(String imageUrl) {
		this.imageUrl = imageUrl;
	}
}
