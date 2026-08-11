package com.kiwobollae.api.journal.controller;

import com.kiwobollae.api.journal.dto.response.JournalImageUploadResponse;
import com.kiwobollae.api.journal.service.JournalImageUploadService;
import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "성장 일지", description = "식물 성장 일지 작성/조회 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/journals/images")
public class JournalImageUploadController {

	private final JournalImageUploadService journalImageUploadService;

	@Operation(summary = "성장 일지 이미지 업로드", description = "일지 작성/수정에 사용할 이미지를 S3에 업로드하고, 서버가 대신 서빙하는 경로와 해시를 반환합니다.")
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ApiResponse<JournalImageUploadResponse>> uploadImage(
			@AuthenticationPrincipal Long userId,
			@RequestParam("file") MultipartFile file) {
		JournalImageUploadResponse response = journalImageUploadService.upload(file, userId);
		return ResponseEntity.ok(ApiResponse.success(response));
	}

	// 버킷이 private이라 브라우저가 S3에 직접 접근할 수 없다 — 업로드 응답의 imageUrl이 이 엔드포인트를
	// 가리키고, 여기서 S3Client 자격증명으로 대신 가져와 스트리밍한다. <img> 태그는 인증 헤더를 못 보내므로
	// 이 GET은 permitAll이다(see SecurityConfig) — 파일명이 UUID라 URL 추측은 사실상 불가능하다.
	@Operation(summary = "성장 일지 이미지 서빙", description = "private S3 버킷의 이미지를 서버가 대신 가져와 반환합니다.")
	@GetMapping("/{userId}/{filename}")
	public ResponseEntity<byte[]> serveImage(@PathVariable Long userId, @PathVariable String filename) {
		return journalImageUploadService.download(userId, filename);
	}

	// 사진을 업로드했지만 이어지는 일지 작성/수정이 실패해 일지에 연결되지 못한 이미지를 프론트가
	// 직접 정리할 수 있도록 제공한다. delete()는 소유권이 다르면 조용히 무시하고 best-effort로 동작한다.
	@Operation(summary = "성장 일지 이미지 삭제", description = "업로드했지만 일지에 연결되지 못한 이미지를 정리합니다.")
	@DeleteMapping
	public ResponseEntity<Void> deleteImage(
			@AuthenticationPrincipal Long userId,
			@RequestParam("imageUrl") String imageUrl) {
		journalImageUploadService.delete(imageUrl, userId);
		return ResponseEntity.noContent().build();
	}
}
