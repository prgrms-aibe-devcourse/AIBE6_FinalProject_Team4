package com.kiwobollae.api.commerce.gacha.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kiwobollae.api.commerce.gacha.dto.AdminGachaDrawPageResponse;
import com.kiwobollae.api.commerce.gacha.dto.GachaManualRetryResponse;
import com.kiwobollae.api.commerce.gacha.entity.enums.GachaDrawStatus;
import com.kiwobollae.api.commerce.gacha.service.AdminGachaQueryService;
import com.kiwobollae.api.commerce.gacha.service.GachaManualReviewService;
import com.kiwobollae.api.global.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AdminGachaControllerTest {

  @Mock private GachaManualReviewService manualReviewService;
  @Mock private AdminGachaQueryService queryService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new AdminGachaController(manualReviewService, queryService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void requeuesManualReviewDraw() throws Exception {
    given(manualReviewService.retry(10L))
        .willReturn(new GachaManualRetryResponse(10L, GachaDrawStatus.PENDING));

    mockMvc
        .perform(patch("/api/v1/admin/card/gacha/draws/10/retry"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.data.drawId").value(10L))
        .andExpect(jsonPath("$.data.status").value("PENDING"));
  }

  @Test
  void filtersAdminDrawHistory() throws Exception {
    given(queryService.getDraws(GachaDrawStatus.MANUAL_REVIEW, 7L, 0, 20))
        .willReturn(new AdminGachaDrawPageResponse(List.of(), 0, 20, 0, 0));

    mockMvc
        .perform(
            get("/api/v1/admin/card/gacha/draws")
                .param("status", "MANUAL_REVIEW")
                .param("userId", "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(0));
  }
}
