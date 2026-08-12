package com.kiwobollae.api.commerce.cardmarket.controller;

import com.kiwobollae.api.commerce.cardmarket.dto.AdminCardMarketRevenueResponse;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketTradeType;
import com.kiwobollae.api.commerce.cardmarket.service.AdminCardMarketRevenueService;
import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) Long userId,
      @RequestParam(required = false) Long cardId,
      @RequestParam(required = false) CardMarketTradeType tradeType,
      @RequestParam(required = false) String keyword) {
    return ResponseEntity.ok(
        ApiResponse.success(
            revenueService.getRevenue(
                page, size, from, to, userId, cardId, tradeType, keyword)));
  }

  @Operation(summary = "카드 거래소 수수료 내역 CSV 다운로드")
  @GetMapping(value = "/revenue.csv", produces = "text/csv")
  public ResponseEntity<byte[]> exportRevenueCsv(
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) Long userId,
      @RequestParam(required = false) Long cardId,
      @RequestParam(required = false) CardMarketTradeType tradeType,
      @RequestParam(required = false) String keyword) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=card-market-revenue.csv")
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .body(revenueService.exportCsv(from, to, userId, cardId, tradeType, keyword));
  }
}
