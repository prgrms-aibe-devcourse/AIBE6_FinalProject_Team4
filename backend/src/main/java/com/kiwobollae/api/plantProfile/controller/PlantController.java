package com.kiwobollae.api.plantProfile.controller;

import com.kiwobollae.api.plantProfile.dto.request.PlantProfileRequest;
import com.kiwobollae.api.plantProfile.dto.request.PlantProfileUpdateRequest;
import com.kiwobollae.api.plantProfile.dto.response.PlantProfileResponse;
import com.kiwobollae.api.plantProfile.entity.enums.PlantStatus;
import com.kiwobollae.api.plantProfile.service.PlantProfileService;
import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "내 식물", description = "반려 식물 등록/조회 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/plants")
public class PlantController {

	// 프레임워크 기본 상한(2000)은 개인 식물 목록치고 과도하다 — 화면에서 쓰는 값(최대 100, journals류의
	// "사실상 전체" 조회 포함)보다만 넉넉하게 잡아, 그 이상은 오용으로 보고 거부한다.
	private static final int MAX_PAGE_SIZE = 100;

	private final PlantProfileService plantProfileService;

	@Operation(summary = "내 식물 등록", description = "별명/종/재배 시작일/대표 사진으로 새 식물 프로필을 등록합니다.")
	@PostMapping
	public ResponseEntity<ApiResponse<PlantProfileResponse>> createProfile(
			@AuthenticationPrincipal Long userId,
			@Valid @RequestBody PlantProfileRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(plantProfileService.createProfile(userId, request)));
	}

	@Operation(summary = "내 식물 목록 조회", description = "로그인한 사용자의 식물 프로필 목록을 status로 필터링하여 페이징 조회합니다.")
	@GetMapping
	public ResponseEntity<ApiResponse<Page<PlantProfileResponse>>> getMyProfiles(
			@AuthenticationPrincipal Long userId,
			@RequestParam(required = false) PlantStatus status,
			@ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
			Pageable pageable) {
		if (pageable.getPageSize() > MAX_PAGE_SIZE) {
			throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "size는 최대 " + MAX_PAGE_SIZE + "까지 가능합니다.");
		}
		return ResponseEntity.ok(ApiResponse.success(plantProfileService.getMyProfiles(userId, status, pageable)));
	}

	@Operation(summary = "내 식물 상세 조회", description = "본인 소유의 식물 프로필 단건을 조회합니다.")
	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<PlantProfileResponse>> getProfile(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(plantProfileService.getProfile(userId, id)));
	}

	@Operation(summary = "내 식물 수정", description = "별명/대표 사진/상태를 수정합니다.")
	@PatchMapping("/{id}")
	public ResponseEntity<ApiResponse<PlantProfileResponse>> updateProfile(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long id,
			@Valid @RequestBody PlantProfileUpdateRequest request) {
		return ResponseEntity.ok(ApiResponse.success(plantProfileService.updateProfile(userId, id, request)));
	}

	@Operation(summary = "내 식물 삭제", description = "식물 프로필과 연결된 일지·이미지를 함께 삭제합니다.")
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteProfile(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long id) {
		plantProfileService.deleteProfile(userId, id);
		return ResponseEntity.noContent().build();
	}
}
