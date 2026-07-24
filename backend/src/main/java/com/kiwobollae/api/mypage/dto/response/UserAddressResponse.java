package com.kiwobollae.api.mypage.dto.response;

import com.kiwobollae.api.mypage.entity.UserAddress;
import java.time.LocalDateTime;

public record UserAddressResponse(
		Long id,
		Long userId,
		String receiverName,
		String receiverPhone,
		String zipCode,
		String address,
		String addressDetail,
		Boolean isDefault,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {
	public static UserAddressResponse from(UserAddress userAddress) {
		return new UserAddressResponse(
				userAddress.getId(),
				userAddress.getUser().getId(),
				userAddress.getReceiverName(),
				userAddress.getReceiverPhone(),
				userAddress.getZipCode(),
				userAddress.getAddress(),
				userAddress.getAddressDetail(),
				userAddress.getIsDefault(),
				userAddress.getCreatedAt(),
				userAddress.getUpdatedAt()
		);
	}
}
