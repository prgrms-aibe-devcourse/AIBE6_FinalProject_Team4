package com.kiwobollae.api.report.controller;

import com.kiwobollae.api.global.common.AdminPageableSupport;
import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import com.kiwobollae.api.report.dto.request.ReportActionRequest;
import com.kiwobollae.api.report.dto.response.ReportResponse;
import com.kiwobollae.api.report.entity.enums.ReportStatus;
import com.kiwobollae.api.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "신고 - 관리자", description = "신고 관리자 처리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReportController {

	private final ReportService reportService;

	@Operation(summary = "신고 전체 목록 조회", description = "상태(선택), 신고 대상 유저(선택)로 필터링해 전체 신고를 조회합니다.")
	@GetMapping
	public ResponseEntity<ApiResponse<Page<ReportResponse>>> getReports(
			@RequestParam(required = false) ReportStatus status,
			@RequestParam(required = false) Long targetUserId,
			@RequestParam(required = false) Integer size,
			@ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
			Pageable pageable) {
		return ResponseEntity.ok(ApiResponse.success(reportService.getReportsForAdmin(
				status, targetUserId, AdminPageableSupport.withUncappedSize(size, pageable))));
	}

	@Operation(summary = "신고 완료 처리", description = "신고를 검토해 조치를 완료 처리합니다.")
	@PatchMapping("/{id}/complete")
	public ResponseEntity<ApiResponse<ReportResponse>> completeReport(
			@AuthenticationPrincipal Long adminId,
			@PathVariable Long id,
			@Valid @RequestBody ReportActionRequest request) {
		return ResponseEntity.ok(ApiResponse.success(reportService.completeReport(adminId, id, request)));
	}

	@Operation(summary = "신고 반려 처리", description = "신고를 검토해 반려 처리합니다.")
	@PatchMapping("/{id}/reject")
	public ResponseEntity<ApiResponse<ReportResponse>> rejectReport(
			@AuthenticationPrincipal Long adminId,
			@PathVariable Long id,
			@Valid @RequestBody ReportActionRequest request) {
		return ResponseEntity.ok(ApiResponse.success(reportService.rejectReport(adminId, id, request)));
	}
}
