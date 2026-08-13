package com.kiwobollae.api.admin.service;

import com.kiwobollae.api.commerce.dto.response.OrderDetailResponse;
import com.kiwobollae.api.commerce.dto.response.OrderItemResponse;
import com.kiwobollae.api.commerce.dto.response.OrderResponse;
import com.kiwobollae.api.commerce.entity.Order;
import com.kiwobollae.api.commerce.entity.OrderItem;
import com.kiwobollae.api.commerce.entity.enums.CancelledBy;
import com.kiwobollae.api.commerce.entity.enums.DeliveryStatus;
import com.kiwobollae.api.commerce.entity.enums.OrderStatus;
import com.kiwobollae.api.commerce.repository.OrderItemRepository;
import com.kiwobollae.api.commerce.repository.OrderRepository;
import com.kiwobollae.api.commerce.repository.ProductRepository;
import com.kiwobollae.api.global.asset.AssetUrlResolver;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.notification.entity.enums.NotificationType;
import com.kiwobollae.api.notification.service.NotificationService;
import com.kiwobollae.api.point.entity.enums.PointRefType;
import com.kiwobollae.api.point.service.WalletService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderManagementService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final String REF_TYPE = "ORDER";

	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final ProductRepository productRepository;
	private final AssetUrlResolver assetUrlResolver;
	private final WalletService walletService;
	private final NotificationService notificationService;

	@Transactional(readOnly = true)
	public Page<OrderDetailResponse> getOrdersForAdmin(
			OrderStatus status,
			DeliveryStatus deliveryStatus,
			Long userId,
			LocalDateTime from,
			LocalDateTime to,
			Pageable pageable
	) {
		Page<Order> orders = orderRepository.search(status, deliveryStatus, userId, from, to, pageable);
		List<Long> orderIds = orders.getContent().stream().map(Order::getId).toList();
		Map<Long, List<OrderItemResponse>> itemsByOrderId = orderItemRepository.findAllByOrderIdIn(orderIds).stream()
				.collect(Collectors.groupingBy(
						item -> item.getOrder().getId(),
						Collectors.mapping(this::orderItemResponse, Collectors.toList())
				));
		return orders.map(order -> new OrderDetailResponse(
				OrderResponse.from(order),
				itemsByOrderId.getOrDefault(order.getId(), List.of())
		));
	}

	@Transactional(readOnly = true)
	public OrderDetailResponse getOrderForAdmin(Long id) {
		Order order = findOrderForAdmin(id);
		List<OrderItemResponse> items = orderItemRepository.findAllByOrderId(id).stream()
				.map(this::orderItemResponse)
				.toList();
		return new OrderDetailResponse(OrderResponse.from(order), items);
	}

	private OrderItemResponse orderItemResponse(OrderItem item) {
		return OrderItemResponse.from(item, assetUrlResolver.resolve(item.getProduct().getImageUrl()));
	}

	@Transactional
	public OrderResponse shipOrder(Long id) {
		int updated = orderRepository.updateDeliveryStatusIfMatches(
				id, DeliveryStatus.SHIPPING, OrderStatus.PAID, DeliveryStatus.PREPARING
		);
		if (updated == 0) {
			throwNotFoundOrInvalidState(id);
		}
		Order order = findOrderForAdmin(id);
		notificationService.notify(
				order.getUser().getId(),
				NotificationType.DELIVERY,
				"주문하신 상품이 배송을 시작했어요 📦",
				"주문 #" + id + " · " + order.getAddress(),
				"/my/orders#order-" + id,
				REF_TYPE,
				id
		);
		return OrderResponse.from(order);
	}

	@Transactional
	public OrderResponse deliverOrder(Long id) {
		int updated = orderRepository.deliverIfMatches(
				id, DeliveryStatus.DELIVERED, LocalDateTime.now(KST), OrderStatus.PAID, DeliveryStatus.SHIPPING
		);
		if (updated == 0) {
			throwNotFoundOrInvalidState(id);
		}
		Order order = findOrderForAdmin(id);
		notificationService.notify(
				order.getUser().getId(),
				NotificationType.DELIVERY,
				"주문하신 상품이 배송완료됐어요 🎉",
				"주문 #" + id + " · " + order.getAddress(),
				"/my/orders#order-" + id,
				REF_TYPE,
				id
		);
		return OrderResponse.from(order);
	}

	@Transactional
	public OrderResponse adminCancelOrder(Long id, String reason) {
		Order order = findOrderForAdmin(id);
		int updated = orderRepository.cancelIfMatches(
				id, OrderStatus.CANCELLED, OrderStatus.PAID, DeliveryStatus.PREPARING,
				LocalDateTime.now(KST), reason, CancelledBy.ADMIN
		);
		if (updated == 0) {
			throwNotFoundOrInvalidState(id);
		}

		for (OrderItem item : orderItemRepository.findAllByOrderId(id)) {
			productRepository.incrementStock(item.getProduct().getId(), item.getQuantity());
		}
		walletService.restorePurchasePoints(
				order.getUser().getId(),
				order.getUsedFreePoint(),
				order.getUsedPaidPoint(),
				PointRefType.ORDER,
				id
		);
		notificationService.notify(
				order.getUser().getId(),
				NotificationType.DELIVERY,
				"주문이 취소됐어요",
				reason != null && !reason.isBlank() ? "취소 사유: " + reason : "주문 #" + id + "가 취소됐어요.",
				"/my/orders#order-" + id,
				REF_TYPE,
				id
		);
		return OrderResponse.from(findOrderForAdmin(id));
	}

	private Order findOrderForAdmin(Long id) {
		return orderRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
	}

	private void throwNotFoundOrInvalidState(Long id) {
		if (!orderRepository.existsById(id)) {
			throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
		}
		throw new BusinessException(ErrorCode.ORDER_INVALID_STATE);
	}
}
