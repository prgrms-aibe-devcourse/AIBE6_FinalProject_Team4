package com.kiwobollae.api.commerce.dto.response;

import com.kiwobollae.api.commerce.entity.Order;
import com.kiwobollae.api.commerce.entity.enums.CancelledBy;
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
		String zipCode,
		String address,
		String addressDetail,
		LocalDateTime orderedAt,
		LocalDateTime deliveredAt,
		LocalDateTime cancelledAt,
		String cancelReason,
		CancelledBy cancelledBy,
		LocalDateTime confirmedAt,
		ConfirmedBy confirmedBy,
		boolean cancellable,
		boolean confirmable
) {
	public static OrderResponse from(Order order) {
		boolean cancellable = order.getStatus() == OrderStatus.PAID
				&& order.getDeliveryStatus() == DeliveryStatus.PREPARING;
		boolean confirmable = order.getStatus() == OrderStatus.PAID
				&& order.getDeliveryStatus() == DeliveryStatus.DELIVERED;
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
				order.getZipCode(),
				order.getAddress(),
				order.getAddressDetail(),
				order.getOrderedAt(),
				order.getDeliveredAt(),
				order.getCancelledAt(),
				order.getCancelReason(),
				order.getCancelledBy(),
				order.getConfirmedAt(),
				order.getConfirmedBy(),
				cancellable,
				confirmable
		);
	}
}
