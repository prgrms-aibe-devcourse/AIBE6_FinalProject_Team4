package com.kiwobollae.api.ai.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.kiwobollae.api.ai.analysis.JournalImageAnalysisStore.Claim;
import com.kiwobollae.api.ai.analysis.dto.JournalImageAnalysisResponse;
import com.kiwobollae.api.ai.client.AiClient;
import com.kiwobollae.api.ai.client.AiImageInput;
import com.kiwobollae.api.ai.client.AiModelRole;
import com.kiwobollae.api.ai.client.AiRequest;
import com.kiwobollae.api.ai.client.AiResponse;
import com.kiwobollae.api.ai.policy.AiFeature;
import com.kiwobollae.api.ai.policy.AiRequestGuard;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import com.kiwobollae.api.journal.dto.response.JournalImageAnalysisContext;
import com.kiwobollae.api.journal.service.JournalImageAnalysisContextQuery;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class JournalImageAnalysisServiceTest {

  private static final String IMAGE_HASH = "a".repeat(64);
  private static final String OTHER_IMAGE_HASH = "b".repeat(64);
  private static final LocalDateTime ANALYZED_AT = LocalDateTime.of(2026, 8, 13, 10, 30);
  private static final Clock FIXED_KST_CLOCK =
      Clock.fixed(Instant.parse("2026-08-13T01:30:00Z"), ZoneId.of("Asia/Seoul"));
  private static final String RESULT_JSON =
      """
      {
        "imageQuality": "CLEAR",
        "condition": "NEEDS_ATTENTION",
        "summary": "아래쪽 잎 한 장의 끝이 옅게 변한 모습이 보여요.",
        "observations": ["아래쪽 잎 끝부분의 색이 주변보다 옅어요."],
        "possibleCauses": ["물주기 간격이나 자연스러운 오래된 잎 변화일 수 있어요."],
        "recommendedActions": ["흙이 마른 정도를 먼저 확인해 주세요."],
        "additionalChecks": ["잎 뒷면에 작은 점이나 벌레가 있는지 확인해 주세요."]
      }
      """;

  @Mock private JournalImageAnalysisContextQuery contextQuery;
  @Mock private JournalImageAnalysisStore store;
  @Mock private AiClient aiClient;
  @Mock private AiRequestGuard requestGuard;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void reusesCompletedResultWithoutConsumingRateLimitOrCallingAi() {
    JournalImageAnalysis completed = completedAnalysis(IMAGE_HASH, RESULT_JSON);
    given(store.claim(31L, IMAGE_HASH)).willReturn(Claim.completed(completed));

    JournalImageAnalysisResponse response = service().analyze(7L, 31L, IMAGE_HASH);

    assertThat(response.imageHash()).isEqualTo(IMAGE_HASH);
    assertThat(response.summary()).contains("아래쪽 잎");
    assertThat(response.analyzedAt()).isEqualTo(ANALYZED_AT);
    verify(contextQuery).validateAnalysisTarget(7L, 31L, IMAGE_HASH);
    verify(contextQuery, never()).getAnalysisContext(any(), any(), anyInt());
    verifyNoInteractions(requestGuard, aiClient);
  }

  @Test
  void analyzesOnlyAttachedSavedImageWithVisionModelAndJournalContext() throws Exception {
    given(contextQuery.getAnalysisContext(7L, 31L, 5)).willReturn(context());
    given(store.claim(31L, IMAGE_HASH)).willReturn(Claim.owner("claim-token"));
    given(aiClient.generate(any(AiRequest.class)))
        .willReturn(
            new AiResponse("response-id", "vision-model", objectMapper.readTree(RESULT_JSON)));
    given(
            store.complete(
                eq(31L),
                eq(IMAGE_HASH),
                eq("claim-token"),
                any(String.class),
                eq("vision-model"),
                eq(ANALYZED_AT)))
        .willAnswer(
            invocation -> completedAnalysis(IMAGE_HASH, invocation.getArgument(3, String.class)));

    JournalImageAnalysisResponse response = service().analyze(7L, 31L, IMAGE_HASH);

    assertThat(response.condition().name()).isEqualTo("NEEDS_ATTENTION");
    verify(requestGuard).checkRateLimit(7L, AiFeature.JOURNAL_IMAGE_ANALYSIS);
    ArgumentCaptor<AiRequest> requestCaptor = ArgumentCaptor.forClass(AiRequest.class);
    verify(aiClient).generate(requestCaptor.capture());
    AiRequest request = requestCaptor.getValue();
    assertThat(request.modelRole()).isEqualTo(AiModelRole.VISION);
    assertThat(request.images())
        .containsExactly(
            new AiImageInput("/api/v1/journals/images/7/basil.webp", AiImageInput.Detail.HIGH));
    assertThat(request.userPrompt()).contains("바질이").contains("스위트 바질").contains("새 잎이 두 장 자랐어요");
  }

  @Test
  void rejectsImageHashThatIsNotAttachedBeforeClaimingOrCallingAi() {
    willThrow(new BusinessException(ErrorCode.AI_IMAGE_ANALYSIS_IMAGE_NOT_FOUND))
        .given(contextQuery)
        .validateAnalysisTarget(7L, 31L, OTHER_IMAGE_HASH);

    assertThatThrownBy(() -> service().analyze(7L, 31L, OTHER_IMAGE_HASH))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.AI_IMAGE_ANALYSIS_IMAGE_NOT_FOUND));

    verifyNoInteractions(store, requestGuard, aiClient);
  }

  @Test
  void rejectsConcurrentAnalysisWithoutCallingAi() {
    given(store.claim(31L, IMAGE_HASH)).willReturn(Claim.inProgress());

    assertThatThrownBy(() -> service().analyze(7L, 31L, IMAGE_HASH))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.AI_IMAGE_ANALYSIS_IN_PROGRESS));

    verify(contextQuery, never()).getAnalysisContext(any(), any(), anyInt());
    verifyNoInteractions(requestGuard, aiClient);
  }

  @Test
  void marksClaimFailedWhenFullContextLookupFailsAfterClaiming() {
    given(store.claim(31L, IMAGE_HASH)).willReturn(Claim.owner("claim-token"));
    given(contextQuery.getAnalysisContext(7L, 31L, 5))
        .willThrow(new BusinessException(ErrorCode.JOURNAL_NOT_FOUND));

    assertThatThrownBy(() -> service().analyze(7L, 31L, IMAGE_HASH))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.JOURNAL_NOT_FOUND));

    verify(store).fail(31L, IMAGE_HASH, "claim-token");
    verifyNoInteractions(requestGuard, aiClient);
  }

  @Test
  void marksClaimFailedWhenAiReturnsInvalidStructuredResult() throws Exception {
    given(contextQuery.getAnalysisContext(7L, 31L, 5)).willReturn(context());
    given(store.claim(31L, IMAGE_HASH)).willReturn(Claim.owner("claim-token"));
    given(aiClient.generate(any(AiRequest.class)))
        .willReturn(
            new AiResponse(
                "response-id",
                "vision-model",
                objectMapper.readTree(
                    """
                    {"imageQuality":"UNUSABLE","condition":"HEALTHY"}
                    """)));

    assertThatThrownBy(() -> service().analyze(7L, 31L, IMAGE_HASH))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AI_RESPONSE_INVALID));

    verify(store).fail(31L, IMAGE_HASH, "claim-token");
    verify(store, never()).complete(eq(31L), eq(IMAGE_HASH), any(), any(), any(), any());
  }

  @Test
  void listsOnlyCompletedResultsForImagesStillAttachedToJournal() {
    given(contextQuery.getAnalysisContext(7L, 31L, 5)).willReturn(context());
    given(store.findCompleted(31L))
        .willReturn(
            List.of(
                completedAnalysis(OTHER_IMAGE_HASH, RESULT_JSON),
                completedAnalysis(IMAGE_HASH, RESULT_JSON)));

    List<JournalImageAnalysisResponse> responses = service().getCompleted(7L, 31L);

    assertThat(responses)
        .extracting(JournalImageAnalysisResponse::imageHash)
        .containsExactly(IMAGE_HASH);
    verifyNoInteractions(aiClient, requestGuard);
  }

  private JournalImageAnalysisService service() {
    return new JournalImageAnalysisService(
        contextQuery, store, aiClient, requestGuard, objectMapper, FIXED_KST_CLOCK);
  }

  private JournalImageAnalysisContext context() {
    return new JournalImageAnalysisContext(
        31L,
        21L,
        "바질이",
        "스위트 바질",
        LocalDate.of(2026, 8, 13),
        "아래 잎 끝이 조금 옅어 보여요.",
        List.of(
            new JournalImageAnalysisContext.Image(
                "/api/v1/journals/images/7/basil.webp", IMAGE_HASH, true)),
        List.of(
            new JournalImageAnalysisContext.RecentJournal(
                LocalDate.of(2026, 8, 12), "새 잎이 두 장 자랐어요.")));
  }

  private JournalImageAnalysis completedAnalysis(String imageHash, String resultJson) {
    return JournalImageAnalysis.builder()
        .journalId(31L)
        .imageHash(imageHash)
        .status(JournalImageAnalysisStatus.COMPLETED)
        .resultJson(resultJson)
        .model("vision-model")
        .createdAt(ANALYZED_AT.minusMinutes(1))
        .updatedAt(ANALYZED_AT)
        .build();
  }
}
