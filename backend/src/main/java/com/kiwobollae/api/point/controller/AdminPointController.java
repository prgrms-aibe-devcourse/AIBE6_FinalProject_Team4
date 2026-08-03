package com.kiwobollae.api.point.controller;

import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import com.kiwobollae.api.point.dto.request.AdminPointAdjustmentRequest;
import com.kiwobollae.api.point.dto.response.AdminPointAdjustmentResponse;
import com.kiwobollae.api.point.service.AdminPointAdjustmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 포인트", description = "관리자 전용 포인트 조정 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/admin/point")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPointController {

	private final AdminPointAdjustmentService adminPointAdjustmentService;

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
