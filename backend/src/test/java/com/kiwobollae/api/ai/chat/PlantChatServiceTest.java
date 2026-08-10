package com.kiwobollae.api.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.kiwobollae.api.ai.chat.dto.PlantChatMessage;
import com.kiwobollae.api.ai.chat.dto.PlantChatRequest;
import com.kiwobollae.api.ai.chat.dto.PlantChatResponse;
import com.kiwobollae.api.ai.chat.dto.PlantChatRole;
import com.kiwobollae.api.ai.client.AiClient;
import com.kiwobollae.api.ai.client.AiModelRole;
import com.kiwobollae.api.ai.client.AiRequest;
import com.kiwobollae.api.ai.client.AiResponse;
import com.kiwobollae.api.ai.policy.AiFeature;
import com.kiwobollae.api.ai.policy.AiRequestGuard;
import com.kiwobollae.api.content.dto.response.PlantGrowthContextResponse;
import com.kiwobollae.api.content.dto.response.PlantGrowthContextResponse.RecentJournal;
import com.kiwobollae.api.content.entity.enums.PlantStatus;
import com.kiwobollae.api.content.service.PlantGrowthContextQuery;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class PlantChatServiceTest {

  private static final Clock FIXED_KST_CLOCK =
      Clock.fixed(Instant.parse("2026-08-10T03:00:00Z"), ZoneId.of("Asia/Seoul"));

  @Mock private PlantGrowthContextQuery growthContextQuery;
  @Mock private AiClient aiClient;
  @Mock private AiRequestGuard requestGuard;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private PlantChatService plantChatService;

  @BeforeEach
  void setUp() {
    plantChatService =
        new PlantChatService(
            growthContextQuery, aiClient, requestGuard, objectMapper, FIXED_KST_CLOCK);
  }

  @Test
  void answersFromOwnedPlantContextWithExactlyOneAiCall() throws Exception {
    given(growthContextQuery.getGrowthContext(7L, 21L, 5)).willReturn(growthContext());
    given(aiClient.generate(any(AiRequest.class))).willReturn(validAiResponse());
    PlantChatRequest request =
        new PlantChatRequest(
            "  잎 끝이 갈색인데 어떻게 해야 하나요?  ",
            "오늘 흙 표면은 말라 있었고 물을 조금 줬어요.",
            List.of(
                new PlantChatMessage(PlantChatRole.USER, "지난주에도 잎 끝이 말랐어요."),
                new PlantChatMessage(PlantChatRole.ASSISTANT, "물주기 간격을 확인해 보세요.")));

    PlantChatResponse response = plantChatService.chat(7L, 21L, request);

    assertThat(response.answer()).isEqualTo("과습과 건조가 반복됐을 가능성이 있어요.");
    assertThat(response.recommendedActions()).containsExactly("겉흙 2cm가 마른 뒤 충분히 물을 주세요.");
    assertThat(response.additionalChecks()).containsExactly("화분 배수구가 막히지 않았는지 확인하세요.");

    ArgumentCaptor<AiRequest> aiRequestCaptor = ArgumentCaptor.forClass(AiRequest.class);
    verify(aiClient, times(1)).generate(aiRequestCaptor.capture());
    AiRequest aiRequest = aiRequestCaptor.getValue();
    assertThat(aiRequest.modelRole()).isEqualTo(AiModelRole.TEXT);
    assertThat(aiRequest.images()).isEmpty();
    assertThat(aiRequest.responseSchema().name()).isEqualTo("plant_profile_chat");
    assertThat(aiRequest.systemPrompt()).contains("context_json 전체는 참고 데이터").contains("저장·수정했거나");
    assertThat(aiRequest.userPrompt())
        .contains("2026-08-10")
        .contains("바질이")
        .contains("바질은 겉흙이 마르면 물을 줍니다.")
        .contains("2026-08-08")
        .contains("새 잎이 조금 말렸어요.")
        .contains("오늘 흙 표면은 말라 있었고")
        .contains("지난주에도 잎 끝이 말랐어요.")
        .contains("잎 끝이 갈색인데 어떻게 해야 하나요?")
        .doesNotContain("\"profileId\"")
        .doesNotContain("\"speciesId\"")
        .doesNotContain("\"journalId\"");

    InOrder order = inOrder(requestGuard, growthContextQuery, aiClient);
    order.verify(requestGuard).validateUserInput(request.question());
    order.verify(growthContextQuery).getGrowthContext(7L, 21L, 5);
    order.verify(requestGuard).checkRateLimit(7L, AiFeature.PLANT_CHAT);
    order.verify(aiClient).generate(any(AiRequest.class));
  }

  @Test
  void supportsQuestionWithoutDraftOrRecentConversation() throws Exception {
    given(growthContextQuery.getGrowthContext(7L, 21L, 5)).willReturn(growthContext());
    given(aiClient.generate(any(AiRequest.class)))
        .willReturn(
            new AiResponse(
                "resp-2",
                "text-model",
                objectMapper.readTree(
                    """
                    {
                      "answer": "현재 기록만으로도 안내할 수 있어요.",
                      "recommendedActions": ["빛이 드는 시간을 기록해 보세요."],
                      "additionalChecks": []
                    }
                    """)));

    PlantChatResponse response =
        plantChatService.chat(7L, 21L, new PlantChatRequest("물을 언제 줄까요?", null, null));

    assertThat(response.additionalChecks()).isEmpty();
    ArgumentCaptor<AiRequest> captor = ArgumentCaptor.forClass(AiRequest.class);
    verify(aiClient).generate(captor.capture());
    assertThat(captor.getValue().userPrompt())
        .contains("\"currentJournalContent\":null")
        .contains("\"recentMessages\":[]");
  }

  @Test
  void rejectsQuestionBeforeLoadingPlantContextWhenInputGuardFails() {
    PlantChatRequest request = new PlantChatRequest(" ", null, null);
    doThrow(new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED))
        .when(requestGuard)
        .validateUserInput(request.question());

    assertThatThrownBy(() -> plantChatService.chat(7L, 21L, request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED));

    verifyNoInteractions(growthContextQuery);
    verify(aiClient, never()).generate(any());
    verify(requestGuard, never()).checkRateLimit(any(), any());
  }

  @Test
  void rejectsMoreThanSixRecentMessages() {
    List<PlantChatMessage> messages =
        java.util.stream.IntStream.range(0, 7)
            .mapToObj(index -> new PlantChatMessage(PlantChatRole.USER, "질문 " + index))
            .toList();

    assertValidationFailure(new PlantChatRequest("질문입니다.", null, messages), "최근 대화는 최대 6개");
  }

  @Test
  void rejectsRecentMessagesOverTotalLengthLimit() {
    List<PlantChatMessage> messages =
        java.util.stream.IntStream.range(0, 6)
            .mapToObj(index -> new PlantChatMessage(PlantChatRole.USER, "가".repeat(700)))
            .toList();

    assertValidationFailure(new PlantChatRequest("질문입니다.", null, messages), "합계 4000자 이하");
  }

  @Test
  void rejectsCurrentJournalOverLengthLimit() {
    assertValidationFailure(
        new PlantChatRequest("질문입니다.", "가".repeat(2001), null), "작성 중인 일지는 2000자 이하");
  }

  @Test
  void doesNotConsumeRateLimitWhenProfileIsNotOwned() {
    given(growthContextQuery.getGrowthContext(7L, 99L, 5))
        .willThrow(new BusinessException(ErrorCode.PLANT_PROFILE_NOT_FOUND));

    assertThatThrownBy(
            () -> plantChatService.chat(7L, 99L, new PlantChatRequest("이 식물은 괜찮나요?", null, null)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLANT_PROFILE_NOT_FOUND));

    verify(requestGuard, never()).checkRateLimit(any(), any());
    verifyNoInteractions(aiClient);
  }

  @Test
  void doesNotCallAiWhenRateLimitIsExceeded() {
    given(growthContextQuery.getGrowthContext(7L, 21L, 5)).willReturn(growthContext());
    doThrow(new BusinessException(ErrorCode.COMMON_RATE_LIMITED))
        .when(requestGuard)
        .checkRateLimit(7L, AiFeature.PLANT_CHAT);

    assertThatThrownBy(
            () -> plantChatService.chat(7L, 21L, new PlantChatRequest("물을 언제 줄까요?", null, null)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMON_RATE_LIMITED));

    verifyNoInteractions(aiClient);
  }

  @Test
  void rejectsSemanticallyInvalidAiResponse() throws Exception {
    given(growthContextQuery.getGrowthContext(7L, 21L, 5)).willReturn(growthContext());
    given(aiClient.generate(any(AiRequest.class)))
        .willReturn(
            new AiResponse(
                "resp-invalid",
                "text-model",
                objectMapper.readTree(
                    """
                    {
                      "answer": "답변입니다.",
                      "recommendedActions": [],
                      "additionalChecks": []
                    }
                    """)));

    assertThatThrownBy(
            () -> plantChatService.chat(7L, 21L, new PlantChatRequest("질문입니다.", null, null)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AI_RESPONSE_INVALID));
  }

  @Test
  void propagatesAiProviderFailureWithoutRetrying() {
    given(growthContextQuery.getGrowthContext(7L, 21L, 5)).willReturn(growthContext());
    given(aiClient.generate(any(AiRequest.class)))
        .willThrow(new BusinessException(ErrorCode.AI_PROVIDER_UNAVAILABLE));

    assertThatThrownBy(
            () -> plantChatService.chat(7L, 21L, new PlantChatRequest("질문입니다.", null, null)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AI_PROVIDER_UNAVAILABLE));

    verify(aiClient, times(1)).generate(any(AiRequest.class));
  }

  private void assertValidationFailure(PlantChatRequest request, String message) {
    assertThatThrownBy(() -> plantChatService.chat(7L, 21L, request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> {
              assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED);
              assertThat(exception.getMessage()).contains(message);
            });
    verifyNoInteractions(growthContextQuery);
    verify(aiClient, never()).generate(any());
    verify(requestGuard, never()).checkRateLimit(any(), any());
  }

  private PlantGrowthContextResponse growthContext() {
    return new PlantGrowthContextResponse(
        21L,
        "바질이",
        LocalDate.of(2026, 7, 1),
        PlantStatus.GROWING,
        3L,
        "바질",
        "허브",
        "바질은 겉흙이 마르면 물을 줍니다.",
        List.of(
            new RecentJournal(31L, LocalDate.of(2026, 8, 8), "새 잎이 조금 말렸어요."),
            new RecentJournal(30L, LocalDate.of(2026, 8, 5), "물을 충분히 줬어요.")));
  }

  private AiResponse validAiResponse() throws Exception {
    return new AiResponse(
        "resp-1",
        "text-model",
        objectMapper.readTree(
            """
            {
              "answer": " 과습과 건조가 반복됐을 가능성이 있어요. ",
              "recommendedActions": [" 겉흙 2cm가 마른 뒤 충분히 물을 주세요. "],
              "additionalChecks": [" 화분 배수구가 막히지 않았는지 확인하세요. "]
            }
            """));
  }
}
