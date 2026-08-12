package com.kiwobollae.api.commerce.service;

import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.commerce.dto.request.CartItemRequest;
import com.kiwobollae.api.commerce.dto.response.CartItemResponse;
import com.kiwobollae.api.commerce.dto.response.CartResponse;
import com.kiwobollae.api.commerce.entity.CartItem;
import com.kiwobollae.api.commerce.entity.Product;
import com.kiwobollae.api.commerce.entity.enums.ProductStatus;
import com.kiwobollae.api.commerce.repository.CartItemRepository;
import com.kiwobollae.api.commerce.repository.ProductRepository;
import com.kiwobollae.api.global.asset.AssetUrlResolver;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.point.service.WalletService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {

	private static final int MAX_QUANTITY_PER_ITEM = 99;

	private final CartItemRepository cartItemRepository;
	private final ProductRepository productRepository;
	private final UserRepository userRepository;
	private final WalletService walletService;
	private final AssetUrlResolver assetUrlResolver;

	@Transactional
	public CartItemResponse addItem(Long userId, CartItemRequest request) {
		Product product = productRepository.findByIdAndStatus(request.productId(), ProductStatus.ACTIVE)
				.orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
		// EXCHANGE 카테고리는 point_price가 없어 포인트로 담을 수 없다.
		if (product.getPointPrice() == null) {
			throw new BusinessException(ErrorCode.PRODUCT_NOT_AVAILABLE);
		}

		CartItem cartItem = cartItemRepository.findByUserIdAndProductId(userId, request.productId())
				.orElse(null);
		int newQuantity = (cartItem == null ? 0 : cartItem.getQuantity()) + request.quantity();
		validateQuantity(newQuantity, product);

		if (cartItem == null) {
			cartItem = cartItemRepository.save(CartItem.builder()
					.user(userRepository.getReferenceById(userId))
					.product(product)
					.quantity(newQuantity)
					.build());
		} else {
			cartItem.changeQuantity(newQuantity);
		}
		return response(cartItem);
	}

	public CartResponse getCart(Long userId) {
		List<CartItemResponse> items = cartItemRepository.findAllByUserIdOrderByIdDesc(userId).stream()
				.map(this::response)
				.toList();
		long expectedTotal = items.stream()
				.mapToLong(item -> item.unitPrice() * item.quantity())
				.sum();
		long walletBalance = walletService.getWallet(userId).balance();
		return new CartResponse(items, expectedTotal, walletBalance);
	}

	@Transactional
	public CartItemResponse updateQuantity(Long userId, Long cartItemId, Integer quantity) {
		CartItem cartItem = cartItemRepository.findByIdAndUserId(cartItemId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));
		// Stock may have dropped below what's already in the cart since it was added. A
		// decrease should still be allowed even then (the user is trying to fix exactly
		// that problem) — only block the request when it *increases* the quantity past
		// current stock. CartItemResponse.stockShortage already flags the remaining gap.
		if (quantity > cartItem.getQuantity()) {
			validateQuantity(quantity, cartItem.getProduct());
		} else if (quantity > MAX_QUANTITY_PER_ITEM) {
			throw new BusinessException(ErrorCode.CART_QUANTITY_LIMIT_EXCEEDED);
		}
		cartItem.changeQuantity(quantity);
		return response(cartItem);
	}

	@Transactional
	public void deleteItem(Long userId, Long cartItemId) {
		CartItem cartItem = cartItemRepository.findByIdAndUserId(cartItemId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));
		cartItemRepository.delete(cartItem);
	}

	@Transactional
	public void deleteItems(Long userId, List<Long> ids) {
		Set<Long> requestedIds = new LinkedHashSet<>(ids);
		List<CartItem> found = cartItemRepository.findAllByIdInAndUserId(List.copyOf(requestedIds), userId);
		if (found.size() != requestedIds.size()) {
			Set<Long> foundIds = found.stream().map(CartItem::getId).collect(Collectors.toSet());
			Set<Long> missingIds = new LinkedHashSet<>(requestedIds);
			missingIds.removeAll(foundIds);
			throw new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND, Map.of("missingIds", missingIds));
		}
		cartItemRepository.deleteAll(found);
	}

	private void validateQuantity(int quantity, Product product) {
		if (quantity > MAX_QUANTITY_PER_ITEM) {
			throw new BusinessException(ErrorCode.CART_QUANTITY_LIMIT_EXCEEDED);
		}
		if (quantity > product.getStock()) {
			throw new BusinessException(
					ErrorCode.CART_QUANTITY_EXCEEDS_STOCK,
					Map.of("availableStock", product.getStock())
			);
		}
	}

	private CartItemResponse response(CartItem cartItem) {
		return CartItemResponse.from(
				cartItem,
				assetUrlResolver.resolve(cartItem.getProduct().getImageUrl())
		);
	}
}
