package com.kiwobollae.api.admin.controller;

import com.kiwobollae.api.admin.service.ExchangeManagementService;
import com.kiwobollae.api.admin.service.OrderManagementService;
import com.kiwobollae.api.admin.service.PlantSpeciesManagementService;
import com.kiwobollae.api.board.service.BoardCommentService;
import com.kiwobollae.api.board.service.BoardPostService;
import com.kiwobollae.api.commerce.dto.request.ExchangeCancelRequest;
import com.kiwobollae.api.commerce.dto.request.OrderCancelRequest;
import com.kiwobollae.api.commerce.dto.response.ExchangeOrderResponse;
import com.kiwobollae.api.commerce.dto.response.OrderDetailResponse;
import com.kiwobollae.api.commerce.dto.response.OrderResponse;
import com.kiwobollae.api.commerce.entity.enums.DeliveryStatus;
import com.kiwobollae.api.commerce.entity.enums.ExchangeStatus;
import com.kiwobollae.api.commerce.entity.enums.OrderStatus;
import com.kiwobollae.api.species.dto.request.PlantSpeciesRequest;
import com.kiwobollae.api.species.dto.response.PlantSpeciesResponse;
import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Shell for admin-only endpoints. Admin business logic mostly lives in a domain-specific
 * admin service (e.g. {@link ExchangeManagementService}); this controller is the single
 * entry point the team expands as more admin-facing domains (exchange, stock, etc.) come in.
 */
@Tag(name = "관리자", description = "관리자 전용 API (주문/교환/상품/신고 관리 등)")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

	private final ExchangeManagementService exchangeManagementService;
	private final OrderManagementService orderManagementService;
	private final PlantSpeciesManagementService plantSpeciesManagementService;
	private final BoardPostService boardPostService;
	private final BoardCommentService boardCommentService;

	@Operation(summary = "식물 종 추가", description = "새로운 식물 종을 등록합니다.")
	@PostMapping("/plants/species")
	public ResponseEntity<ApiResponse<PlantSpeciesResponse>> createSpecies(
			@Valid @RequestBody PlantSpeciesRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(plantSpeciesManagementService.createSpecies(request)));
	}

	@Operation(summary = "식물 종 수정", description = "기존 식물 종의 이름/카테고리/관리 가이드를 수정합니다.")
	@PatchMapping("/plants/species/{id}")
	public ResponseEntity<ApiResponse<PlantSpeciesResponse>> updateSpecies(
			@PathVariable Long id,
			@Valid @RequestBody PlantSpeciesRequest request) {
		return ResponseEntity.ok(ApiResponse.success(plantSpeciesManagementService.updateSpecies(id, request)));
	}

	@Operation(summary = "주문 전체 목록 조회", description = "주문 상태·배송 상태·유저·기간(선택)으로 필터링해 전체 주문을 조회합니다. 각 주문의 구매 상품 목록을 함께 반환합니다.")
	@GetMapping("/order")
	public ResponseEntity<ApiResponse<Page<OrderDetailResponse>>> getOrders(
			@RequestParam(required = false) OrderStatus status,
			@RequestParam(required = false) DeliveryStatus deliveryStatus,
			@RequestParam(required = false) Long userId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
			@ParameterObject @PageableDefault(size = 20, sort = "orderedAt", direction = Sort.Direction.DESC)
			Pageable pageable) {
		return ResponseEntity.ok(ApiResponse.success(
				orderManagementService.getOrdersForAdmin(status, deliveryStatus, userId, from, to, pageable)
		));
	}

	@Operation(summary = "주문 상세 조회", description = "주문자 정보, 배송지, 상품 목록, 포인트 사용 내역을 포함한 주문 상세를 조회합니다.")
	@GetMapping("/order/{id}")
	public ResponseEntity<ApiResponse<OrderDetailResponse>> getOrder(@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(orderManagementService.getOrderForAdmin(id)));
	}

	@Operation(summary = "주문 배송 시작 처리", description = "준비 중인 주문을 배송 중 상태로 전환합니다.")
	@PatchMapping("/order/{id}/ship")
	public ResponseEntity<ApiResponse<OrderResponse>> shipOrder(@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(orderManagementService.shipOrder(id)));
	}

	@Operation(summary = "주문 배송 완료 처리", description = "배송 중인 주문을 배송 완료 상태로 전환합니다.")
	@PatchMapping("/order/{id}/deliver")
	public ResponseEntity<ApiResponse<OrderResponse>> deliverOrder(@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(orderManagementService.deliverOrder(id)));
	}

	@Operation(summary = "주문 취소", description = "배송 준비 중인 주문을 관리자가 취소합니다. 재고·포인트가 환급되고, 취소 사유가 고객 알림에 표시됩니다.")
	@PatchMapping("/order/{id}/cancel")
	public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
			@PathVariable Long id,
			@Valid @RequestBody(required = false) OrderCancelRequest request) {
		String reason = request != null ? request.reason() : null;
		return ResponseEntity.ok(ApiResponse.success(orderManagementService.adminCancelOrder(id, reason)));
	}

	@Operation(summary = "교환 신청 전체 목록 조회", description = "상태(선택)로 필터링해 전체 교환 신청을 조회합니다.")
	@GetMapping("/exchanges")
	public ResponseEntity<ApiResponse<Page<ExchangeOrderResponse>>> getExchanges(
			@RequestParam(required = false) ExchangeStatus status,
			@ParameterObject @PageableDefault(size = 20, sort = "requestedAt", direction = Sort.Direction.DESC)
			Pageable pageable) {
		return ResponseEntity.ok(ApiResponse.success(exchangeManagementService.getExchangesForAdmin(status, pageable)));
	}

	@Operation(summary = "교환 준비 처리", description = "접수된 교환 신청을 준비 중 상태로 전환합니다.")
	@PatchMapping("/exchanges/{id}/prepare")
	public ResponseEntity<ApiResponse<ExchangeOrderResponse>> prepareExchange(@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(exchangeManagementService.prepareExchange(id)));
	}

	@Operation(summary = "교환 배송 시작 처리", description = "준비 중인 교환 신청을 배송 중 상태로 전환합니다.")
	@PatchMapping("/exchanges/{id}/ship")
	public ResponseEntity<ApiResponse<ExchangeOrderResponse>> shipExchange(@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(exchangeManagementService.shipExchange(id)));
	}

	@Operation(summary = "교환 배송 완료 처리", description = "배송 중인 교환 신청을 배송 완료 상태로 전환합니다.")
	@PatchMapping("/exchanges/{id}/deliver")
	public ResponseEntity<ApiResponse<ExchangeOrderResponse>> deliverExchange(@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(exchangeManagementService.deliverExchange(id)));
	}

	@Operation(summary = "교환 신청 취소", description = "접수 대기 중인 교환 신청을 관리자가 취소합니다. 쿠폰·재고가 환급됩니다.")
	@PatchMapping("/exchanges/{id}/cancel")
	public ResponseEntity<ApiResponse<ExchangeOrderResponse>> cancelExchange(
			@PathVariable Long id,
			@Valid @RequestBody ExchangeCancelRequest request) {
		return ResponseEntity.ok(ApiResponse.success(
				exchangeManagementService.adminCancelExchange(id, request.reason())
		));
	}

	@Operation(summary = "게시글 숨김 처리", description = "부적절한 게시글을 관리자가 숨깁니다. 물리 삭제는 하지 않습니다.")
	@PatchMapping("/board/posts/{id}/hide")
	public ResponseEntity<Void> hideBoardPost(@PathVariable Long id) {
		boardPostService.adminHidePost(id);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "댓글 숨김 처리", description = "부적절한 댓글을 관리자가 숨깁니다. 물리 삭제는 하지 않습니다.")
	@PatchMapping("/board/comments/{id}/hide")
	public ResponseEntity<Void> hideBoardComment(@PathVariable Long id) {
		boardCommentService.adminHideComment(id);
		return ResponseEntity.noContent().build();
	}
}
