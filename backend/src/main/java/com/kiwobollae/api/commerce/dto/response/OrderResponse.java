package com.kiwobollae.api.commerce.dto.response;

import com.kiwobollae.api.commerce.entity.Order;
import com.kiwobollae.api.commerce.entity.enums.ConfirmedBy;
import com.kiwobollae.api.commerce.entity.enums.DeliveryStatus;
import com.kiwobollae.api.commerce.entity.enums.OrderStatus;
import java.time.LocalDateTime;

public record OrderResponse(
		Long id,
		Long userId,
		Long totalPoint,
		Long usedFreePoint,
		Long usedPaidPoint,
		OrderStatus status,
		DeliveryStatus deliveryStatus,
		String receiverName,
		String receiverPhone,
		String address,
		String addressDetail,
		LocalDateTime orderedAt,
		LocalDateTime deliveredAt,
		LocalDateTime cancelledAt,
		LocalDateTime confirmedAt,
		ConfirmedBy confirmedBy
) {
	public static OrderResponse from(Order order) {
		return new OrderResponse(
				order.getId(),
				order.getUser().getId(),
				order.getTotalPoint(),
				order.getUsedFreePoint(),
				order.getUsedPaidPoint(),
				order.getStatus(),
				order.getDeliveryStatus(),
				order.getReceiverName(),
				order.getReceiverPhone(),
				order.getAddress(),
				order.getAddressDetail(),
				order.getOrderedAt(),
				order.getDeliveredAt(),
				order.getCancelledAt(),
				order.getConfirmedAt(),
				order.getConfirmedBy()
		);
	}
}
