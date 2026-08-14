package com.kiwobollae.api.payment.dto.response;

import com.kiwobollae.api.payment.entity.ChargeProduct;

public record ChargeProductResponse(
		Long id,
		String name,
		Long price,
		Long pointAmount,
		Boolean isActive,
		Long version
) {
	public static ChargeProductResponse from(ChargeProduct chargeProduct) {
		return new ChargeProductResponse(
				chargeProduct.getId(),
				chargeProduct.getName(),
				chargeProduct.getPrice(),
				chargeProduct.getPointAmount(),
				chargeProduct.getIsActive(),
				chargeProduct.getVersion()
		);
	}
}
