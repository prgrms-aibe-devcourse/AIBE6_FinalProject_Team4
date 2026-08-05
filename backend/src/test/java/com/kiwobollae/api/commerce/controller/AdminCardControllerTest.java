package com.kiwobollae.api.commerce.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kiwobollae.api.commerce.dto.response.AdminExchangeProductOptionResponse;
import com.kiwobollae.api.commerce.service.AdminCardService;
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
class AdminCardControllerTest {

  @Mock private AdminCardService adminCardService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new AdminCardController(adminCardService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void returnsHiddenInclusiveAdminCardList() throws Exception {
    given(adminCardService.getCards()).willReturn(List.of());

    mockMvc
        .perform(get("/api/v1/admin/card"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray());
  }

  @Test
  void returnsActiveExchangeProductOptions() throws Exception {
    given(adminCardService.getActiveExchangeProducts())
        .willReturn(List.of(new AdminExchangeProductOptionResponse(3L, "수박", 8)));

    mockMvc
        .perform(get("/api/v1/admin/card/exchange-products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(3L))
        .andExpect(jsonPath("$.data[0].name").value("수박"));
  }
}
