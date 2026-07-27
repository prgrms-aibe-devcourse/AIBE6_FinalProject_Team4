package com.kiwobollae.api.commerce.entity.enums;

import org.springframework.data.domain.Sort;

public enum ProductSort {
	LATEST,
	PRICE_ASC,
	PRICE_DESC;

	public Sort toSort() {
		return switch (this) {
			case LATEST -> Sort.by(
					Sort.Order.desc("createdAt"),
					Sort.Order.desc("id")
			);
			case PRICE_ASC -> Sort.by(
					Sort.Order.asc("pointPrice"),
					Sort.Order.desc("id")
			);
			case PRICE_DESC -> Sort.by(
					Sort.Order.desc("pointPrice"),
					Sort.Order.desc("id")
			);
		};
	}
}
