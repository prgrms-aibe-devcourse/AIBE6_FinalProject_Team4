package com.kiwobollae.api.commerce.gacha.controller;

import com.kiwobollae.api.commerce.gacha.dto.GachaManualRetryResponse;
import com.kiwobollae.api.commerce.gacha.service.GachaManualReviewService;
import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 가챠", description = "관리자 전용 가챠 복구 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/admin/card/gacha")
@PreAuthorize("hasRole('ADMIN')")
public class AdminGachaController {

  private final GachaManualReviewService manualReviewService;

  @Operation(summary = "수동 확인 가챠 재시도", description = "MANUAL_REVIEW 상태의 가챠를 처리 대기 상태로 되돌립니다.")
  @PatchMapping("/draws/{drawId}/retry")
  public ResponseEntity<ApiResponse<GachaManualRetryResponse>> retry(@PathVariable Long drawId) {
    return ResponseEntity.accepted().body(ApiResponse.success(manualReviewService.retry(drawId)));
  }
}
