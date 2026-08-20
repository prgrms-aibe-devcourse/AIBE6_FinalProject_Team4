package com.kiwobollae.api.ai.guide;

import com.kiwobollae.api.ai.guide.dto.PlantCareGuide;
import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI 재배 가이드", description = "입력한 식물 종의 재배 가이드 제공 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/ai/plants")
public class PlantCareGuideController {

  private final PlantCareGuideService plantCareGuideService;

  // 조회처럼 보이지만 저장본이 없으면 생성이 일어나므로 GET이다 — 같은 종에 대해 몇 번 호출해도
  // 결과가 같고(멱등) 부수 효과는 캐시 채우기뿐이다.
  @Operation(
      summary = "식물 종 재배 가이드 조회",
      description =
          "입력한 종의 검증 재배 자료가 있으면 이를 근거로 가이드를 반환합니다. 저장된 가이드가 없으면 AI로 생성한 뒤 저장합니다. "
              + "검증 자료가 없는 새 종도 일반 재배 지식을 바탕으로 생성합니다. cached=false면 이번 요청에서 생성된 결과입니다.")
  @GetMapping("/care-guide")
  public ResponseEntity<ApiResponse<PlantCareGuide>> getCareGuide(
      @AuthenticationPrincipal Long userId, @RequestParam String speciesName) {
    return ResponseEntity.ok(
        ApiResponse.success(plantCareGuideService.getGuideBySpeciesName(userId, speciesName)));
  }

  @Operation(
      summary = "재배 가이드 종 이름 검색",
      description = "이미 가이드가 생성된 종 이름 중 검색어를 포함하는 이름을 반환합니다. 아직 가이드가 없는 새 종은 검색되지 않습니다.")
  @GetMapping("/care-guide/search")
  public ResponseEntity<ApiResponse<List<String>>> searchCareGuideSpecies(
      @RequestParam String query) {
    return ResponseEntity.ok(ApiResponse.success(plantCareGuideService.searchSpeciesNames(query)));
  }
}
