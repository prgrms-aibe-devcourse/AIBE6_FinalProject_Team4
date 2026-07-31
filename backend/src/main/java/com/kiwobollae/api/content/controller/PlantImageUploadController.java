package com.kiwobollae.api.content.controller;

import com.kiwobollae.api.content.dto.response.PlantImageUploadResponse;
import com.kiwobollae.api.content.service.PlantImageUploadService;
import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "내 식물", description = "반려 식물 등록/조회 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/plants/images")
public class PlantImageUploadController {

	private final PlantImageUploadService plantImageUploadService;

	@Operation(summary = "식물 프로필 대표 사진 업로드", description = "식물 등록/수정에 사용할 대표 사진을 S3에 업로드하고, 서버가 대신 서빙하는 경로와 해시를 반환합니다.")
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ApiResponse<PlantImageUploadResponse>> uploadImage(
			@AuthenticationPrincipal Long userId,
			@RequestParam("file") MultipartFile file) {
		PlantImageUploadResponse response = plantImageUploadService.upload(file, userId);
		return ResponseEntity.ok(ApiResponse.success(response));
	}

	// 버킷이 private이라 브라우저가 S3에 직접 접근할 수 없다 — JournalImageUploadController와 동일한 이유로
	// 이 GET은 permitAll이다(see SecurityConfig) — 파일명이 UUID라 URL 추측은 사실상 불가능하다.
	@Operation(summary = "식물 프로필 대표 사진 서빙", description = "private S3 버킷의 이미지를 서버가 대신 가져와 반환합니다.")
	@GetMapping("/{userId}/{filename}")
	public ResponseEntity<byte[]> serveImage(@PathVariable Long userId, @PathVariable String filename) {
		return plantImageUploadService.download(userId, filename);
	}
}
