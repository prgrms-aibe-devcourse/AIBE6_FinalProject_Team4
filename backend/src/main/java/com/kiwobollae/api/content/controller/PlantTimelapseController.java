package com.kiwobollae.api.content.controller;

import com.kiwobollae.api.content.dto.response.PlantTimelapseResponse;
import com.kiwobollae.api.content.service.PlantTimelapseService;
import com.kiwobollae.api.content.service.PlantTimelapseVideoStorageService;
import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Tag(name = "식물 타임랩스", description = "일지 대표이미지를 모아 타임랩스 영상을 만드는 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/plants")
public class PlantTimelapseController {

	private final PlantTimelapseService plantTimelapseService;
	private final PlantTimelapseVideoStorageService videoStorageService;

	@Operation(summary = "타임랩스 생성 요청", description = "재배가 완료된 식물의 대표이미지를 모아 타임랩스 영상 생성을 요청합니다.")
	@PostMapping("/{profileId}/timelapse")
	public ResponseEntity<ApiResponse<PlantTimelapseResponse>> requestTimelapse(
			@AuthenticationPrincipal Long userId, @PathVariable Long profileId) {
		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(ApiResponse.success(plantTimelapseService.requestTimelapse(userId, profileId)));
	}

	@Operation(summary = "타임랩스 상태/결과 조회", description = "타임랩스 생성 상태 또는 완성된 영상 URL을 조회합니다.")
	@GetMapping("/{profileId}/timelapse")
	public ResponseEntity<ApiResponse<PlantTimelapseResponse>> getTimelapse(
			@AuthenticationPrincipal Long userId, @PathVariable Long profileId) {
		return ResponseEntity.ok(ApiResponse.success(plantTimelapseService.getTimelapse(userId, profileId)));
	}

	@Operation(summary = "타임랩스 영상 서빙", description = "private S3에 저장된 타임랩스 영상을 프록시로 서빙합니다. Range 요청 시 해당 구간만 반환합니다.")
	@GetMapping("/timelapse-videos/{userId}/{filename}")
	public ResponseEntity<StreamingResponseBody> serveVideo(@PathVariable Long userId, @PathVariable String filename,
			@RequestHeader(value = HttpHeaders.RANGE, required = false) String range) {
		return videoStorageService.download(userId, filename, range);
	}
}
