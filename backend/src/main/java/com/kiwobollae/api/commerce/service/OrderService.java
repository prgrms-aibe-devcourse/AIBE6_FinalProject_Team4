package com.kiwobollae.api.commerce.service;

import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.commerce.dto.request.OrderCreateRequest;
import com.kiwobollae.api.commerce.dto.response.OrderDetailResponse;
import com.kiwobollae.api.commerce.dto.response.OrderItemResponse;
import com.kiwobollae.api.commerce.dto.response.OrderResponse;
import com.kiwobollae.api.commerce.entity.CartItem;
import com.kiwobollae.api.commerce.entity.Order;
import com.kiwobollae.api.commerce.entity.OrderItem;
import com.kiwobollae.api.commerce.entity.Product;
import com.kiwobollae.api.commerce.entity.enums.ConfirmedBy;
import com.kiwobollae.api.commerce.entity.enums.DeliveryStatus;
import com.kiwobollae.api.commerce.entity.enums.OrderStatus;
import com.kiwobollae.api.commerce.entity.enums.ProductStatus;
import com.kiwobollae.api.commerce.repository.CartItemRepository;
import com.kiwobollae.api.commerce.repository.OrderItemRepository;
import com.kiwobollae.api.commerce.repository.OrderRepository;
import com.kiwobollae.api.commerce.repository.ProductRepository;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.infra.service.IdempotencyExecution;
import com.kiwobollae.api.infra.service.IdempotencyService;
import com.kiwobollae.api.point.dto.response.PointDeductionResult;
import com.kiwobollae.api.point.entity.enums.PointRefType;
import com.kiwobollae.api.point.service.WalletService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class OrderService {

	private static final String API_TYPE = "ORDER_CREATE";
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final CartItemRepository cartItemRepository;
	private final ProductRepository productRepository;
	private final UserRepository userRepository;
	private final WalletService walletService;
	private final IdempotencyService idempotencyService;
	private final ObjectMapper objectMapper;

	@Transactional(isolation = Isolation.REPEATABLE_READ)
	public OrderDetailResponse createOrder(Long userId, String idempotencyKey, OrderCreateRequest request) {
		validate(userId, idempotencyKey, request);
		IdempotencyExecution execution = idempotencyService.start(
				userId,
				API_TYPE,
				idempotencyKey,
				hash(request)
		);
		if (execution.replay()) {
			return deserialize(execution.key().getResponseSnapshot());
		}

		// 행 잠금으로 조회한다: 같은 카트 항목으로 동시에 들어온 다른 결제 요청(다른 멱등키)이
		// 먼저 처리된 트랜잭션이 끝날 때까지 대기했다가, 이미 삭제된 걸 보고 안전하게 실패하도록 한다.
		List<CartItem> cartItems = cartItemRepository.findAllByIdInAndUserIdForUpdate(request.cartItemIds(), userId);
		if (cartItems.size() != new HashSet<>(request.cartItemIds()).size()) {
			throw new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND);
		}

		long totalPoint = 0L;
		for (CartItem cartItem : cartItems) {
			Product product = cartItem.getProduct();
			if (product.getStatus() != ProductStatus.ACTIVE || product.getPointPrice() == null) {
				throw new BusinessException(ErrorCode.PRODUCT_NOT_AVAILABLE);
			}
			int decremented = productRepository.decrementStockIfAvailable(product.getId(), cartItem.getQuantity());
			if (decremented == 0) {
				throw new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK);
			}
			try {
				totalPoint = Math.addExact(
						totalPoint,
						Math.multiplyExact(product.getPointPrice(), cartItem.getQuantity().longValue())
				);
			} catch (ArithmeticException exception) {
				throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
			}
		}

		Order order = orderRepository.saveAndFlush(Order.create(
				userRepository.getReferenceById(userId),
				totalPoint,
				request.receiverName(),
				request.receiverPhone(),
				request.address(),
				request.addressDetail(),
				LocalDateTime.now(KST)
		));

		PointDeductionResult pointUsage = walletService.deductForOrderPurchase(
				userId,
				totalPoint,
				request.requestedFreePoint(),
				order.getId()
		);
		order.applyPointUsage(pointUsage);

		List<OrderItem> orderItems = new ArrayList<>();
		for (CartItem cartItem : cartItems) {
			Product product = cartItem.getProduct();
			orderItems.add(OrderItem.builder()
					.order(order)
					.product(product)
					.productName(product.getName())
					.quantity(cartItem.getQuantity())
					.unitPoint(product.getPointPrice())
					.build());
		}
		orderItemRepository.saveAll(orderItems);
		cartItemRepository.deleteAll(cartItems);

		OrderDetailResponse response = new OrderDetailResponse(
				OrderResponse.from(order),
				orderItems.stream().map(OrderItemResponse::from).toList()
		);
		String responseSnapshot = serialize(response);
		idempotencyService.succeed(execution.key(), 201, responseSnapshot, "ORDER", order.getId());
		return response;
	}

	@Transactional(readOnly = true)
	public Page<OrderResponse> getOrders(Long userId, Pageable pageable) {
		return orderRepository.findAllByUserId(userId, pageable).map(OrderResponse::from);
	}

	@Transactional(readOnly = true)
	public OrderDetailResponse getOrder(Long userId, Long orderId) {
		Order order = orderRepository.findByIdAndUserId(orderId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		List<OrderItemResponse> items = orderItemRepository.findAllByOrderId(order.getId()).stream()
				.map(OrderItemResponse::from)
				.toList();
		return new OrderDetailResponse(OrderResponse.from(order), items);
	}

	@Transactional
	public void cancelOrder(Long userId, Long orderId) {
		Order order = orderRepository.findByIdAndUserId(orderId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

		int updated = orderRepository.cancelIfMatches(
				orderId,
				OrderStatus.CANCELLED,
				OrderStatus.PAID,
				DeliveryStatus.PREPARING,
				LocalDateTime.now(KST)
		);
		if (updated == 0) {
			throw new BusinessException(ErrorCode.ORDER_INVALID_STATE);
		}

		for (OrderItem item : orderItemRepository.findAllByOrderId(orderId)) {
			productRepository.incrementStock(item.getProduct().getId(), item.getQuantity());
		}
		walletService.restorePurchasePoints(
				userId,
				order.getUsedFreePoint(),
				order.getUsedPaidPoint(),
				PointRefType.ORDER,
				orderId
		);
	}

	@Transactional
	public void confirmOrder(Long userId, Long orderId) {
		orderRepository.findByIdAndUserId(orderId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

		int updated = orderRepository.confirmIfMatches(
				orderId,
				OrderStatus.PURCHASE_CONFIRMED,
				OrderStatus.PAID,
				DeliveryStatus.DELIVERED,
				LocalDateTime.now(KST),
				ConfirmedBy.USER
		);
		if (updated == 0) {
			throw new BusinessException(ErrorCode.ORDER_INVALID_STATE);
		}
	}

	private void validate(Long userId, String idempotencyKey, OrderCreateRequest request) {
		if (userId == null) {
			throw new BusinessException(ErrorCode.AUTH_AUTHENTICATION_REQUIRED);
		}
		if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}
		Set<Long> distinctIds = new HashSet<>(request.cartItemIds());
		if (distinctIds.size() != request.cartItemIds().size()) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED);
		}
	}

	private String hash(OrderCreateRequest request) {
		try {
			String sortedIds = request.cartItemIds().stream()
					.sorted()
					.map(String::valueOf)
					.reduce((a, b) -> a + "," + b)
					.orElse("");
			String value = sortedIds + ":" + request.requestedFreePoint() + ":" + request.receiverName()
					+ ":" + request.receiverPhone() + ":" + request.address() + ":" + request.addressDetail();
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256")
							.digest(value.getBytes(StandardCharsets.UTF_8))
			);
		} catch (NoSuchAlgorithmException exception) {
			throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
		}
	}

	private String serialize(OrderDetailResponse response) {
		try {
			return objectMapper.writeValueAsString(response);
		} catch (Exception exception) {
			throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
		}
	}

	private OrderDetailResponse deserialize(String snapshot) {
		try {
			return objectMapper.readValue(snapshot, OrderDetailResponse.class);
		} catch (Exception exception) {
			throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
		}
	}
}
