package com.kiwobollae.api.commerce.cardmarket.controller;

import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketListingCreateRequest;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketListingResponse;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketNegotiationResponse;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketPageResponse;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketProposalRequest;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketSellableCardResponse;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketTradeResponse;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketWalletResponse;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketAssetType;
import com.kiwobollae.api.commerce.cardmarket.service.CardMarketCommandService;
import com.kiwobollae.api.commerce.cardmarket.service.CardMarketQueryService;
import com.kiwobollae.api.global.common.ApiResponse;
import com.kiwobollae.api.global.common.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiVersion.V1 + "/card/market")
@RequiredArgsConstructor
public class CardMarketController {

  private final CardMarketQueryService queryService;
  private final CardMarketCommandService commandService;

  @Operation(summary = "카드 거래소 판매글 목록")
  @GetMapping("/listings")
  public ResponseEntity<ApiResponse<CardMarketPageResponse<CardMarketListingResponse>>> listings(
      @RequestParam(required = false) CardMarketAssetType assetType,
      @RequestParam(required = false) Long cardId,
      @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
    return ResponseEntity.ok(ApiResponse.success(queryService.getListings(assetType, cardId, pageable)));
  }

  @Operation(summary = "카드 거래소 판매글 상세")
  @GetMapping("/listings/{listingId}")
  public ResponseEntity<ApiResponse<CardMarketListingResponse>> listing(
      @PathVariable Long listingId) {
    return ResponseEntity.ok(ApiResponse.success(queryService.getListing(listingId)));
  }

  @Operation(summary = "내 판매 가능 카드")
  @GetMapping("/me/sellable-cards")
  public ResponseEntity<ApiResponse<List<CardMarketSellableCardResponse>>> sellableCards(
      @AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(ApiResponse.success(queryService.getMySellableCards(userId)));
  }

  @Operation(summary = "거래소용 내 포인트")
  @GetMapping("/me/wallet")
  public ResponseEntity<ApiResponse<CardMarketWalletResponse>> wallet(
      @AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(ApiResponse.success(queryService.getMyWallet(userId)));
  }

  @Operation(summary = "내 판매글")
  @GetMapping("/me/listings")
  public ResponseEntity<ApiResponse<List<CardMarketListingResponse>>> myListings(
      @AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(ApiResponse.success(queryService.getMyListings(userId)));
  }

  @Operation(summary = "내가 보낸 가격 제안")
  @GetMapping("/me/negotiations/sent")
  public ResponseEntity<ApiResponse<List<CardMarketNegotiationResponse>>> sentNegotiations(
      @AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(ApiResponse.success(queryService.getMySentNegotiations(userId)));
  }

  @Operation(summary = "내가 받은 가격 제안")
  @GetMapping("/me/negotiations/received")
  public ResponseEntity<ApiResponse<List<CardMarketNegotiationResponse>>> receivedNegotiations(
      @AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(ApiResponse.success(queryService.getMyReceivedNegotiations(userId)));
  }

  @Operation(summary = "내 카드 거래 내역")
  @GetMapping("/me/trades")
  public ResponseEntity<ApiResponse<List<CardMarketTradeResponse>>> trades(
      @AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(ApiResponse.success(queryService.getMyTrades(userId)));
  }

  @Operation(summary = "카드 판매 등록")
  @PostMapping("/listings")
  public ResponseEntity<ApiResponse<CardMarketListingResponse>> createListing(
      @AuthenticationPrincipal Long userId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody CardMarketListingCreateRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(commandService.createListing(userId, idempotencyKey, request)));
  }

  @Operation(summary = "카드 판매 취소")
  @DeleteMapping("/listings/{listingId}")
  public ResponseEntity<ApiResponse<CardMarketListingResponse>> cancelListing(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long listingId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    return ResponseEntity.ok(
        ApiResponse.success(commandService.cancelListing(userId, listingId, idempotencyKey)));
  }

  @Operation(summary = "카드 바로 구매")
  @PostMapping("/listings/{listingId}/purchases")
  public ResponseEntity<ApiResponse<CardMarketTradeResponse>> buyNow(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long listingId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    return ResponseEntity.ok(
        ApiResponse.success(commandService.buyNow(userId, listingId, idempotencyKey)));
  }

  @Operation(summary = "구매 가격 최초 제안")
  @PostMapping("/listings/{listingId}/negotiations")
  public ResponseEntity<ApiResponse<CardMarketNegotiationResponse>> createNegotiation(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long listingId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody CardMarketProposalRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            commandService.createNegotiation(userId, listingId, idempotencyKey, request)));
  }

  @Operation(summary = "가격 재제안 또는 역제안")
  @PostMapping("/negotiations/{negotiationId}/proposals")
  public ResponseEntity<ApiResponse<CardMarketNegotiationResponse>> propose(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long negotiationId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody CardMarketProposalRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(commandService.propose(userId, negotiationId, idempotencyKey, request)));
  }

  @Operation(summary = "가격 제안 수락")
  @PostMapping("/negotiations/{negotiationId}/acceptances")
  public ResponseEntity<ApiResponse<CardMarketTradeResponse>> accept(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long negotiationId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    return ResponseEntity.ok(
        ApiResponse.success(commandService.accept(userId, negotiationId, idempotencyKey)));
  }

  @Operation(summary = "가격 제안 거절")
  @PostMapping("/negotiations/{negotiationId}/rejections")
  public ResponseEntity<ApiResponse<CardMarketNegotiationResponse>> reject(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long negotiationId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    return ResponseEntity.ok(
        ApiResponse.success(commandService.reject(userId, negotiationId, idempotencyKey)));
  }

  @Operation(summary = "내 가격 제안 취소")
  @DeleteMapping("/negotiations/{negotiationId}")
  public ResponseEntity<ApiResponse<CardMarketNegotiationResponse>> cancelNegotiation(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long negotiationId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    return ResponseEntity.ok(
        ApiResponse.success(
            commandService.cancelNegotiation(userId, negotiationId, idempotencyKey)));
  }
}
