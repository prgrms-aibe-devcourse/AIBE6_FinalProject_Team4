package com.kiwobollae.api.journal.controller;

import com.kiwobollae.api.journal.dto.request.PlantJournalRequest;
import com.kiwobollae.api.journal.dto.request.PlantJournalUpdateRequest;
import com.kiwobollae.api.journal.dto.response.PlantJournalCreateResponse;
import com.kiwobollae.api.journal.dto.response.PlantJournalResponse;
import com.kiwobollae.api.journal.dto.response.DailyJournalRewardStatusResponse;
import com.kiwobollae.api.journal.service.PlantJournalService;
import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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

@Tag(name = "성장 일지", description = "식물 성장 일지 작성/조회 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/journals")
public class JournalController {

	private final PlantJournalService plantJournalService;

	@Operation(summary = "성장 일지 작성", description = "선택한 식물 프로필에 이미지(1~3장)와 내용으로 일지를 작성합니다.")
	@PostMapping
	public ResponseEntity<ApiResponse<PlantJournalCreateResponse>> createJournal(
			@AuthenticationPrincipal Long userId,
			@Valid @RequestBody PlantJournalRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(plantJournalService.createJournal(userId, request)));
	}

	@Operation(summary = "성장 일지 목록 조회", description = "전체/식물 프로필별/날짜별(연·월) 필터와 페이지네이션으로 내 일지를 조회합니다.")
	@GetMapping
	public ResponseEntity<ApiResponse<Page<PlantJournalResponse>>> getJournals(
			@AuthenticationPrincipal Long userId,
			@RequestParam(required = false) Long profileId,
			@RequestParam(required = false) Integer year,
			@RequestParam(required = false) Integer month,
			@ParameterObject @PageableDefault(size = 20, sort = {"writtenDate", "id"}, direction = Sort.Direction.DESC)
			Pageable pageable) {
		return ResponseEntity.ok(ApiResponse.success(
				plantJournalService.getJournals(userId, profileId, year, month, pageable)));
	}

	@Operation(summary = "오늘 일지 작성한 프로필 목록", description = "오늘(KST) 일지를 작성한 식물 프로필 id 목록을 반환합니다.")
	@GetMapping("/written-today")
	public ResponseEntity<ApiResponse<List<Long>>> getProfileIdsWrittenToday(@AuthenticationPrincipal Long userId) {
		return ResponseEntity.ok(ApiResponse.success(plantJournalService.getProfileIdsWrittenToday(userId)));
	}

	@Operation(summary = "오늘 일지 보상 상태", description = "계정이 오늘(KST) 일지 작성 보상을 받았는지 반환합니다.")
	@GetMapping("/reward-status")
	public ResponseEntity<ApiResponse<DailyJournalRewardStatusResponse>> getDailyRewardStatus(
			@AuthenticationPrincipal Long userId) {
		return ResponseEntity.ok(ApiResponse.success(plantJournalService.getDailyRewardStatus(userId)));
	}

	@Operation(summary = "성장 일지 상세 조회", description = "본인 소유의 성장 일지 단건을 조회합니다.")
	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<PlantJournalResponse>> getJournal(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long id) {
		return ResponseEntity.ok(ApiResponse.success(plantJournalService.getJournal(userId, id)));
	}

	@Operation(summary = "성장 일지 수정", description = "내용과 이미지(1~3장 전체 교체)를 수정합니다.")
	@PatchMapping("/{id}")
	public ResponseEntity<ApiResponse<PlantJournalResponse>> updateJournal(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long id,
			@Valid @RequestBody PlantJournalUpdateRequest request) {
		return ResponseEntity.ok(ApiResponse.success(plantJournalService.updateJournal(userId, id, request)));
	}

	@Operation(summary = "성장 일지 삭제", description = "성장 일지를 삭제합니다(soft delete).")
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteJournal(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long id) {
		plantJournalService.deleteJournal(userId, id);
		return ResponseEntity.noContent().build();
	}
}
