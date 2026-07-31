package com.kiwobollae.api.commerce.gacha.controller;

import com.kiwobollae.api.commerce.gacha.dto.GachaCardResponse;
import com.kiwobollae.api.commerce.gacha.dto.GachaCollectionResponse;
import com.kiwobollae.api.commerce.gacha.dto.GachaDrawDetailResponse;
import com.kiwobollae.api.commerce.gacha.dto.GachaDrawPageResponse;
import com.kiwobollae.api.commerce.gacha.dto.GachaPackPurchaseRequest;
import com.kiwobollae.api.commerce.gacha.dto.GachaPackPurchaseResponse;
import com.kiwobollae.api.commerce.gacha.dto.GachaRateResponse;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;
import com.kiwobollae.api.commerce.gacha.service.GachaPackPurchaseService;
import com.kiwobollae.api.commerce.gacha.service.GachaQueryService;
import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "가챠 카드", description = "트레이딩 카드 도감, 개봉 결과, 확률 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/card/gacha")
public class GachaController {

  private final GachaQueryService gachaQueryService;
  private final GachaPackPurchaseService gachaPackPurchaseService;

  @Operation(summary = "가챠 카드 공개 도감")
  @GetMapping("/catalog")
  public ResponseEntity<ApiResponse<List<GachaCardResponse>>> getCatalog(
      @RequestParam(required = false) TradingCardRarity rarity) {
    return ResponseEntity.ok(ApiResponse.success(gachaQueryService.getCatalog(rarity)));
  }

  @Operation(summary = "가챠 확률 조회")
  @GetMapping("/rates")
  public ResponseEntity<ApiResponse<GachaRateResponse>> getRates() {
    return ResponseEntity.ok(ApiResponse.success(gachaQueryService.getRates()));
  }

  @Operation(summary = "포인트로 가챠 팩 구매")
  @PostMapping("/purchases")
  public ResponseEntity<ApiResponse<GachaPackPurchaseResponse>> purchasePacks(
      @AuthenticationPrincipal Long userId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody GachaPackPurchaseRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(gachaPackPurchaseService.purchase(userId, idempotencyKey, request)));
  }

  @Operation(summary = "내 가챠 카드 도감")
  @GetMapping("/me/collection")
  public ResponseEntity<ApiResponse<List<GachaCollectionResponse>>> getCollection(
      @AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(ApiResponse.success(gachaQueryService.getCollection(userId)));
  }

  @Operation(summary = "내 가챠 개봉 이력")
  @GetMapping("/draws")
  public ResponseEntity<ApiResponse<GachaDrawPageResponse>> getHistory(
      @AuthenticationPrincipal Long userId,
      @RequestParam(required = false) Boolean viewed,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    int safeSize = Math.max(1, Math.min(size, 100));
    return ResponseEntity.ok(
        ApiResponse.success(
            gachaQueryService.getHistory(
                userId,
                viewed,
                PageRequest.of(
                    Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt")))));
  }

  @Operation(summary = "가챠 개봉 결과 조회")
  @GetMapping("/draws/{drawId}")
  public ResponseEntity<ApiResponse<GachaDrawDetailResponse>> getDraw(
      @AuthenticationPrincipal Long userId, @PathVariable Long drawId) {
    return ResponseEntity.ok(ApiResponse.success(gachaQueryService.getDraw(userId, drawId)));
  }

  @Operation(summary = "가챠 결과 확인 완료")
  @PatchMapping("/draws/{drawId}/viewed")
  public ResponseEntity<ApiResponse<GachaDrawDetailResponse>> markViewed(
      @AuthenticationPrincipal Long userId, @PathVariable Long drawId) {
    return ResponseEntity.ok(ApiResponse.success(gachaQueryService.markViewed(userId, drawId)));
  }
}
