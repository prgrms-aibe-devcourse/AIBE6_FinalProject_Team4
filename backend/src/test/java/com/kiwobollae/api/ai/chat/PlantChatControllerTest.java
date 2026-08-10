package com.kiwobollae.api.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kiwobollae.api.ai.chat.dto.PlantChatRequest;
import com.kiwobollae.api.ai.chat.dto.PlantChatResponse;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.global.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PlantChatControllerTest {

  @Mock private PlantChatService plantChatService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new PlantChatController(plantChatService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();
    authenticateAs(7L);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void returnsChatAnswerForAuthenticatedProfileOwner() throws Exception {
    given(plantChatService.chat(eq(7L), eq(21L), any(PlantChatRequest.class)))
        .willReturn(
            new PlantChatResponse(
                "물을 주기 전에 흙을 확인해 주세요.", List.of("겉흙 2cm를 확인하세요."), List.of("배수구를 확인하세요.")));

    mockMvc
        .perform(
            post("/api/v1/ai/plant-profiles/21/chat")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {
                      "question": "물을 언제 줄까요?",
                      "currentJournalContent": "오늘 잎은 괜찮아 보여요.",
                      "recentMessages": [
                        {"role": "USER", "content": "어제 물을 줬어요."},
                        {"role": "ASSISTANT", "content": "흙을 확인해 주세요."}
                      ]
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.answer").value("물을 주기 전에 흙을 확인해 주세요."))
        .andExpect(jsonPath("$.data.recommendedActions[0]").value("겉흙 2cm를 확인하세요."))
        .andExpect(jsonPath("$.data.additionalChecks[0]").value("배수구를 확인하세요."));

    ArgumentCaptor<PlantChatRequest> requestCaptor =
        ArgumentCaptor.forClass(PlantChatRequest.class);
    verify(plantChatService).chat(eq(7L), eq(21L), requestCaptor.capture());
    assertThat(requestCaptor.getValue().recentMessages()).hasSize(2);
  }

  @Test
  void rejectsBlankQuestionAtHttpBoundary() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/ai/plant-profiles/21/chat")
                .contentType(APPLICATION_JSON)
                .content("{\"question\":\"   \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("question"));

    verifyNoInteractions(plantChatService);
  }

  @Test
  void rejectsMoreThanSixRecentMessagesAtHttpBoundary() throws Exception {
    String messages =
        java.util.stream.IntStream.range(0, 7)
            .mapToObj(index -> "{\"role\":\"USER\",\"content\":\"질문\"}")
            .collect(java.util.stream.Collectors.joining(","));

    mockMvc
        .perform(
            post("/api/v1/ai/plant-profiles/21/chat")
                .contentType(APPLICATION_JSON)
                .content("{\"question\":\"질문\",\"recentMessages\":[" + messages + "]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));

    verifyNoInteractions(plantChatService);
  }

  @Test
  void returnsNotFoundWhenProfileDoesNotBelongToUser() throws Exception {
    given(plantChatService.chat(eq(7L), eq(99L), any(PlantChatRequest.class)))
        .willThrow(new BusinessException(ErrorCode.PLANT_PROFILE_NOT_FOUND));

    mockMvc
        .perform(
            post("/api/v1/ai/plant-profiles/99/chat")
                .contentType(APPLICATION_JSON)
                .content("{\"question\":\"이 식물은 괜찮나요?\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PLANT_PROFILE_NOT_FOUND"));
  }

  private void authenticateAs(Long userId) {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
  }
}
