package com.kiwobollae.api.point.controller;

import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import com.kiwobollae.api.point.dto.request.AdminPointAdjustmentRequest;
import com.kiwobollae.api.point.dto.request.AdminPointAdjustmentDirection;
import com.kiwobollae.api.point.dto.response.AdminPointAdjustmentHistoryResponse;
import com.kiwobollae.api.point.dto.response.AdminPointAdjustmentResponse;
import com.kiwobollae.api.point.dto.response.WalletResponse;
import com.kiwobollae.api.point.entity.enums.CurrencyType;
import com.kiwobollae.api.point.service.AdminPointAdjustmentService;
import com.kiwobollae.api.point.service.AdminPointAdjustmentHistoryService;
import com.kiwobollae.api.point.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 포인트", description = "관리자 전용 포인트 조정 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/admin/points")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPointController {

	private final AdminPointAdjustmentService adminPointAdjustmentService;
	private final AdminPointAdjustmentHistoryService adminPointAdjustmentHistoryService;
	private final WalletService walletService;

	@Operation(summary = "관리자 회원 포인트 잔액 조회", description = "선택한 회원의 유상·무상·전체 포인트 잔액을 조회합니다.")
	@GetMapping("/user/{userId}/wallet")
	public ResponseEntity<ApiResponse<WalletResponse>> getWallet(@PathVariable Long userId) {
		return ResponseEntity.ok(ApiResponse.success(walletService.getWallet(userId)));
	}

	@Operation(summary = "관리자 포인트 조정 내역 조회", description = "전체 관리자 포인트 조정 원장을 최신순으로 조회합니다.")
	@GetMapping("/adjustments")
	public ResponseEntity<ApiResponse<Page<AdminPointAdjustmentHistoryResponse>>> getAdjustments(
			@RequestParam(required = false) Long userId,
			@RequestParam(required = false) CurrencyType currencyType,
			@RequestParam(required = false) AdminPointAdjustmentDirection direction,
			@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime from,
			@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime to,
			@ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
			Pageable pageable
	) {
		return ResponseEntity.ok(ApiResponse.success(
				adminPointAdjustmentHistoryService.getAdjustments(
						userId, currencyType, direction, from, to, pageable
				)
		));
	}

	@Operation(
			summary = "관리자 포인트 수동 조정",
			description = "특정 사용자의 유상/무상 포인트를 지급하거나 차감하고 ADMIN_ADJUST 원장을 기록합니다. [POINT-09]"
	)
	@PostMapping("/adjust")
	public ResponseEntity<ApiResponse<AdminPointAdjustmentResponse>> adjustPoint(
			@AuthenticationPrincipal Long adminUserId,
			@RequestHeader("Idempotency-Key") String idempotencyKey,
			@Valid @RequestBody AdminPointAdjustmentRequest request
	) {
		return ResponseEntity.ok(ApiResponse.success(
				adminPointAdjustmentService.adjust(adminUserId, idempotencyKey, request)
		));
	}
}
