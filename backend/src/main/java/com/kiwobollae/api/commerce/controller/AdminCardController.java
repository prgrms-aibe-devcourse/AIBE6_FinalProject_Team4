package com.kiwobollae.api.commerce.controller;

import com.kiwobollae.api.commerce.dto.request.AdminCardStatusRequest;
import com.kiwobollae.api.commerce.dto.request.CardRequest;
import com.kiwobollae.api.commerce.dto.response.AdminCardResponse;
import com.kiwobollae.api.commerce.dto.response.AdminExchangeProductOptionResponse;
import com.kiwobollae.api.commerce.service.AdminCardService;
import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 쿠폰", description = "실물 교환 쿠폰 마스터 관리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/admin/card")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCardController {

  private final AdminCardService adminCardService;

  @Operation(summary = "관리자 쿠폰 목록")
  @GetMapping
  public ResponseEntity<ApiResponse<List<AdminCardResponse>>> getCards() {
    return ResponseEntity.ok(ApiResponse.success(adminCardService.getCards()));
  }

  @Operation(summary = "쿠폰 연결용 활성 교환 상품 목록")
  @GetMapping("/exchange-products")
  public ResponseEntity<ApiResponse<List<AdminExchangeProductOptionResponse>>>
      getExchangeProducts() {
    return ResponseEntity.ok(ApiResponse.success(adminCardService.getActiveExchangeProducts()));
  }

  @Operation(summary = "쿠폰 등록")
  @PostMapping
  public ResponseEntity<ApiResponse<AdminCardResponse>> create(
      @Valid @RequestBody CardRequest request) {
    return ResponseEntity.ok(ApiResponse.success(adminCardService.create(request)));
  }

  @Operation(summary = "쿠폰 정보·가격 수정")
  @PutMapping("/{cardId}")
  public ResponseEntity<ApiResponse<AdminCardResponse>> update(
      @PathVariable Long cardId, @Valid @RequestBody CardRequest request) {
    return ResponseEntity.ok(ApiResponse.success(adminCardService.update(cardId, request)));
  }

  @Operation(summary = "쿠폰 노출 상태 변경")
  @PatchMapping("/{cardId}/status")
  public ResponseEntity<ApiResponse<AdminCardResponse>> changeStatus(
      @PathVariable Long cardId, @Valid @RequestBody AdminCardStatusRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(adminCardService.changeStatus(cardId, request.status())));
  }

  @Operation(summary = "쿠폰 숨김 처리")
  @DeleteMapping("/{cardId}")
  public ResponseEntity<ApiResponse<AdminCardResponse>> hide(@PathVariable Long cardId) {
    return ResponseEntity.ok(ApiResponse.success(adminCardService.hide(cardId)));
  }
}
