package com.kiwobollae.api.commerce.gacha.controller;

import com.kiwobollae.api.commerce.gacha.dto.GachaQaBatchDrawResponse;
import com.kiwobollae.api.commerce.gacha.dto.GachaQaDrawRequest;
import com.kiwobollae.api.commerce.gacha.dto.GachaQaDrawResponse;
import com.kiwobollae.api.commerce.gacha.service.GachaQaReservationService;
import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@Profile("local")
@ConditionalOnProperty(prefix = "app.gacha.qa", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/card/gacha/qa")
public class GachaQaController {

  private final GachaQaReservationService reservationService;

  @PostMapping("/draws")
  public ResponseEntity<ApiResponse<GachaQaDrawResponse>> createDraw(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody GachaQaDrawRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(reservationService.reserve(userId, request.clientKey())));
  }

  @PostMapping("/draws/100")
  public ResponseEntity<ApiResponse<GachaQaBatchDrawResponse>> createOneHundredDraws(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody GachaQaDrawRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(reservationService.reserveBatch(userId, request.clientKey(), 100)));
  }
}
