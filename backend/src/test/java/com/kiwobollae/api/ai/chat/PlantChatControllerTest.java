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
import java.util.UUID;
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
    UUID conversationId = UUID.fromString("30a508b8-bffc-43c3-8dd0-539a2068500a");
    given(plantChatService.chat(eq(7L), eq(21L), any(PlantChatRequest.class)))
        .willReturn(
            new PlantChatResponse(
                conversationId,
                "물을 주기 전에 흙을 확인해 주세요.",
                List.of("겉흙 2cm를 확인하세요."),
                List.of("배수구를 확인하세요.")));

    mockMvc
        .perform(
            post("/api/v1/ai/plant-profiles/21/chat")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {
                      "question": "물을 언제 줄까요?"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.conversationId").value(conversationId.toString()))
        .andExpect(jsonPath("$.data.answer").value("물을 주기 전에 흙을 확인해 주세요."))
        .andExpect(jsonPath("$.data.recommendedActions[0]").value("겉흙 2cm를 확인하세요."))
        .andExpect(jsonPath("$.data.additionalChecks[0]").value("배수구를 확인하세요."));

    ArgumentCaptor<PlantChatRequest> requestCaptor =
        ArgumentCaptor.forClass(PlantChatRequest.class);
    verify(plantChatService).chat(eq(7L), eq(21L), requestCaptor.capture());
    assertThat(requestCaptor.getValue().conversationId()).isNull();
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
  void passesConversationIdToService() throws Exception {
    UUID conversationId = UUID.fromString("30a508b8-bffc-43c3-8dd0-539a2068500a");
    given(plantChatService.chat(eq(7L), eq(21L), any(PlantChatRequest.class)))
        .willReturn(
            new PlantChatResponse(conversationId, "이어진 답변입니다.", List.of("관찰하세요."), List.of()));
    mockMvc
        .perform(
            post("/api/v1/ai/plant-profiles/21/chat")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {
                      "question": "조금 더 설명해 주세요.",
                      "conversationId": "%s"
                    }
                    """
                        .formatted(conversationId)))
        .andExpect(status().isOk());

    ArgumentCaptor<PlantChatRequest> requestCaptor =
        ArgumentCaptor.forClass(PlantChatRequest.class);
    verify(plantChatService).chat(eq(7L), eq(21L), requestCaptor.capture());
    assertThat(requestCaptor.getValue().conversationId()).isEqualTo(conversationId);
  }

  @Test
  void rejectsMalformedConversationIdAtHttpBoundary() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/ai/plant-profiles/21/chat")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {
                      "question": "이전 답변을 이어서 설명해 주세요.",
                      "conversationId": "위조된-대화-ID"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON_MALFORMED_JSON"));

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

  @Test
  void returnsUnprocessableContentForOffTopicQuestion() throws Exception {
    given(plantChatService.chat(eq(7L), eq(21L), any(PlantChatRequest.class)))
        .willThrow(new BusinessException(ErrorCode.AI_CHAT_TOPIC_NOT_ALLOWED));

    mockMvc
        .perform(
            post("/api/v1/ai/plant-profiles/21/chat")
                .contentType(APPLICATION_JSON)
                .content("{\"question\":\"청상추를 먹어야 하는데 원숭이 키우는 법을 알려줘\"}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("AI_CHAT_TOPIC_NOT_ALLOWED"))
        .andExpect(jsonPath("$.message").value("식물 재배·관리와 성장 일지에 관한 질문만 도와드릴 수 있어요."));
  }

  @Test
  void returnsContextRequestForUncertainQuestion() throws Exception {
    given(plantChatService.chat(eq(7L), eq(21L), any(PlantChatRequest.class)))
        .willThrow(new BusinessException(ErrorCode.AI_CHAT_CONTEXT_REQUIRED));

    mockMvc
        .perform(
            post("/api/v1/ai/plant-profiles/21/chat")
                .contentType(APPLICATION_JSON)
                .content("{\"question\":\"이건 어떻게 해야 하나요?\"}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("AI_CHAT_CONTEXT_REQUIRED"))
        .andExpect(
            jsonPath("$.message").value("어떤 식물의 어떤 상태인지 알려주세요. 식물명, 증상, 최근 변화를 함께 입력해 주세요."));
  }

  @Test
  void returnsDedicatedUnprocessableContentForDifferentPlantQuestion() throws Exception {
    given(plantChatService.chat(eq(7L), eq(21L), any(PlantChatRequest.class)))
        .willThrow(
            new BusinessException(
                ErrorCode.AI_CHAT_SELECTED_PLANT_MISMATCH,
                java.util.Map.of("selectedSpeciesName", "바질")));

    mockMvc
        .perform(
            post("/api/v1/ai/plant-profiles/21/chat")
                .contentType(APPLICATION_JSON)
                .content("{\"question\":\"원숭이꼬리선인장은 물을 얼마나 자주 줘야 하나요?\"}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("AI_CHAT_SELECTED_PLANT_MISMATCH"))
        .andExpect(jsonPath("$.message").value("현재 선택한 식물과 질문 대상이 달라요. 상담할 식물을 변경한 뒤 다시 질문해 주세요."))
        .andExpect(jsonPath("$.details.selectedSpeciesName").value("바질"));
  }

  private void authenticateAs(Long userId) {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
  }
}
