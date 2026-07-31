package com.kiwobollae.api.commerce.controller;

import com.kiwobollae.api.commerce.dto.request.OrderCreateRequest;
import com.kiwobollae.api.commerce.dto.response.OrderDetailResponse;
import com.kiwobollae.api.commerce.dto.response.OrderResponse;
import com.kiwobollae.api.commerce.service.OrderService;
import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "주문", description = "상품 주문 및 주문 내역 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/order")
public class OrderController {

	// 클라이언트가 ?size=로 과도한 값을 보내 대량 조회를 유발하지 않도록 이 엔드포인트에서만 상한을 둔다.
	private static final int MAX_PAGE_SIZE = 100;

	private final OrderService orderService;

	@Operation(
			summary = "포인트 주문 생성",
			description = "장바구니 항목을 골라 포인트로 결제합니다. 재고 조건부 차감→포인트 차감→스냅샷 생성을 단일 트랜잭션으로 처리하며 멱등키가 필수입니다."
	)
	@PostMapping
	public ResponseEntity<ApiResponse<OrderDetailResponse>> createOrder(
			@AuthenticationPrincipal Long userId,
			@RequestHeader("Idempotency-Key") String idempotencyKey,
			@Valid @RequestBody OrderCreateRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(orderService.createOrder(userId, idempotencyKey, request)));
	}

	@Operation(summary = "내 주문 목록 조회", description = "cancellable/confirmable 플래그를 포함해 반환합니다.")
	@GetMapping
	public ResponseEntity<ApiResponse<Page<OrderResponse>>> getOrders(
			@AuthenticationPrincipal Long userId,
			@ParameterObject @PageableDefault(size = 20, sort = {"orderedAt", "id"}, direction = Sort.Direction.DESC)
			Pageable pageable
	) {
		return ResponseEntity.ok(ApiResponse.success(orderService.getOrders(userId, boundPageSize(pageable))));
	}

	private Pageable boundPageSize(Pageable pageable) {
		if (pageable.getPageSize() <= MAX_PAGE_SIZE) {
			return pageable;
		}
		return PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
	}

	@Operation(summary = "주문 상세 조회", description = "소유권을 검증하고 스냅샷 기준으로 표시합니다.")
	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<OrderDetailResponse>> getOrder(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long id
	) {
		return ResponseEntity.ok(ApiResponse.success(orderService.getOrder(userId, id)));
	}

	@Operation(summary = "주문 취소", description = "PAID∧PREPARING 상태에서만 허용합니다. 재고와 포인트를 원복합니다.")
	@PostMapping("/{id}/cancel")
	public ResponseEntity<Void> cancelOrder(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long id
	) {
		orderService.cancelOrder(userId, id);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "구매 확정", description = "DELIVERED∧PAID 상태에서만 허용합니다. 확정 후에는 취소할 수 없습니다.")
	@PostMapping("/{id}/confirm")
	public ResponseEntity<Void> confirmOrder(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long id
	) {
		orderService.confirmOrder(userId, id);
		return ResponseEntity.noContent().build();
	}
}
