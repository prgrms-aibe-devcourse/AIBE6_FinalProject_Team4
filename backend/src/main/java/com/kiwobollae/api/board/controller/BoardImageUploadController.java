package com.kiwobollae.api.board.controller;

import com.kiwobollae.api.board.dto.response.BoardImageUploadResponse;
import com.kiwobollae.api.board.service.BoardImageUploadService;
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

@Tag(name = "게시판", description = "커뮤니티 게시판(공지사항/자유게시판/식물 Q&A) 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/board/images")
public class BoardImageUploadController {

	private final BoardImageUploadService boardImageUploadService;

	@Operation(summary = "게시글 이미지 업로드", description = "게시글 작성/수정에 사용할 이미지를 S3에 업로드하고, 서버가 대신 서빙하는 경로를 반환합니다.")
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ApiResponse<BoardImageUploadResponse>> uploadImage(
			@AuthenticationPrincipal Long userId,
			@RequestParam("file") MultipartFile file) {
		BoardImageUploadResponse response = boardImageUploadService.upload(file, userId);
		return ResponseEntity.ok(ApiResponse.success(response));
	}

	// 버킷이 private이라 <img> 태그가 S3에 직접 접근할 수 없다 — 이 GET은 permitAll이다(SecurityConfig의
	// "/board/**" GET 공개 규칙에 포함). 파일명이 UUID라 URL 추측은 사실상 불가능하다.
	@Operation(summary = "게시글 이미지 서빙", description = "private S3 버킷의 이미지를 서버가 대신 가져와 반환합니다.")
	@GetMapping("/{userId}/{filename}")
	public ResponseEntity<byte[]> serveImage(@PathVariable Long userId, @PathVariable String filename) {
		return boardImageUploadService.download(userId, filename);
	}

	// 업로드했지만 이어지는 게시글 작성/수정이 실패해 게시글에 연결되지 못한 이미지를 프론트가
	// 직접 정리할 수 있도록 제공한다. delete()는 소유권이 다르면 조용히 무시하고 best-effort로 동작한다.
	@Operation(summary = "게시글 이미지 삭제", description = "업로드했지만 게시글에 연결되지 못한 이미지를 정리합니다.")
	@DeleteMapping
	public ResponseEntity<Void> deleteImage(
			@AuthenticationPrincipal Long userId,
			@RequestParam("imageUrl") String imageUrl) {
		boardImageUploadService.delete(imageUrl, userId);
		return ResponseEntity.noContent().build();
	}
}
