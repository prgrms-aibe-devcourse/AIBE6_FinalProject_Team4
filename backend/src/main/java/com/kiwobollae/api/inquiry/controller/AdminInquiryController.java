package com.kiwobollae.api.inquiry.controller;

import com.kiwobollae.api.global.common.AdminPageableSupport;
import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import com.kiwobollae.api.inquiry.dto.request.InquiryAnswerRequest;
import com.kiwobollae.api.inquiry.dto.response.InquiryResponse;
import com.kiwobollae.api.inquiry.entity.enums.InquiryStatus;
import com.kiwobollae.api.inquiry.service.InquiryService;
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

@Tag(name = "1:1 문의 - 관리자", description = "문의 관리자 처리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/admin/inquiries")
@PreAuthorize("hasRole('ADMIN')")
public class AdminInquiryController {

	private final InquiryService inquiryService;

	@Operation(summary = "문의 전체 목록 조회", description = "상태(선택)로 필터링해 전체 문의를 조회합니다.")
	@GetMapping
	public ResponseEntity<ApiResponse<Page<InquiryResponse>>> getInquiries(
			@RequestParam(required = false) InquiryStatus status,
			@RequestParam(required = false) Integer size,
			@ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
			Pageable pageable) {
		return ResponseEntity.ok(ApiResponse.success(inquiryService.getInquiriesForAdmin(
				status, AdminPageableSupport.withUncappedSize(size, pageable))));
	}

	@Operation(summary = "문의 답변", description = "문의에 답변을 등록합니다. 답변과 동시에 종료 처리됩니다.")
	@PatchMapping("/{id}/answer")
	public ResponseEntity<ApiResponse<InquiryResponse>> answerInquiry(
			@AuthenticationPrincipal Long adminId,
			@PathVariable Long id,
			@Valid @RequestBody InquiryAnswerRequest request) {
		return ResponseEntity.ok(ApiResponse.success(inquiryService.answerInquiry(adminId, id, request)));
	}
}
