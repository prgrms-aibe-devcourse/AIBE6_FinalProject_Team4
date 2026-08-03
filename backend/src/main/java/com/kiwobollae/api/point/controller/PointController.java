package com.kiwobollae.api.point.controller;

import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import com.kiwobollae.api.point.dto.response.PointActivityResponse;
import com.kiwobollae.api.point.dto.response.PointTransactionResponse;
import com.kiwobollae.api.point.dto.response.WalletResponse;
import com.kiwobollae.api.point.entity.enums.PointRefType;
import com.kiwobollae.api.point.entity.enums.PointTxType;
import com.kiwobollae.api.point.service.PointTransactionService;
import com.kiwobollae.api.point.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "포인트", description = "포인트 잔액/내역 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/points")
public class PointController {

	private final WalletService walletService;
	private final PointTransactionService pointTransactionService;

	@Operation(summary = "포인트 잔액 조회",
			description = "내 지갑의 합산 잔액과 유상/무상 잔액을 조회합니다. [POINT-01]")
	@GetMapping("/wallet")
	public ResponseEntity<ApiResponse<WalletResponse>> getWallet(@AuthenticationPrincipal Long userId) {
		return ResponseEntity.ok(ApiResponse.success(walletService.getWallet(userId)));
	}

	@Operation(summary = "포인트 거래 내역 조회",
			description = "내 지갑의 포인트 거래 내역을 조회합니다. 유형(type)/기간(from,to) 필터와 페이지네이션 지원. [POINT-02]")
	@GetMapping("/transactions")
	public ResponseEntity<ApiResponse<Page<PointTransactionResponse>>> getTransactions(
			@AuthenticationPrincipal Long userId,
			@RequestParam(required = false) PointTxType type,
			@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime from,
			@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime to,
			@ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return ResponseEntity.ok(ApiResponse.success(
				pointTransactionService.getTransactions(userId, type, from, to, pageable)));
	}

	@Operation(summary = "사용자 포인트 활동 내역 조회",
			description = "같은 거래의 유상·무상 원장을 한 건으로 묶어 조회합니다. 유형·출처·기간 필터와 페이지네이션을 지원합니다.")
	@GetMapping("/activities")
	public ResponseEntity<ApiResponse<Page<PointActivityResponse>>> getActivities(
			@AuthenticationPrincipal Long userId,
			@RequestParam(required = false) PointTxType type,
			@RequestParam(required = false) PointRefType refType,
			@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime from,
			@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime to,
			@ParameterObject @PageableDefault(size = 20) Pageable pageable
	) {
		return ResponseEntity.ok(ApiResponse.success(
				pointTransactionService.getActivities(userId, type, refType, from, to, pageable)));
	}
}
