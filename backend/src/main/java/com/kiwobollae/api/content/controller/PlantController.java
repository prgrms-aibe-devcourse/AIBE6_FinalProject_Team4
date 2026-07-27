package com.kiwobollae.api.content.controller;

import com.kiwobollae.api.content.dto.request.PlantProfileRequest;
import com.kiwobollae.api.content.dto.request.PlantProfileUpdateRequest;
import com.kiwobollae.api.content.dto.response.PlantProfileResponse;
import com.kiwobollae.api.content.service.PlantProfileService;
import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "내 식물", description = "반려 식물 등록/조회 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/plants")
public class PlantController {

	private final PlantProfileService plantProfileService;

	@Operation(summary = "내 식물 등록", description = "별명/종/재배 시작일/대표 사진으로 새 식물 프로필을 등록합니다.")
	@PostMapping
	public ResponseEntity<ApiResponse<PlantProfileResponse>> createProfile(
			@AuthenticationPrincipal Long userId,
			@Valid @RequestBody PlantProfileRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(plantProfileService.createProfile(userId, request)));
	}

	@Operation(summary = "내 식물 목록 조회", description = "로그인한 사용자의 식물 프로필 목록을 최신순으로 조회합니다.")
	@GetMapping
	public ResponseEntity<ApiResponse<List<PlantProfileResponse>>> getMyProfiles(
			@AuthenticationPrincipal Long userId) {
		return ResponseEntity.ok(ApiResponse.success(plantProfileService.getMyProfiles(userId)));
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
