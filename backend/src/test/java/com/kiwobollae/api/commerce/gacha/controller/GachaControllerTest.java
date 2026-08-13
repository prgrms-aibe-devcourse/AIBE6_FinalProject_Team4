package com.kiwobollae.api.commerce.gacha.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kiwobollae.api.commerce.gacha.dto.GachaPackPurchaseRequest;
import com.kiwobollae.api.commerce.gacha.dto.GachaPackPurchaseResponse;
import com.kiwobollae.api.commerce.gacha.service.GachaCosmeticService;
import com.kiwobollae.api.commerce.gacha.service.GachaDismantleService;
import com.kiwobollae.api.commerce.gacha.service.GachaPackPurchaseService;
import com.kiwobollae.api.commerce.gacha.service.GachaQueryService;
import com.kiwobollae.api.commerce.gacha.service.GachaShardWalletService;
import com.kiwobollae.api.global.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class GachaControllerTest {

  @Mock private GachaQueryService queryService;
  @Mock private GachaPackPurchaseService purchaseService;
  @Mock private GachaShardWalletService shardWalletService;
  @Mock private GachaDismantleService dismantleService;
  @Mock private GachaCosmeticService cosmeticService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new GachaController(
                    queryService,
                    purchaseService,
                    shardWalletService,
                    dismantleService,
                    cosmeticService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(7L, null, List.of()));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void acceptsLegacyPurchaseWithoutExpectedUnitPoint() throws Exception {
    GachaPackPurchaseRequest request = new GachaPackPurchaseRequest(9L, 1, null);
    given(purchaseService.purchase(7L, "legacy-key", request))
        .willReturn(
            new GachaPackPurchaseResponse(
                501L, 9L, "시즌 1 가챠 카드팩", 1, 120L, 120L, 120L, 0L, 680L, List.of(701L)));

    mockMvc
        .perform(
            post("/api/v1/card/gacha/purchases")
                .header("Idempotency-Key", "legacy-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"productId":9,"quantity":1}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.unitPoint").value(120L))
        .andExpect(jsonPath("$.data.totalPoint").value(120L));

    verify(purchaseService).purchase(7L, "legacy-key", request);
  }

  @Test
  void rejectsNegativeExpectedUnitPoint() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/card/gacha/purchases")
                .header("Idempotency-Key", "purchase-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"productId":9,"quantity":1,"expectedUnitPoint":-1}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("expectedUnitPoint"));

    verify(purchaseService, never())
        .purchase(7L, "purchase-key", new GachaPackPurchaseRequest(9L, 1, -1L));
  }
}
