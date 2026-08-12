package com.kiwobollae.api.commerce.cardmarket.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketListingResponse;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketPageResponse;
import com.kiwobollae.api.commerce.cardmarket.dto.CardMarketWalletResponse;
import com.kiwobollae.api.commerce.cardmarket.dto.AdminCardMarketRevenueResponse;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketAssetType;
import com.kiwobollae.api.commerce.cardmarket.entity.enums.CardMarketListingStatus;
import com.kiwobollae.api.commerce.cardmarket.service.CardMarketCommandService;
import com.kiwobollae.api.commerce.cardmarket.service.CardMarketQueryService;
import com.kiwobollae.api.commerce.cardmarket.service.AdminCardMarketRevenueService;
import com.kiwobollae.api.commerce.gacha.entity.enums.TradingCardRarity;
import com.kiwobollae.api.global.security.JwtTokenProvider;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:mysql://localhost:3306/kiwobollae_card_market_auth_test"
          + "?createDatabaseIfNotExist=true&serverTimezone=Asia%2FSeoul&characterEncoding=UTF-8",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "app.seed.gacha.enabled=false",
      "app.seed.charge-product.enabled=false",
      "app.seed.product.enabled=false",
      "app.seed.card.enabled=false"
    })
@AutoConfigureMockMvc
class CardMarketControllerAuthorizationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtTokenProvider jwtTokenProvider;

  @MockitoBean private CardMarketQueryService queryService;
  @MockitoBean private CardMarketCommandService commandService;
  @MockitoBean private AdminCardMarketRevenueService revenueService;

  @Test
  void anonymousUserCanBrowseOpenListings() throws Exception {
    given(queryService.getListings(any(), any(), any(), any()))
        .willReturn(new CardMarketPageResponse<>(List.of(), 0, 20, 0, 0));

    mockMvc
        .perform(get("/api/v1/card/market/listings"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").isArray());
  }

  @Test
  void anonymousUserCannotReadWalletOrCreateListing() throws Exception {
    mockMvc
        .perform(get("/api/v1/card/market/me/wallet"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_AUTHENTICATION_REQUIRED"));
    mockMvc
        .perform(
            post("/api/v1/card/market/listings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cardId\":11,\"askingPrice\":1000}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void authenticatedUserCanReadSeparatedWalletAndCreateListing() throws Exception {
    String token = jwtTokenProvider.generateAccessToken(7L, "USER");
    given(queryService.getMyWallet(7L))
        .willReturn(
            new CardMarketWalletResponse(
                1_000L, 500L, 300L, "유상 포인트만 사용", "무상 포인트 사용 불가"));
    CardMarketListingResponse listing =
        new CardMarketListingResponse(
            101L,
            7L,
            "판매자",
            11L,
            null,
            "HYPER_11",
            "하이퍼 카드",
            TradingCardRarity.HYPER_RARE,
            "/cards/11/image.png",
            CardMarketAssetType.HYPER_RARE,
            1_000L,
            CardMarketListingStatus.OPEN,
            0,
            LocalDateTime.of(2026, 8, 13, 0, 0),
            LocalDateTime.of(2026, 8, 6, 0, 0));
    given(commandService.createListing(eq(7L), eq("listing-key"), any()))
        .willReturn(listing);

    mockMvc
        .perform(
            get("/api/v1/card/market/me/wallet")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.paidPoint").value(1_000))
        .andExpect(jsonPath("$.data.freePoint").value(500))
        .andExpect(jsonPath("$.data.escrowedPaidPoint").value(300));
    mockMvc
        .perform(
            post("/api/v1/card/market/listings")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "listing-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cardId\":11,\"askingPrice\":1000}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(101));
  }

  @Test
  void authenticatedMutationRequiresIdempotencyKey() throws Exception {
    String token = jwtTokenProvider.generateAccessToken(7L, "USER");
    given(commandService.createListing(eq(7L), isNull(), any()))
        .willThrow(new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED));

    mockMvc
        .perform(
            post("/api/v1/card/market/listings")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cardId\":11,\"askingPrice\":1000}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void onlyAdminCanReadMarketRevenue() throws Exception {
    String userToken = jwtTokenProvider.generateAccessToken(7L, "USER");
    String adminToken = jwtTokenProvider.generateAccessToken(1L, "ADMIN");
    given(revenueService.getRevenue(0, 20, null, null, null, null, null, null))
        .willReturn(new AdminCardMarketRevenueResponse(0, 0, 0, 0, List.of(), 0, 20, 0, 0));

    mockMvc
        .perform(
            get("/api/v1/admin/card/market/revenue")
                .header("Authorization", "Bearer " + userToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/api/v1/admin/card/market/revenue")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalFeePoint").value(0));
  }
}
