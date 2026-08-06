package com.kiwobollae.api.ai.guide;

import com.kiwobollae.api.ai.guide.dto.PlantCareGuide;
import com.kiwobollae.api.ai.guide.dto.PlantCareGuideInvalidation;
import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 생성된 재배 가이드 내용이 부정확할 때 쓰는 운영 도구.
 *
 * <p>가이드는 종당 한 번 생성해 계속 재사용하므로, 한 번 잘못 생성되면 그대로 굳는다. 스키마 버전을 올리면 전체가 무효화되지만 그건 종 하나를 고치려고 쓰기엔 과하다.
 * 그래서 종 단위로만 지우거나 다시 뽑을 수 있게 한다.
 */
@Tag(name = "관리자 AI 재배 가이드", description = "종별 재배 가이드 캐시 무효화·재생성 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/admin/ai/plant-guides")
@PreAuthorize("hasRole('ADMIN')")
public class PlantCareGuideAdminController {

  private final PlantCareGuideService plantCareGuideService;

  @Operation(
      summary = "종별 재배 가이드 캐시 삭제",
      description =
          "해당 종의 저장된 가이드를 모두 지웁니다. AI를 호출하지 않으며, 다음 사용자 요청에서 다시 생성됩니다. "
              + "deletedCount=0이면 지울 저장본이 없었다는 뜻입니다.")
  @DeleteMapping("/species/{speciesId}")
  public ResponseEntity<ApiResponse<PlantCareGuideInvalidation>> invalidate(
      @PathVariable Long speciesId) {
    return ResponseEntity.ok(
        ApiResponse.success(plantCareGuideService.invalidateBySpeciesId(speciesId)));
  }

  @Operation(
      summary = "종별 재배 가이드 강제 재생성",
      description = "해당 종의 저장본을 지우고 즉시 AI로 새 가이드를 생성해 저장합니다. 실제 AI 호출이므로 일반 요청과 동일하게 호출 제한을 소모합니다.")
  @PostMapping("/species/{speciesId}/regenerate")
  public ResponseEntity<ApiResponse<PlantCareGuide>> regenerate(
      @AuthenticationPrincipal Long adminUserId, @PathVariable Long speciesId) {
    return ResponseEntity.ok(
        ApiResponse.success(plantCareGuideService.regenerateBySpeciesId(adminUserId, speciesId)));
  }
}
