package com.kiwobollae.api.inquiry.controller;

import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import com.kiwobollae.api.inquiry.dto.request.InquiryRequest;
import com.kiwobollae.api.inquiry.dto.response.InquiryResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "1:1 문의", description = "고객 문의 등록/조회 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/inquiries")
public class InquiryController {

	private final InquiryService inquiryService;

	@Operation(summary = "문의 등록", description = "분류/제목/내용으로 1:1 문의를 등록합니다.")
	@PostMapping
	public ResponseEntity<ApiResponse<InquiryResponse>> createInquiry(
			@AuthenticationPrincipal Long userId,
			@Valid @RequestBody InquiryRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(inquiryService.createInquiry(userId, request)));
	}

	@Operation(summary = "내 문의 목록 조회", description = "내가 등록한 문의를 최신순으로 조회합니다.")
	@GetMapping
	public ResponseEntity<ApiResponse<Page<InquiryResponse>>> getMyInquiries(
			@AuthenticationPrincipal Long userId,
			@ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
			Pageable pageable) {
		return ResponseEntity.ok(ApiResponse.success(inquiryService.getMyInquiries(userId, pageable)));
	}

	@Operation(summary = "내 문의 상세 조회", description = "본인이 등록한 문의 단건을 조회합니다.")
	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<InquiryResponse>> getInquiry(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(inquiryService.getInquiry(userId, id)));
	}
}
