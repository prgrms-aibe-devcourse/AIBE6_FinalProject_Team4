package com.kiwobollae.api.commerce.controller;

import com.kiwobollae.api.commerce.dto.request.CartItemQuantityRequest;
import com.kiwobollae.api.commerce.dto.request.CartItemRequest;
import com.kiwobollae.api.commerce.dto.response.CartItemResponse;
import com.kiwobollae.api.commerce.dto.response.CartResponse;
import com.kiwobollae.api.commerce.service.CartService;
import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "장바구니", description = "장바구니 담기/조회/수정 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/order/cart")
public class CartController {

	private final CartService cartService;

	@Operation(summary = "장바구니 담기", description = "동일 상품이 이미 있으면 수량을 합산합니다. 1~99개, 재고 초과 시 자동 조정 없이 실패합니다.")
	@PostMapping("/items")
	public ResponseEntity<ApiResponse<CartItemResponse>> addItem(
			@AuthenticationPrincipal Long userId,
			@Valid @RequestBody CartItemRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(cartService.addItem(userId, request)));
	}

	@Operation(summary = "장바구니 조회", description = "현재가·품절·재고 부족 플래그와 예상 합계, 보유 포인트를 함께 반환합니다.")
	@GetMapping
	public ResponseEntity<ApiResponse<CartResponse>> getCart(@AuthenticationPrincipal Long userId) {
		return ResponseEntity.ok(ApiResponse.success(cartService.getCart(userId)));
	}

	@Operation(summary = "장바구니 수량 변경", description = "1~99개, 재고 초과 시 자동 조정 없이 실패합니다.")
	@PatchMapping("/items/{id}")
	public ResponseEntity<ApiResponse<CartItemResponse>> updateQuantity(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long id,
			@Valid @RequestBody CartItemQuantityRequest request) {
		return ResponseEntity.ok(ApiResponse.success(cartService.updateQuantity(userId, id, request.quantity())));
	}

	@Operation(summary = "장바구니 항목 단일 삭제")
	@DeleteMapping("/items/{id}")
	public ResponseEntity<Void> deleteItem(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long id) {
		cartService.deleteItem(userId, id);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "장바구니 항목 복수 삭제", description = "원자적으로 전체 성공/실패합니다. 타인 소유이거나 존재하지 않는 id가 포함되면 전체 실패합니다.")
	@DeleteMapping("/items")
	public ResponseEntity<Void> deleteItems(
			@AuthenticationPrincipal Long userId,
			@RequestParam List<Long> ids) {
		cartService.deleteItems(userId, ids);
		return ResponseEntity.noContent().build();
	}
}
