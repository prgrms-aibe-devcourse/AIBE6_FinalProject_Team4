package com.kiwobollae.api.ai.analysis;

import com.kiwobollae.api.ai.analysis.dto.JournalImageAnalysisRequest;
import com.kiwobollae.api.ai.analysis.dto.JournalImageAnalysisResponse;
import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI 성장일지 사진 분석", description = "저장된 성장일지 사진의 AI 관찰 결과 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/ai/journals")
public class JournalImageAnalysisController {

  private final JournalImageAnalysisService analysisService;

  @Operation(
      summary = "저장된 사진 분석",
      description =
          "일지에 저장된 사진만 분석하며 동일한 사진 해시는 완료 결과를 재사용합니다. 응답의 grounding으로 공식 재배 근거 사용 여부, 적용 범위, 기준 종명과 출처를 확인할 수 있습니다.")
  @PostMapping("/{journalId}/image-analysis")
  public ResponseEntity<ApiResponse<JournalImageAnalysisResponse>> analyze(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long journalId,
      @Valid @RequestBody JournalImageAnalysisRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(analysisService.analyze(userId, journalId, request.imageHash())));
  }

  @Operation(
      summary = "저장된 사진 분석 결과 목록",
      description = "현재 일지에 연결된 사진들의 완료된 분석 결과와 생성 당시의 공식 재배 근거 상태·적용 범위·기준 종명·출처를 반환합니다.")
  @GetMapping("/{journalId}/image-analysis")
  public ResponseEntity<ApiResponse<List<JournalImageAnalysisResponse>>> getCompleted(
      @AuthenticationPrincipal Long userId, @PathVariable Long journalId) {
    return ResponseEntity.ok(ApiResponse.success(analysisService.getCompleted(userId, journalId)));
  }
}
