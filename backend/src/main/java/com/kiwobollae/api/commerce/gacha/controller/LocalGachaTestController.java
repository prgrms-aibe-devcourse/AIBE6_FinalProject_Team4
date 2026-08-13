package com.kiwobollae.api.commerce.gacha.controller;

import com.kiwobollae.api.commerce.gacha.dto.GachaTestCardGrantRequest;
import com.kiwobollae.api.commerce.gacha.dto.GachaTestCardGrantResponse;
import com.kiwobollae.api.commerce.gacha.service.LocalGachaTestCardGrantService;
import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "로컬 가챠 테스트", description = "로컬 환경 전용 카드 지급 API")
@RestController
@Profile("local")
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/card/gacha/me/test-cards")
public class LocalGachaTestController {

  private final LocalGachaTestCardGrantService testCardGrantService;

  @Operation(summary = "로컬 테스트용 하이퍼·골든 카드 지급")
  @PostMapping
  public ResponseEntity<ApiResponse<GachaTestCardGrantResponse>> grant(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody GachaTestCardGrantRequest request) {
    return ResponseEntity.ok(ApiResponse.success(testCardGrantService.grant(userId, request)));
  }
}
