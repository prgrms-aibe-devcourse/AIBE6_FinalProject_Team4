package com.kiwobollae.api.commerce.controller;

import com.kiwobollae.api.commerce.dto.request.AdminProductRequest;
import com.kiwobollae.api.commerce.dto.request.AdminProductStatusRequest;
import com.kiwobollae.api.commerce.dto.request.AdminProductStockRequest;
import com.kiwobollae.api.commerce.dto.response.AdminProductResponse;
import com.kiwobollae.api.commerce.service.AdminProductService;
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

@Tag(name = "관리자 상품", description = "상점·가챠 팩 상품 관리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiVersion.V1 + "/admin/product")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductController {

  private final AdminProductService adminProductService;

  @Operation(summary = "관리자 상품 목록")
  @GetMapping
  public ResponseEntity<ApiResponse<List<AdminProductResponse>>> getProducts() {
    return ResponseEntity.ok(ApiResponse.success(adminProductService.getProducts()));
  }

  @Operation(summary = "상품 등록")
  @PostMapping
  public ResponseEntity<ApiResponse<AdminProductResponse>> create(
      @Valid @RequestBody AdminProductRequest request) {
    return ResponseEntity.ok(ApiResponse.success(adminProductService.create(request)));
  }

  @Operation(summary = "상품 정보·가격 수정")
  @PutMapping("/{productId}")
  public ResponseEntity<ApiResponse<AdminProductResponse>> update(
      @PathVariable Long productId, @Valid @RequestBody AdminProductRequest request) {
    return ResponseEntity.ok(ApiResponse.success(adminProductService.update(productId, request)));
  }

  @Operation(summary = "상품 재고 증감")
  @PatchMapping("/{productId}/stock")
  public ResponseEntity<ApiResponse<AdminProductResponse>> adjustStock(
      @PathVariable Long productId, @Valid @RequestBody AdminProductStockRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(adminProductService.adjustStock(productId, request.delta())));
  }

  @Operation(summary = "상품 노출 상태 변경")
  @PatchMapping("/{productId}/status")
  public ResponseEntity<ApiResponse<AdminProductResponse>> changeStatus(
      @PathVariable Long productId, @Valid @RequestBody AdminProductStatusRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(adminProductService.changeStatus(productId, request.status())));
  }

  @Operation(summary = "상품 삭제(숨김 처리)")
  @DeleteMapping("/{productId}")
  public ResponseEntity<ApiResponse<AdminProductResponse>> hide(@PathVariable Long productId) {
    return ResponseEntity.ok(ApiResponse.success(adminProductService.hide(productId)));
  }
}
