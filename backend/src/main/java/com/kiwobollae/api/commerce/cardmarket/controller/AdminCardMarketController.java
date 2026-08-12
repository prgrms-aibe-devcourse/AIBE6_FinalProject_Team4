package com.kiwobollae.api.commerce.cardmarket.controller;

import com.kiwobollae.api.commerce.cardmarket.dto.AdminCardMarketRevenueResponse;
import com.kiwobollae.api.commerce.cardmarket.service.AdminCardMarketRevenueService;
import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 카드 거래소", description = "카드 거래소 플랫폼 수수료 수익 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/admin/card/market")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCardMarketController {

  private final AdminCardMarketRevenueService revenueService;

  @Operation(summary = "카드 거래소 수수료 수익 조회")
  @GetMapping("/revenue")
  public ResponseEntity<ApiResponse<AdminCardMarketRevenueResponse>> getRevenue(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(ApiResponse.success(revenueService.getRevenue(page, size)));
  }
}
