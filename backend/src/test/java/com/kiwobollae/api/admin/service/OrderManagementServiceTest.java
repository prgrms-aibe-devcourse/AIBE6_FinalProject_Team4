package com.kiwobollae.api.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.commerce.dto.response.OrderDetailResponse;
import com.kiwobollae.api.commerce.dto.response.OrderResponse;
import com.kiwobollae.api.commerce.entity.Order;
import com.kiwobollae.api.commerce.entity.OrderItem;
import com.kiwobollae.api.commerce.entity.Product;
import com.kiwobollae.api.commerce.entity.enums.CancelledBy;
import com.kiwobollae.api.commerce.entity.enums.DeliveryStatus;
import com.kiwobollae.api.commerce.entity.enums.OrderStatus;
import com.kiwobollae.api.commerce.repository.OrderItemRepository;
import com.kiwobollae.api.commerce.repository.OrderRepository;
import com.kiwobollae.api.commerce.repository.ProductRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.notification.entity.enums.NotificationType;
import com.kiwobollae.api.notification.service.NotificationService;
import com.kiwobollae.api.point.entity.enums.PointRefType;
import com.kiwobollae.api.point.service.WalletService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class OrderManagementServiceTest {

	@Mock private OrderRepository orderRepository;
	@Mock private OrderItemRepository orderItemRepository;
	@Mock private ProductRepository productRepository;
	@Mock private WalletService walletService;
	@Mock private NotificationService notificationService;
	@InjectMocks private OrderManagementService orderManagementService;

	private Order mockOrder(Long id, OrderStatus status, DeliveryStatus deliveryStatus, Long userId) {
		Order order = mock(Order.class);
		User user = mock(User.class);
		lenient().when(user.getId()).thenReturn(userId);
		lenient().when(order.getId()).thenReturn(id);
		lenient().when(order.getUser()).thenReturn(user);
		lenient().when(order.getStatus()).thenReturn(status);
		lenient().when(order.getDeliveryStatus()).thenReturn(deliveryStatus);
		lenient().when(order.getTotalPoint()).thenReturn(2000L);
		lenient().when(order.getUsedFreePoint()).thenReturn(500L);
		lenient().when(order.getUsedPaidPoint()).thenReturn(1500L);
		lenient().when(order.getReceiverName()).thenReturn("홍길동");
		lenient().when(order.getReceiverPhone()).thenReturn("01012345678");
		lenient().when(order.getAddress()).thenReturn("서울시 강남구");
		lenient().when(order.getAddressDetail()).thenReturn("101동");
		lenient().when(order.getOrderedAt()).thenReturn(LocalDateTime.of(2026, 7, 28, 10, 0));
		return order;
	}

	@Test
	void getOrdersForAdminMapsRepositoryPage() {
		Pageable pageable = PageRequest.of(0, 10);
		Order order = mockOrder(1L, OrderStatus.PAID, DeliveryStatus.PREPARING, 7L);
		given(orderRepository.search(OrderStatus.PAID, null, null, null, null, pageable))
				.willReturn(new PageImpl<>(List.of(order)));
		OrderItem item = mock(OrderItem.class);
		Product product = mock(Product.class);
		lenient().when(item.getId()).thenReturn(100L);
		lenient().when(item.getOrder()).thenReturn(order);
		lenient().when(item.getProduct()).thenReturn(product);
		lenient().when(item.getProductName()).thenReturn("새싹 재배 키트");
		lenient().when(item.getQuantity()).thenReturn(1);
		lenient().when(item.getUnitPoint()).thenReturn(800L);
		given(orderItemRepository.findAllByOrderIdIn(List.of(1L))).willReturn(List.of(item));

		Page<OrderDetailResponse> result =
				orderManagementService.getOrdersForAdmin(OrderStatus.PAID, null, null, null, null, pageable);

		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().get(0).order().id()).isEqualTo(1L);
		assertThat(result.getContent().get(0).items()).hasSize(1);
	}

	@Test
	void getOrderForAdminReturnsDetailWithItems() {
		Order order = mockOrder(1L, OrderStatus.PAID, DeliveryStatus.PREPARING, 7L);
		given(orderRepository.findById(1L)).willReturn(Optional.of(order));
		OrderItem item = mock(OrderItem.class);
		Product product = mock(Product.class);
		lenient().when(item.getId()).thenReturn(100L);
		lenient().when(item.getOrder()).thenReturn(order);
		lenient().when(item.getProduct()).thenReturn(product);
		lenient().when(item.getProductName()).thenReturn("새싹 재배 키트");
		lenient().when(item.getQuantity()).thenReturn(1);
		lenient().when(item.getUnitPoint()).thenReturn(800L);
		given(orderItemRepository.findAllByOrderId(1L)).willReturn(List.of(item));

		OrderDetailResponse response = orderManagementService.getOrderForAdmin(1L);

		assertThat(response.order().id()).isEqualTo(1L);
		assertThat(response.items()).hasSize(1);
	}

	@Test
	void shipOrderTransitionsPreparingToShipping() {
		given(orderRepository.updateDeliveryStatusIfMatches(
				50L, DeliveryStatus.SHIPPING, OrderStatus.PAID, DeliveryStatus.PREPARING
		)).willReturn(1);
		Order refreshed = mockOrder(50L, OrderStatus.PAID, DeliveryStatus.SHIPPING, 7L);
		given(orderRepository.findById(50L)).willReturn(Optional.of(refreshed));

		OrderResponse response = orderManagementService.shipOrder(50L);

		assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.SHIPPING);
		verify(notificationService).notify(
				eq(7L), eq(NotificationType.DELIVERY), any(), any(), any(), any(), eq(50L)
		);
	}

	@Test
	void shipOrderFailsWithNotFoundWhenOrderDoesNotExist() {
		given(orderRepository.updateDeliveryStatusIfMatches(
				50L, DeliveryStatus.SHIPPING, OrderStatus.PAID, DeliveryStatus.PREPARING
		)).willReturn(0);
		given(orderRepository.existsById(50L)).willReturn(false);

		assertThatThrownBy(() -> orderManagementService.shipOrder(50L))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_FOUND));
		verify(notificationService, never()).notify(anyLong(), any(), any(), any(), any(), any(), anyLong());
	}

	@Test
	void shipOrderFailsWithInvalidStateWhenNotPreparing() {
		given(orderRepository.updateDeliveryStatusIfMatches(
				50L, DeliveryStatus.SHIPPING, OrderStatus.PAID, DeliveryStatus.PREPARING
		)).willReturn(0);
		given(orderRepository.existsById(50L)).willReturn(true);

		assertThatThrownBy(() -> orderManagementService.shipOrder(50L))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_INVALID_STATE));
		verify(notificationService, never()).notify(anyLong(), any(), any(), any(), any(), any(), anyLong());
	}

	@Test
	void deliverOrderTransitionsShippingToDelivered() {
		given(orderRepository.deliverIfMatches(
				eq(50L), eq(DeliveryStatus.DELIVERED), any(LocalDateTime.class), eq(OrderStatus.PAID), eq(DeliveryStatus.SHIPPING)
		)).willReturn(1);
		Order refreshed = mockOrder(50L, OrderStatus.PAID, DeliveryStatus.DELIVERED, 7L);
		given(orderRepository.findById(50L)).willReturn(Optional.of(refreshed));

		OrderResponse response = orderManagementService.deliverOrder(50L);

		assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
		verify(notificationService).notify(
				eq(7L), eq(NotificationType.DELIVERY), any(), any(), any(), any(), eq(50L)
		);
	}

	@Test
	void deliverOrderFailsWhenNotShipping() {
		given(orderRepository.deliverIfMatches(
				eq(50L), eq(DeliveryStatus.DELIVERED), any(LocalDateTime.class), eq(OrderStatus.PAID), eq(DeliveryStatus.SHIPPING)
		)).willReturn(0);
		given(orderRepository.existsById(50L)).willReturn(true);

		assertThatThrownBy(() -> orderManagementService.deliverOrder(50L))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_INVALID_STATE));
		verify(notificationService, never()).notify(anyLong(), any(), any(), any(), any(), any(), anyLong());
	}

	@Test
	void adminCancelOrderRestocksAndRestoresPointsOnSuccess() {
		Order order = mockOrder(50L, OrderStatus.PAID, DeliveryStatus.PREPARING, 7L);
		given(orderRepository.cancelIfMatches(
				eq(50L), eq(OrderStatus.CANCELLED), eq(OrderStatus.PAID), eq(DeliveryStatus.PREPARING),
				any(LocalDateTime.class), eq("품절"), eq(CancelledBy.ADMIN)
		)).willReturn(1);
		OrderItem item = mock(OrderItem.class);
		Product product = mock(Product.class);
		lenient().when(product.getId()).thenReturn(10L);
		lenient().when(item.getProduct()).thenReturn(product);
		lenient().when(item.getQuantity()).thenReturn(2);
		given(orderItemRepository.findAllByOrderId(50L)).willReturn(List.of(item));
		Order cancelled = mockOrder(50L, OrderStatus.CANCELLED, DeliveryStatus.PREPARING, 7L);
		given(orderRepository.findById(50L))
				.willReturn(Optional.of(order))
				.willReturn(Optional.of(cancelled));

		OrderResponse response = orderManagementService.adminCancelOrder(50L, "품절");

		assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
		verify(productRepository).incrementStock(10L, 2);
		verify(walletService).restorePurchasePoints(7L, 500L, 1500L, PointRefType.ORDER, 50L);
		verify(notificationService).notify(
				eq(7L), eq(NotificationType.DELIVERY), any(), eq("취소 사유: 품절"), any(), any(), eq(50L)
		);
	}

	@Test
	void adminCancelOrderFailsWhenOrderDoesNotExist() {
		given(orderRepository.findById(50L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> orderManagementService.adminCancelOrder(50L, "품절"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_FOUND));
		verify(walletService, never()).restorePurchasePoints(anyLong(), anyLong(), anyLong(), any(), anyLong());
		verify(notificationService, never()).notify(anyLong(), any(), any(), any(), any(), any(), anyLong());
	}

	@Test
	void adminCancelOrderFailsWithInvalidStateWhenNotPreparing() {
		Order order = mockOrder(50L, OrderStatus.PAID, DeliveryStatus.SHIPPING, 7L);
		given(orderRepository.findById(50L)).willReturn(Optional.of(order));
		given(orderRepository.cancelIfMatches(
				eq(50L), eq(OrderStatus.CANCELLED), eq(OrderStatus.PAID), eq(DeliveryStatus.PREPARING),
				any(LocalDateTime.class), eq("품절"), eq(CancelledBy.ADMIN)
		)).willReturn(0);
		given(orderRepository.existsById(50L)).willReturn(true);

		assertThatThrownBy(() -> orderManagementService.adminCancelOrder(50L, "품절"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_INVALID_STATE));
		verify(walletService, never()).restorePurchasePoints(anyLong(), anyLong(), anyLong(), any(), anyLong());
		verify(notificationService, never()).notify(anyLong(), any(), any(), any(), any(), any(), anyLong());
	}
}
