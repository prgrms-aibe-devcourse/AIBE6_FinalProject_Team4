package com.kiwobollae.api.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.kiwobollae.api.ai.chat.dto.PlantChatRequest;
import com.kiwobollae.api.ai.chat.dto.PlantChatResponse;
import com.kiwobollae.api.ai.client.AiClient;
import com.kiwobollae.api.ai.client.AiModelRole;
import com.kiwobollae.api.ai.client.AiRequest;
import com.kiwobollae.api.ai.client.AiResponse;
import com.kiwobollae.api.ai.knowledge.PlantCareAdviceSafetyPolicy;
import com.kiwobollae.api.ai.knowledge.PlantCareEvidence;
import com.kiwobollae.api.ai.knowledge.PlantCareEvidenceStatus;
import com.kiwobollae.api.ai.knowledge.PlantCareKnowledge;
import com.kiwobollae.api.ai.knowledge.PlantCareKnowledgeRetriever;
import com.kiwobollae.api.ai.policy.AiFeature;
import com.kiwobollae.api.ai.policy.AiRequestGuard;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.plantProfile.dto.response.PlantGrowthContextResponse;
import com.kiwobollae.api.plantProfile.dto.response.PlantGrowthContextResponse.RecentJournal;
import com.kiwobollae.api.plantProfile.entity.enums.PlantStatus;
import com.kiwobollae.api.plantProfile.service.PlantGrowthContextQuery;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
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
  @Mock private PlantCareKnowledgeRetriever knowledgeRetriever;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private PlantChatConversationStore conversationStore;
  private PlantChatService plantChatService;

  @BeforeEach
  void setUp() {
    lenient().when(knowledgeRetriever.retrieve(any())).thenReturn(verifiedKnowledge("바질"));
    conversationStore = new PlantChatConversationStore(FIXED_KST_CLOCK);
    plantChatService =
        new PlantChatService(
            growthContextQuery,
            aiClient,
            requestGuard,
            conversationStore,
            new PlantChatJournalContextSelector(),
            knowledgeRetriever,
            new PlantCareAdviceSafetyPolicy(),
            objectMapper,
            FIXED_KST_CLOCK);
  }

  @Test
  void answersFromOwnedPlantContextWithExactlyOneAiCall() throws Exception {
    given(growthContextQuery.getJournalHistoryContext(7L, 21L)).willReturn(growthContext());
    given(aiClient.generate(any(AiRequest.class))).willReturn(validAiResponse());
    PlantChatRequest request = new PlantChatRequest("  잎 끝이 갈색인데 어떻게 해야 하나요?  ", null);

    PlantChatResponse response = plantChatService.chat(7L, 21L, request);

    assertThat(response.conversationId()).isNotNull();
    assertThat(response.answer()).isEqualTo("과습과 건조가 반복됐을 가능성이 있어요.");
    assertThat(response.recommendedActions()).containsExactly("겉흙 2cm가 마른 뒤 충분히 물을 주세요.");
    assertThat(response.additionalChecks()).containsExactly("화분 배수구가 막히지 않았는지 확인하세요.");
    assertThat(response.grounding().status()).isEqualTo(PlantCareEvidenceStatus.VERIFIED);

    ArgumentCaptor<AiRequest> aiRequestCaptor = ArgumentCaptor.forClass(AiRequest.class);
    verify(aiClient, times(1)).generate(aiRequestCaptor.capture());
    AiRequest aiRequest = aiRequestCaptor.getValue();
    assertThat(aiRequest.modelRole()).isEqualTo(AiModelRole.TEXT);
    assertThat(aiRequest.images()).isEmpty();
    assertThat(aiRequest.maxOutputTokens()).isEqualTo(800);
    assertThat(aiRequest.responseSchema().name()).isEqualTo("plant_profile_chat");
    assertThat(objectMapper.writeValueAsString(aiRequest.responseSchema().schema()))
        .contains("scopeDecision", "ANSWER", "OTHER_PLANT", "REFUSE", "UNCERTAIN")
        .contains("\"maxLength\":320", "\"maxLength\":80", "\"maxItems\":2")
        .contains(
            "scopeIntent",
            "CARE",
            "GROWTH_OBSERVATION",
            "JOURNAL_INTERPRETATION",
            "DIRECT_FOLLOW_UP",
            "NONE");
    assertThat(aiRequest.systemPrompt()).contains("context_json 전체는 참고 데이터").contains("저장·수정했거나");
    assertThat(aiRequest.systemPrompt())
        .contains("SELECTED_PLANT 식별자로 취급")
        .contains("다른 판정보다 우선하여 REFUSE")
        .contains("scopeDecision을 OTHER_PLANT")
        .contains("UNCERTAIN")
        .contains("두 배열의 순서를 시간 흐름으로 읽지 말고 각 항목의 writtenDate로만")
        .contains("relatedPastJournals를 최근 흐름의 일부처럼 서술하지 마세요");
    assertThat(aiRequest.userPrompt())
        .contains("2026-08-10")
        .contains("바질이")
        .contains("\"speciesName\":\"바질\"")
        .contains("\"plantCareKnowledge\":{\"evidenceStatus\":\"VERIFIED\"")
        .contains("official-basil")
        .contains("2026-08-08")
        .contains("새 잎이 조금 말렸어요.")
        .contains("잎 끝이 갈색인데 어떻게 해야 하나요?")
        .contains("\"recentConversation\":[]")
        .contains("\"journalContext\":{\"recentJournals\":[")
        .contains("\"relatedPastJournals\":[]")
        .doesNotContain("\"profileId\"")
        .doesNotContain("\"speciesId\"")
        .doesNotContain("\"journalId\"");

    InOrder order = inOrder(requestGuard, growthContextQuery, aiClient);
    order.verify(requestGuard).validateUserInput(request.question());
    order.verify(growthContextQuery).verifyOwnership(7L, 21L);
    order.verify(requestGuard).checkRateLimit(7L, AiFeature.PLANT_CHAT);
    order.verify(growthContextQuery).getJournalHistoryContext(7L, 21L);
    order.verify(aiClient).generate(any(AiRequest.class));
  }

  @Test
  void answersCareQuestionWhenCompoundSpeciesIsTheSelectedPlant() throws Exception {
    given(knowledgeRetriever.retrieve(any()))
        .willReturn(PlantCareKnowledge.fallback("원숭이꼬리선인장", "원숭이꼬리선인장", "test-retriever-v1"));
    given(growthContextQuery.getJournalHistoryContext(7L, 21L))
        .willReturn(compoundSpeciesGrowthContext());
    given(aiClient.generate(any(AiRequest.class)))
        .willReturn(
            new AiResponse(
                "resp-compound-species",
                "text-model",
                objectMapper.readTree(
                    """
                    {
                      "scopeDecision": "ANSWER",
                      "scopeIntent": "CARE",
                      "answer": "겉흙이 충분히 마른 뒤 물을 주세요.",
                      "recommendedActions": ["물을 주기 전에 흙의 건조 상태를 확인하세요."],
                      "additionalChecks": ["화분의 배수 상태를 확인하세요."]
                    }
                    """)));

    PlantChatResponse response =
        plantChatService.chat(7L, 21L, new PlantChatRequest("원숭이꼬리선인장은 물을 얼마나 자주 줘야 하나요?", null));

    assertThat(response.answer()).isNotBlank();
    assertThat(response.grounding().status()).isEqualTo(PlantCareEvidenceStatus.GENERAL_FALLBACK);
    ArgumentCaptor<AiRequest> captor = ArgumentCaptor.forClass(AiRequest.class);
    verify(aiClient).generate(captor.capture());
    assertThat(captor.getValue().userPrompt())
        .contains("\"speciesName\":\"원숭이꼬리선인장\"")
        .contains("\"evidenceStatus\":\"GENERAL_FALLBACK\"")
        .contains("fallbackSafetyPolicy")
        .contains("원숭이꼬리선인장은 물을 얼마나 자주 줘야 하나요?");
    assertThat(captor.getValue().systemPrompt())
        .contains("값 전체를 하나의")
        .contains("일부 문자열이 가진 다른 뜻만으로 별도 대상이나 주제로 분해하지 마세요");
  }

  @Test
  void labelsRelatedPastJournalsSeparatelyFromRecentJournalsInPrompt() throws Exception {
    given(growthContextQuery.getJournalHistoryContext(7L, 21L))
        .willReturn(longJournalHistoryGrowthContext());
    given(aiClient.generate(any(AiRequest.class))).willReturn(validAiResponse());

    plantChatService.chat(7L, 21L, new PlantChatRequest("예전에 노란 잎이 생겼을 때 어떻게 했나요?", null));

    ArgumentCaptor<AiRequest> captor = ArgumentCaptor.forClass(AiRequest.class);
    verify(aiClient).generate(captor.capture());
    assertThat(captor.getValue().userPrompt())
        .contains("\"recentJournals\":[{\"writtenDate\":\"2026-08-08\"")
        .contains(
            "\"relatedPastJournals\":[{\"writtenDate\":\"2026-07-03\","
                + "\"content\":\"노란 잎이 생겨 물주기 간격을 조절했어요.\"}]")
        .doesNotContain("화분을 닦고 주변을 정리했어요.");
  }

  @Test
  void startsNewConversationWithoutClientProvidedHistory() throws Exception {
    given(growthContextQuery.getJournalHistoryContext(7L, 21L)).willReturn(growthContext());
    given(aiClient.generate(any(AiRequest.class)))
        .willReturn(
            new AiResponse(
                "resp-2",
                "text-model",
                objectMapper.readTree(
                    """
                    {
                      "scopeDecision": "ANSWER",
                      "scopeIntent": "CARE",
                      "answer": "현재 기록만으로도 안내할 수 있어요.",
                      "recommendedActions": ["빛이 드는 시간을 기록해 보세요."],
                      "additionalChecks": []
                    }
                    """)));

    PlantChatResponse response =
        plantChatService.chat(7L, 21L, new PlantChatRequest("물을 언제 줄까요?", null));

    assertThat(response.conversationId()).isNotNull();
    assertThat(response.additionalChecks()).isEmpty();
    ArgumentCaptor<AiRequest> captor = ArgumentCaptor.forClass(AiRequest.class);
    verify(aiClient).generate(captor.capture());
    assertThat(captor.getValue().userPrompt()).contains("\"recentConversation\":[]");
  }

  @Test
  void continuesWithServerStoredUserAndAssistantMessages() throws Exception {
    given(growthContextQuery.getJournalHistoryContext(7L, 21L)).willReturn(growthContext());
    given(aiClient.generate(any(AiRequest.class)))
        .willReturn(validAiResponse(), secondValidAiResponse());

    PlantChatResponse first =
        plantChatService.chat(7L, 21L, new PlantChatRequest("잎 끝이 왜 갈색인가요?", null));
    PlantChatResponse second =
        plantChatService.chat(
            7L, 21L, new PlantChatRequest("말씀하신 첫 번째 행동을 더 설명해 주세요.", first.conversationId()));

    assertThat(second.conversationId()).isEqualTo(first.conversationId());
    ArgumentCaptor<AiRequest> captor = ArgumentCaptor.forClass(AiRequest.class);
    verify(aiClient, times(2)).generate(captor.capture());
    assertThat(captor.getAllValues().get(1).userPrompt())
        .contains("\"role\":\"USER\"")
        .contains("잎 끝이 왜 갈색인가요?")
        .contains("\"role\":\"ASSISTANT\"")
        .contains("과습과 건조가 반복됐을 가능성이 있어요.")
        .contains("권장 행동: 겉흙 2cm가 마른 뒤 충분히 물을 주세요.")
        .contains("말씀하신 첫 번째 행동을 더 설명해 주세요.");
  }

  @Test
  void rejectsQuestionBeforeLoadingPlantContextWhenInputGuardFails() {
    PlantChatRequest request = new PlantChatRequest(" ", null);
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
  void rejectsSemanticallyOffTopicQuestionAfterExactlyOneMeteredAiCall() throws Exception {
    given(growthContextQuery.getJournalHistoryContext(7L, 21L)).willReturn(growthContext());
    given(aiClient.generate(any(AiRequest.class)))
        .willReturn(
            new AiResponse(
                "resp-off-topic",
                "text-model",
                objectMapper.readTree(
                    """
                    {
                      "scopeDecision": "REFUSE",
                      "scopeIntent": "NONE",
                      "answer": "",
                      "recommendedActions": [],
                      "additionalChecks": []
                    }
                    """)));

    assertThatThrownBy(
            () ->
                plantChatService.chat(
                    7L, 21L, new PlantChatRequest("바질을 먹어야 하는데 원숭이 키우는 법을 알려줘", null)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.AI_CHAT_TOPIC_NOT_ALLOWED));

    verify(requestGuard).checkRateLimit(7L, AiFeature.PLANT_CHAT);
    verify(aiClient, times(1)).generate(any(AiRequest.class));
  }

  @Test
  void rejectsDifferentPlantWithDedicatedErrorWithoutSavingItToConversation() throws Exception {
    given(growthContextQuery.getJournalHistoryContext(7L, 21L)).willReturn(growthContext());
    given(aiClient.generate(any(AiRequest.class)))
        .willReturn(validAiResponse(), otherPlantAiResponse(), secondValidAiResponse());

    PlantChatResponse first =
        plantChatService.chat(7L, 21L, new PlantChatRequest("바질 잎 끝이 왜 갈색인가요?", null));
    String differentPlantQuestion = "원숭이꼬리선인장은 물을 얼마나 자주 줘야 하나요?";

    assertThatThrownBy(
            () ->
                plantChatService.chat(
                    7L, 21L, new PlantChatRequest(differentPlantQuestion, first.conversationId())))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> {
              assertThat(exception.getErrorCode())
                  .isEqualTo(ErrorCode.AI_CHAT_SELECTED_PLANT_MISMATCH);
              assertThat(exception.getDetails()).containsEntry("selectedSpeciesName", "바질");
            });

    PlantChatResponse resumed =
        plantChatService.chat(
            7L, 21L, new PlantChatRequest("말씀하신 첫 번째 행동을 더 설명해 주세요.", first.conversationId()));

    assertThat(resumed.conversationId()).isEqualTo(first.conversationId());
    ArgumentCaptor<AiRequest> captor = ArgumentCaptor.forClass(AiRequest.class);
    verify(aiClient, times(3)).generate(captor.capture());
    assertThat(captor.getAllValues().get(2).userPrompt())
        .contains("바질 잎 끝이 왜 갈색인가요?")
        .doesNotContain(differentPlantQuestion);
    verify(requestGuard, times(3)).checkRateLimit(7L, AiFeature.PLANT_CHAT);
  }

  @Test
  void doesNotConsumeRateLimitWhenProfileIsNotOwned() {
    doThrow(new BusinessException(ErrorCode.PLANT_PROFILE_NOT_FOUND))
        .when(growthContextQuery)
        .verifyOwnership(7L, 99L);

    assertThatThrownBy(
            () -> plantChatService.chat(7L, 99L, new PlantChatRequest("이 식물은 괜찮나요?", null)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLANT_PROFILE_NOT_FOUND));

    verify(requestGuard, never()).checkRateLimit(any(), any());
    verify(growthContextQuery, never()).getJournalHistoryContext(7L, 99L);
    verifyNoInteractions(aiClient);
  }

  @Test
  void rejectsUnknownConversationBeforeConsumingRateLimit() {
    assertThatThrownBy(
            () ->
                plantChatService.chat(
                    7L, 21L, new PlantChatRequest("이어서 알려주세요.", UUID.randomUUID())))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.AI_CHAT_CONVERSATION_INVALID));

    verify(requestGuard, never()).checkRateLimit(any(), any());
    verify(growthContextQuery, never()).getJournalHistoryContext(7L, 21L);
    verifyNoInteractions(aiClient);
  }

  @Test
  void doesNotCallAiWhenRateLimitIsExceeded() {
    doThrow(new BusinessException(ErrorCode.COMMON_RATE_LIMITED))
        .when(requestGuard)
        .checkRateLimit(7L, AiFeature.PLANT_CHAT);

    assertThatThrownBy(
            () -> plantChatService.chat(7L, 21L, new PlantChatRequest("물을 언제 줄까요?", null)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMON_RATE_LIMITED));

    verify(growthContextQuery, never()).getJournalHistoryContext(7L, 21L);
    verifyNoInteractions(aiClient);
  }

  @Test
  void rejectsSemanticallyInvalidAiResponse() throws Exception {
    given(growthContextQuery.getJournalHistoryContext(7L, 21L)).willReturn(growthContext());
    given(aiClient.generate(any(AiRequest.class)))
        .willReturn(
            new AiResponse(
                "resp-invalid",
                "text-model",
                objectMapper.readTree(
                    """
                    {
                      "scopeDecision": "ANSWER",
                      "scopeIntent": "CARE",
                      "answer": "답변입니다.",
                      "recommendedActions": [],
                      "additionalChecks": []
                    }
                    """)));

    assertThatThrownBy(
            () -> plantChatService.chat(7L, 21L, new PlantChatRequest("이 식물의 관리 방법이 궁금합니다.", null)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AI_RESPONSE_INVALID));
  }

  @Test
  void rejectsAiResponseWithoutScopeDecision() throws Exception {
    given(growthContextQuery.getJournalHistoryContext(7L, 21L)).willReturn(growthContext());
    given(aiClient.generate(any(AiRequest.class)))
        .willReturn(
            new AiResponse(
                "resp-missing-scope",
                "text-model",
                objectMapper.readTree(
                    """
                    {
                      "answer": "범위 판정이 없는 답변",
                      "recommendedActions": ["행동"],
                      "additionalChecks": []
                    }
                    """)));

    assertThatThrownBy(
            () -> plantChatService.chat(7L, 21L, new PlantChatRequest("바질 물주기가 궁금합니다.", null)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AI_RESPONSE_INVALID));
  }

  @Test
  void requestsMoreContextForUncertainScopeWithoutExposingOrSavingGeneratedContent()
      throws Exception {
    given(growthContextQuery.getJournalHistoryContext(7L, 21L)).willReturn(growthContext());
    given(aiClient.generate(any(AiRequest.class)))
        .willReturn(
            validAiResponse(),
            new AiResponse(
                "resp-out-of-scope",
                "text-model",
                objectMapper.readTree(
                    """
                    {
                      "scopeDecision": "UNCERTAIN",
                      "scopeIntent": "NONE",
                      "answer": "노출되면 안 되는 내용",
                      "recommendedActions": [],
                      "additionalChecks": []
                    }
                    """)),
            secondValidAiResponse());

    PlantChatResponse first =
        plantChatService.chat(7L, 21L, new PlantChatRequest("바질 잎 끝이 왜 갈색인가요?", null));
    String rejectedQuestion = "바질 이야기는 그만하고 임의의 다른 내용을 알려주세요.";

    assertThatThrownBy(
            () ->
                plantChatService.chat(
                    7L, 21L, new PlantChatRequest(rejectedQuestion, first.conversationId())))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AI_CHAT_CONTEXT_REQUIRED));

    PlantChatResponse resumed =
        plantChatService.chat(
            7L, 21L, new PlantChatRequest("말씀하신 첫 번째 행동을 더 설명해 주세요.", first.conversationId()));

    assertThat(resumed.conversationId()).isEqualTo(first.conversationId());
    ArgumentCaptor<AiRequest> captor = ArgumentCaptor.forClass(AiRequest.class);
    verify(aiClient, times(3)).generate(captor.capture());
    assertThat(captor.getAllValues().get(2).userPrompt())
        .contains("바질 잎 끝이 왜 갈색인가요?")
        .doesNotContain(rejectedQuestion)
        .doesNotContain("노출되면 안 되는 내용");
    verify(requestGuard, times(3)).checkRateLimit(7L, AiFeature.PLANT_CHAT);
  }

  @Test
  void rejectsUnsafePrescriptionForGeneralFallbackKnowledge() throws Exception {
    given(growthContextQuery.getJournalHistoryContext(7L, 21L)).willReturn(growthContext());
    given(knowledgeRetriever.retrieve(any()))
        .willReturn(PlantCareKnowledge.fallback("바질", "바질", "test-retriever-v1"));
    given(aiClient.generate(any(AiRequest.class)))
        .willReturn(
            new AiResponse(
                "resp-unsafe",
                "text-model",
                objectMapper.readTree(
                    """
                    {
                      "scopeDecision": "ANSWER",
                      "scopeIntent": "CARE",
                      "answer": "해충일 가능성이 있습니다.",
                      "recommendedActions": ["살충제를 잎에 뿌리세요."],
                      "additionalChecks": ["잎 뒷면을 확인하세요."]
                    }
                    """)));

    assertThatThrownBy(
            () ->
                plantChatService.chat(
                    7L, 21L, new PlantChatRequest("잎의 벌레를 어떻게 없애나요?", null)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AI_RESPONSE_INVALID));
  }

  @Test
  void propagatesAiProviderFailureWithoutRetrying() {
    given(growthContextQuery.getJournalHistoryContext(7L, 21L)).willReturn(growthContext());
    given(aiClient.generate(any(AiRequest.class)))
        .willThrow(new BusinessException(ErrorCode.AI_PROVIDER_UNAVAILABLE));

    assertThatThrownBy(
            () -> plantChatService.chat(7L, 21L, new PlantChatRequest("이 식물의 잎 상태가 궁금합니다.", null)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AI_PROVIDER_UNAVAILABLE));

    verify(aiClient, times(1)).generate(any(AiRequest.class));
  }

  private PlantGrowthContextResponse growthContext() {
    return new PlantGrowthContextResponse(
        21L,
        "바질이",
        LocalDate.of(2026, 7, 1),
        PlantStatus.GROWING,
        "바질",
        List.of(
            new RecentJournal(31L, LocalDate.of(2026, 8, 8), "새 잎이 조금 말렸어요."),
            new RecentJournal(30L, LocalDate.of(2026, 8, 5), "물을 충분히 줬어요.")));
  }

  private PlantGrowthContextResponse longJournalHistoryGrowthContext() {
    return new PlantGrowthContextResponse(
        21L,
        "바질이",
        LocalDate.of(2026, 7, 1),
        PlantStatus.GROWING,
        "바질",
        List.of(
            new RecentJournal(37L, LocalDate.of(2026, 8, 8), "오늘은 새 잎이 한 장 나왔어요."),
            new RecentJournal(36L, LocalDate.of(2026, 8, 7), "물을 주고 창가로 옮겼어요."),
            new RecentJournal(35L, LocalDate.of(2026, 8, 6), "줄기가 조금 자랐어요."),
            new RecentJournal(34L, LocalDate.of(2026, 8, 5), "흙 표면이 말라 물을 줬어요."),
            new RecentJournal(33L, LocalDate.of(2026, 8, 4), "잎 색은 대체로 초록색이에요."),
            new RecentJournal(32L, LocalDate.of(2026, 7, 3), "노란 잎이 생겨 물주기 간격을 조절했어요."),
            new RecentJournal(31L, LocalDate.of(2026, 7, 2), "화분을 닦고 주변을 정리했어요.")));
  }

  private PlantGrowthContextResponse compoundSpeciesGrowthContext() {
    return new PlantGrowthContextResponse(
        21L,
        "꼬리 선인장",
        LocalDate.of(2026, 7, 1),
        PlantStatus.GROWING,
        "원숭이꼬리선인장",
        List.of(new RecentJournal(31L, LocalDate.of(2026, 8, 8), "줄기가 조금 자랐어요.")));
  }

  private AiResponse validAiResponse() throws Exception {
    return new AiResponse(
        "resp-1",
        "text-model",
        objectMapper.readTree(
            """
            {
              "scopeDecision": "ANSWER",
              "scopeIntent": "GROWTH_OBSERVATION",
              "answer": " 과습과 건조가 반복됐을 가능성이 있어요. ",
              "recommendedActions": [" 겉흙 2cm가 마른 뒤 충분히 물을 주세요. "],
              "additionalChecks": [" 화분 배수구가 막히지 않았는지 확인하세요. "]
            }
            """));
  }

  private AiResponse secondValidAiResponse() throws Exception {
    return new AiResponse(
        "resp-2",
        "text-model",
        objectMapper.readTree(
            """
            {
              "scopeDecision": "ANSWER",
              "scopeIntent": "DIRECT_FOLLOW_UP",
              "answer": "첫 번째 행동을 더 자세히 설명해 드릴게요.",
              "recommendedActions": ["손가락으로 겉흙 아래까지 확인해 주세요."],
              "additionalChecks": []
            }
            """));
  }

  private PlantCareKnowledge verifiedKnowledge(String speciesName) {
    return PlantCareKnowledge.verified(
        speciesName,
        speciesName,
        "test-retriever-v1",
        List.of(
            new PlantCareEvidence(
                "official-basil",
                "공식 바질 재배 문서",
                "https://example.test/basil",
                "2026-08-21",
                "바질은 햇빛과 통풍이 좋은 곳에서 기릅니다.")));
  }

  private AiResponse otherPlantAiResponse() throws Exception {
    return new AiResponse(
        "resp-other-plant",
        "text-model",
        objectMapper.readTree(
            """
            {
              "scopeDecision": "OTHER_PLANT",
              "scopeIntent": "NONE",
              "answer": "",
              "recommendedActions": [],
              "additionalChecks": []
            }
            """));
  }
}
