package com.kiwobollae.api.report.controller;

import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import com.kiwobollae.api.report.dto.request.ReportRequest;
import com.kiwobollae.api.report.dto.response.ReportResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "신고", description = "부적절한 콘텐츠 신고 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/reports")
public class ReportController {

	private final ReportService reportService;

	@Operation(summary = "신고 등록", description = "대상(성장 일지)과 사유로 신고를 등록합니다.")
	@PostMapping
	public ResponseEntity<ApiResponse<ReportResponse>> createReport(
			@AuthenticationPrincipal Long userId,
			@Valid @RequestBody ReportRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(reportService.createReport(userId, request)));
	}

	@Operation(summary = "내 신고 목록 조회", description = "내가 등록한 신고를 최신순으로 조회합니다.")
	@GetMapping
	public ResponseEntity<ApiResponse<Page<ReportResponse>>> getMyReports(
			@AuthenticationPrincipal Long userId,
			@ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
			Pageable pageable) {
		return ResponseEntity.ok(ApiResponse.success(reportService.getMyReports(userId, pageable)));
	}

	@Operation(summary = "내 신고 상세 조회", description = "본인이 등록한 신고 단건을 조회합니다.")
	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<ReportResponse>> getReport(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(reportService.getReport(userId, id)));
	}
}
