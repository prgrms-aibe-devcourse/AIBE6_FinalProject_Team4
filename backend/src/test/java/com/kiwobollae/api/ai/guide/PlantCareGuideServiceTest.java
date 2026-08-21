package com.kiwobollae.api.ai.guide;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.kiwobollae.api.ai.client.AiClient;
import com.kiwobollae.api.ai.client.AiModelRole;
import com.kiwobollae.api.ai.client.AiRequest;
import com.kiwobollae.api.ai.client.AiResponse;
import com.kiwobollae.api.ai.guide.dto.PlantCareGuide;
import com.kiwobollae.api.ai.guide.dto.PlantCareGuideSchema;
import com.kiwobollae.api.ai.knowledge.PlantCareAdviceSafetyPolicy;
import com.kiwobollae.api.ai.knowledge.PlantCareEvidence;
import com.kiwobollae.api.ai.knowledge.PlantCareEvidenceStatus;
import com.kiwobollae.api.ai.knowledge.PlantCareEvidenceScope;
import com.kiwobollae.api.ai.knowledge.PlantCareKnowledge;
import com.kiwobollae.api.ai.knowledge.PlantCareKnowledgeMetadataCodec;
import com.kiwobollae.api.ai.knowledge.PlantCareKnowledgeQuery;
import com.kiwobollae.api.ai.knowledge.PlantCareKnowledgeRetriever;
import com.kiwobollae.api.ai.knowledge.PlantCareSpeciesMatchType;
import com.kiwobollae.api.ai.policy.AiFeature;
import com.kiwobollae.api.ai.policy.AiRequestGuard;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class PlantCareGuideServiceTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final Clock FIXED_KST_CLOCK =
      Clock.fixed(Instant.parse("2026-08-05T01:00:00Z"), KST);

  private static final String GUIDE_JSON =
      """
      {
        "difficulty": "초급",
        "difficultyReason": "생육이 빠르고 병해가 적습니다.",
        "environment": {
          "sunlight": "하루 4시간 이상 밝은 곳",
          "watering": "겉흙이 마르면 충분히",
          "temperature": "18~25도"
        },
        "stages": [
          {"name": "파종", "guide": "씨앗을 1cm 깊이로 심습니다."},
          {"name": "새싹", "guide": "겉흙이 마르지 않게 유지합니다."},
          {"name": "성장", "guide": "2주에 한 번 액체 비료를 줍니다."},
          {"name": "수확", "guide": "바깥 잎부터 잘라 씁니다."}
        ],
        "pitfalls": [
          {"problem": "잎이 노랗게 변합니다.", "action": "물주기 간격을 늘리세요."},
          {"problem": "줄기가 길게 웃자랍니다.", "action": "더 밝은 곳으로 옮기세요."}
        ],
        "harvestTarget": "파종 후 약 5주"
      }
      """;

  @Mock private PlantCareKnowledgeRetriever knowledgeRetriever;
  @Mock private PlantCareGuideCacheRepository cacheRepository;
  @Mock private PlantCareGuideCacheWriter cacheWriter;
  @Mock private AiClient aiClient;
  @Mock private AiRequestGuard requestGuard;
  @Mock private PlantCareGuideGenerationLockStore generationLockStore;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final PlantCareKnowledgeMetadataCodec knowledgeMetadataCodec =
      new PlantCareKnowledgeMetadataCodec(objectMapper);
  private final PlantCareAdviceSafetyPolicy adviceSafetyPolicy = new PlantCareAdviceSafetyPolicy();

  @BeforeEach
  void acquireGenerationLeaseByDefault() {
    lenient().when(knowledgeRetriever.retrieve(any())).thenReturn(knowledge());
    lenient()
        .when(generationLockStore.tryAcquire(any(PlantCareGuideGenerationKey.class)))
        .thenAnswer(
            invocation ->
                Optional.of(
                    new PlantCareGuideGenerationLockStore.Lease(
                        invocation.getArgument(0), new Object())));
  }

  private PlantCareGuideService service() {
    return new PlantCareGuideService(
        knowledgeRetriever,
        knowledgeMetadataCodec,
        adviceSafetyPolicy,
        cacheRepository,
        cacheWriter,
        aiClient,
        requestGuard,
        generationLockStore,
        objectMapper,
        FIXED_KST_CLOCK);
  }

  // 이 기능의 핵심 이점. 저장본이 있으면 AI를 부르지 않고, 따라서 호출 제한도 소모하지 않는다.
  @Test
  void servesStoredGuideWithoutCallingAiOrConsumingRateLimit() {
    given(
            cacheRepository.findBySpeciesNameAndGuideVersionAndSourceContextHash(
                eq("청상추"), eq(PlantCareGuideSchema.VERSION), anyString()))
        .willReturn(Optional.of(cache("청상추")));

    PlantCareGuide guide = service().getGuideBySpeciesName(7L, "청상추");

    assertThat(guide.cached()).isTrue();
    assertThat(guide.speciesName()).isEqualTo("청상추");
    assertThat(guide.difficulty()).isEqualTo("초급");
    assertThat(guide.stages()).hasSize(4);
    assertThat(guide.pitfalls()).hasSize(2);
    assertThat(guide.grounding().status()).isEqualTo(PlantCareEvidenceStatus.VERIFIED);
    assertThat(guide.grounding().sources()).singleElement();
    verifyNoInteractions(aiClient);
    verifyNoInteractions(requestGuard);
    verifyNoInteractions(generationLockStore);
    verify(cacheRepository, never()).save(any());
  }

  @Test
  void generatesAndStoresGuideOnCacheMiss() {
    given(
            cacheRepository.findBySpeciesNameAndGuideVersionAndSourceContextHash(
                eq("청상추"), eq(PlantCareGuideSchema.VERSION), anyString()))
        .willReturn(Optional.empty());
    given(aiClient.generate(any(AiRequest.class))).willReturn(aiResponse());

    PlantCareGuide guide = service().getGuideBySpeciesName(7L, "청상추");

    assertThat(guide.cached()).isFalse();
    assertThat(guide.harvestTarget()).isEqualTo("파종 후 약 5주");
    verify(requestGuard).checkRateLimit(7L, AiFeature.PLANT_CARE_GUIDE);

    ArgumentCaptor<PlantCareGuideCache> cacheCaptor =
        ArgumentCaptor.forClass(PlantCareGuideCache.class);
    verify(cacheWriter).save(cacheCaptor.capture());
    assertThat(cacheCaptor.getValue().getSpeciesName()).isEqualTo("청상추");
    assertThat(cacheCaptor.getValue().getEvidenceStatus())
        .isEqualTo(PlantCareEvidenceStatus.VERIFIED);
    assertThat(cacheCaptor.getValue().getEvidenceSourcesJson()).contains("nongsaro-218964");
    assertThat(cacheCaptor.getValue().getGuideVersion()).isEqualTo(PlantCareGuideSchema.VERSION);
    assertThat(cacheCaptor.getValue().getModel()).isEqualTo("text-model");
    assertThat(cacheCaptor.getValue().getCreatedAt())
        .isEqualTo(LocalDateTime.of(2026, 8, 5, 10, 0));
  }

  // 검증 재배 자료는 환각을 줄이는 근거로 넣는다. 텍스트 모델이므로 이미지 입력은 없어야 한다.
  @Test
  void groundsPromptOnVerifiedKnowledge() {
    given(
            cacheRepository.findBySpeciesNameAndGuideVersionAndSourceContextHash(
                anyString(), anyInt(), anyString()))
        .willReturn(Optional.empty());
    given(aiClient.generate(any(AiRequest.class))).willReturn(aiResponse());

    service().getGuideBySpeciesName(7L, "청상추");

    ArgumentCaptor<AiRequest> requestCaptor = ArgumentCaptor.forClass(AiRequest.class);
    verify(aiClient).generate(requestCaptor.capture());
    AiRequest request = requestCaptor.getValue();
    assertThat(request.modelRole()).isEqualTo(AiModelRole.TEXT);
    assertThat(request.images()).isEmpty();
    assertThat(request.userPrompt())
        .contains("청상추")
        .contains("VERIFIED")
        .contains("검증 재배 자료")
        .contains("서늘하고 밝은 곳");
    assertThat(request.responseSchema().name()).isEqualTo("plant_care_guide");

    ArgumentCaptor<PlantCareKnowledgeQuery> queryCaptor =
        ArgumentCaptor.forClass(PlantCareKnowledgeQuery.class);
    verify(knowledgeRetriever).retrieve(queryCaptor.capture());
    assertThat(queryCaptor.getValue().speciesName()).isEqualTo("청상추");
  }

  @Test
  void generatesGuideForNewSpeciesWithoutVerifiedKnowledge() {
    given(knowledgeRetriever.retrieve(any()))
        .willReturn(PlantCareKnowledge.fallback("고수", "고수", "test-retriever-v1"));
    given(
            cacheRepository.findBySpeciesNameAndGuideVersionAndSourceContextHash(
                eq("고수"), eq(PlantCareGuideSchema.VERSION), anyString()))
        .willReturn(Optional.empty());
    given(aiClient.generate(any(AiRequest.class))).willReturn(safeFallbackAiResponse());

    PlantCareGuide guide = service().getGuideBySpeciesName(7L, "고수");

    assertThat(guide.cached()).isFalse();
    verify(requestGuard).checkRateLimit(7L, AiFeature.PLANT_CARE_GUIDE);

    ArgumentCaptor<AiRequest> requestCaptor = ArgumentCaptor.forClass(AiRequest.class);
    verify(aiClient).generate(requestCaptor.capture());
    assertThat(requestCaptor.getValue().userPrompt())
        .contains("식물 종: 고수")
        .contains("GENERAL_FALLBACK")
        .contains("fallbackSafetyPolicy");
    assertThat(guide.grounding().status()).isEqualTo(PlantCareEvidenceStatus.GENERAL_FALLBACK);
    assertThat(guide.grounding().sources()).isEmpty();
    verify(cacheWriter).save(any(PlantCareGuideCache.class));
  }

  @Test
  void rejectsUnsafePrescriptionWhenOfficialEvidenceIsUnavailable() {
    given(knowledgeRetriever.retrieve(any()))
        .willReturn(PlantCareKnowledge.fallback("고수", "고수", "test-retriever-v1"));
    given(
            cacheRepository.findBySpeciesNameAndGuideVersionAndSourceContextHash(
                eq("고수"), eq(PlantCareGuideSchema.VERSION), anyString()))
        .willReturn(Optional.empty());
    given(aiClient.generate(any(AiRequest.class))).willReturn(aiResponse());

    assertThatThrownBy(() -> service().getGuideBySpeciesName(7L, "고수"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AI_RESPONSE_INVALID));

    verify(cacheWriter, never()).save(any());
  }

  @Test
  void usesBaseSpeciesCacheAndDisclosesScopeForRegisteredCultivar() {
    PlantCareKnowledge cultivarKnowledge =
        PlantCareKnowledge.verified(
            "설향딸기",
            "딸기",
            PlantCareSpeciesMatchType.CULTIVAR,
            "test-retriever-v1",
            knowledge().evidence());
    given(knowledgeRetriever.retrieve(any())).willReturn(cultivarKnowledge);
    given(
            cacheRepository.findBySpeciesNameAndGuideVersionAndSourceContextHash(
                "딸기", PlantCareGuideSchema.VERSION, cultivarKnowledge.sourceContextHash()))
        .willReturn(Optional.of(cache("딸기")));

    PlantCareGuide guide = service().getGuideBySpeciesName(7L, "설향 딸기");

    assertThat(guide.speciesName()).isEqualTo("설향딸기");
    assertThat(guide.cached()).isTrue();
    assertThat(guide.grounding().scope()).isEqualTo(PlantCareEvidenceScope.BASE_SPECIES);
    assertThat(guide.grounding().resolvedSpeciesName()).isEqualTo("딸기");
    verifyNoInteractions(aiClient, requestGuard, generationLockStore);
  }

  @Test
  void generatesCultivarGuideAsBaseSpeciesAndStoresSharedCacheKey() {
    PlantCareKnowledge cultivarKnowledge =
        PlantCareKnowledge.verified(
            "설향딸기",
            "딸기",
            PlantCareSpeciesMatchType.CULTIVAR,
            "test-retriever-v1",
            knowledge().evidence());
    given(knowledgeRetriever.retrieve(any())).willReturn(cultivarKnowledge);
    given(
            cacheRepository.findBySpeciesNameAndGuideVersionAndSourceContextHash(
                "딸기", PlantCareGuideSchema.VERSION, cultivarKnowledge.sourceContextHash()))
        .willReturn(Optional.empty());
    given(aiClient.generate(any(AiRequest.class))).willReturn(aiResponse());

    service().getGuideBySpeciesName(7L, "설향딸기");

    ArgumentCaptor<AiRequest> requestCaptor = ArgumentCaptor.forClass(AiRequest.class);
    verify(aiClient).generate(requestCaptor.capture());
    assertThat(requestCaptor.getValue().userPrompt())
        .contains("식물 종: 딸기")
        .contains("BASE_SPECIES")
        .doesNotContain("식물 종: 설향딸기");
    ArgumentCaptor<PlantCareGuideCache> cacheCaptor =
        ArgumentCaptor.forClass(PlantCareGuideCache.class);
    verify(cacheWriter).save(cacheCaptor.capture());
    assertThat(cacheCaptor.getValue().getSpeciesName()).isEqualTo("딸기");
  }

  @Test
  void regeneratesGuideWhenOfficialEvidenceContentHashChanges() {
    PlantCareKnowledge oldKnowledge = knowledgeWithContent("서늘한 곳에서 기릅니다.");
    PlantCareKnowledge changedKnowledge = knowledgeWithContent("서늘하고 밝은 곳에서 기릅니다.");
    given(knowledgeRetriever.retrieve(any())).willReturn(changedKnowledge);
    lenient()
        .when(
            cacheRepository.findBySpeciesNameAndGuideVersionAndSourceContextHash(
                "청상추", PlantCareGuideSchema.VERSION, oldKnowledge.sourceContextHash()))
        .thenReturn(Optional.of(cache("청상추")));
    given(
            cacheRepository.findBySpeciesNameAndGuideVersionAndSourceContextHash(
                "청상추", PlantCareGuideSchema.VERSION, changedKnowledge.sourceContextHash()))
        .willReturn(Optional.empty());
    given(aiClient.generate(any(AiRequest.class))).willReturn(aiResponse());

    PlantCareGuide guide = service().getGuideBySpeciesName(7L, "청상추");

    assertThat(guide.cached()).isFalse();
    verify(aiClient).generate(any(AiRequest.class));
    ArgumentCaptor<PlantCareGuideCache> cacheCaptor =
        ArgumentCaptor.forClass(PlantCareGuideCache.class);
    verify(cacheWriter).save(cacheCaptor.capture());
    assertThat(cacheCaptor.getValue().getSourceContextHash())
        .isEqualTo(changedKnowledge.sourceContextHash())
        .isNotEqualTo(oldKnowledge.sourceContextHash());
  }

  // 캐시 키는 정규화된 이름이다. 공백을 전부 제거해 "스위트 바질"과 "스위트바질"을 같은 종으로 묶는다.
  @Test
  void normalizesSpeciesNameByRemovingWhitespace() {
    given(knowledgeRetriever.retrieve(any())).willReturn(knowledge("스위트바질", "바질"));
    given(
            cacheRepository.findBySpeciesNameAndGuideVersionAndSourceContextHash(
                eq("바질"), eq(PlantCareGuideSchema.VERSION), anyString()))
        .willReturn(Optional.of(cache("바질")));

    PlantCareGuide guide = service().getGuideBySpeciesName(7L, "  스위트   바질  ");

    assertThat(guide.speciesName()).isEqualTo("스위트바질");
  }

  // lease 만료 뒤의 극히 드문 중복 저장도 캐시 유니크 제약으로 안전하게 수습한다.
  @Test
  void fallsBackToExistingRowWhenConcurrentRequestStoredFirst() {
    given(
            cacheRepository.findBySpeciesNameAndGuideVersionAndSourceContextHash(
                eq("청상추"), eq(PlantCareGuideSchema.VERSION), anyString()))
        .willReturn(Optional.empty())
        .willReturn(Optional.empty())
        .willReturn(Optional.of(cache("청상추")));
    given(aiClient.generate(any(AiRequest.class))).willReturn(aiResponse());
    doThrow(new DataIntegrityViolationException("duplicate species_name"))
        .when(cacheWriter)
        .save(any(PlantCareGuideCache.class));

    PlantCareGuide guide = service().getGuideBySpeciesName(7L, "청상추");

    assertThat(guide.difficulty()).isEqualTo("초급");
    assertThat(guide.cached()).isTrue();
  }

  @Test
  void searchesCachedSpeciesNamesContainingQuery() {
    given(cacheRepository.findDistinctSpeciesNamesContaining("토마토"))
        .willReturn(List.of("방울토마토", "대추토마토"));

    List<String> results = service().searchSpeciesNames("토마토");

    assertThat(results).containsExactly("방울토마토", "대추토마토");
  }

  @Test
  void rejectsBlankSearchQuery() {
    assertThatThrownBy(() -> service().searchSpeciesNames("  "))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED));
    verifyNoInteractions(cacheRepository);
  }

  @Test
  void rejectsConcurrentCacheMissWithoutCallingAi() {
    given(
            cacheRepository.findBySpeciesNameAndGuideVersionAndSourceContextHash(
                eq("청상추"), eq(PlantCareGuideSchema.VERSION), anyString()))
        .willReturn(Optional.empty());
    given(generationLockStore.tryAcquire(any(PlantCareGuideGenerationKey.class)))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> service().getGuideBySpeciesName(7L, "청상추"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> {
              assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMON_DATA_CONFLICT);
              assertThat(exception.getMessage()).contains("생성하고 있습니다");
            });

    verifyNoInteractions(aiClient);
    verifyNoInteractions(requestGuard);
    verify(cacheWriter, never()).save(any());
    verify(generationLockStore, never()).release(any());
  }

  @Test
  void servesGuideSavedWhileGenerationLeaseWasAcquired() {
    given(
            cacheRepository.findBySpeciesNameAndGuideVersionAndSourceContextHash(
                eq("청상추"), eq(PlantCareGuideSchema.VERSION), anyString()))
        .willReturn(Optional.empty())
        .willReturn(Optional.of(cache("청상추")));

    PlantCareGuide guide = service().getGuideBySpeciesName(7L, "청상추");

    assertThat(guide.cached()).isTrue();
    verifyNoInteractions(aiClient);
    verifyNoInteractions(requestGuard);
    verify(generationLockStore).release(any(PlantCareGuideGenerationLockStore.Lease.class));
  }

  @Test
  void rejectsBlankSpeciesName() {
    given(knowledgeRetriever.retrieve(new PlantCareKnowledgeQuery("  ")))
        .willThrow(new IllegalArgumentException("식물 종 이름이 필요합니다."));

    assertThatThrownBy(() -> service().getGuideBySpeciesName(7L, "  "))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED));
    verifyNoInteractions(aiClient);
  }

  // 스키마를 바꿨는데 guide_version을 올리지 않으면 옛 저장본을 읽다 깨진다. 조용히 넘기지 않는다.
  @Test
  void rejectsStoredGuideThatNoLongerMatchesSchema() {
    given(
            cacheRepository.findBySpeciesNameAndGuideVersionAndSourceContextHash(
                eq("청상추"), anyInt(), anyString()))
        .willReturn(
            Optional.of(
                PlantCareGuideCache.builder()
                    .speciesName("청상추")
                    .sourceContextHash("test-hash")
                    .evidenceStatus(PlantCareEvidenceStatus.VERIFIED)
                    .evidenceSourcesJson(knowledgeMetadataCodec.serializeSources(knowledge()))
                    .guideVersion(PlantCareGuideSchema.VERSION)
                    .model("text-model")
                    .guideJson("not-json")
                    .createdAt(LocalDateTime.of(2026, 8, 5, 10, 0))
                    .build()));

    assertThatThrownBy(() -> service().getGuideBySpeciesName(7L, "청상추"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AI_RESPONSE_INVALID));
  }

  @Test
  void rejectsStoredGuideThatViolatesContentRules() {
    given(
            cacheRepository.findBySpeciesNameAndGuideVersionAndSourceContextHash(
                eq("청상추"), anyInt(), anyString()))
        .willReturn(
            Optional.of(
                PlantCareGuideCache.builder()
                    .speciesName("청상추")
                    .sourceContextHash("test-hash")
                    .evidenceStatus(PlantCareEvidenceStatus.VERIFIED)
                    .evidenceSourcesJson(knowledgeMetadataCodec.serializeSources(knowledge()))
                    .guideVersion(PlantCareGuideSchema.VERSION)
                    .model("text-model")
                    .guideJson(
                        GUIDE_JSON.replace(
                            "\"harvestTarget\": \"파종 후 약 5주\"", "\"harvestTarget\": \"\""))
                    .createdAt(LocalDateTime.of(2026, 8, 5, 10, 0))
                    .build()));

    assertThatThrownBy(() -> service().getGuideBySpeciesName(7L, "청상추"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AI_RESPONSE_INVALID));
  }

  private PlantCareGuideCache cache(String speciesName) {
    return PlantCareGuideCache.builder()
        .speciesName(speciesName)
        .guideVersion(PlantCareGuideSchema.VERSION)
        .sourceContextHash("test-hash")
        .evidenceStatus(PlantCareEvidenceStatus.VERIFIED)
        .evidenceSourcesJson(knowledgeMetadataCodec.serializeSources(knowledge()))
        .model("text-model")
        .guideJson(GUIDE_JSON)
        .createdAt(LocalDateTime.of(2026, 8, 5, 10, 0))
        .build();
  }

  private PlantCareKnowledge knowledge() {
    return knowledge("청상추", "청상추");
  }

  private PlantCareKnowledge knowledge(String requestedSpeciesName, String resolvedSpeciesName) {
    return PlantCareKnowledge.verified(
        requestedSpeciesName,
        resolvedSpeciesName,
        "test-retriever-v1",
        List.of(
            new PlantCareEvidence(
                "nongsaro-218964",
                "검증 재배 자료",
                "https://nongsaro.go.kr",
                "2026-08-01",
                "서늘하고 밝은 곳에서 키우며 흙을 촉촉하게 유지하세요.")));
  }

  private PlantCareKnowledge knowledgeWithContent(String content) {
    return PlantCareKnowledge.verified(
        "청상추",
        "청상추",
        "test-retriever-v1",
        List.of(
            new PlantCareEvidence(
                "nongsaro-218964", "검증 재배 자료", "https://nongsaro.go.kr", "2026-08-01", content)));
  }

  private AiResponse aiResponse() {
    return new AiResponse("resp-1", "text-model", objectMapper.readTree(GUIDE_JSON));
  }

  private AiResponse safeFallbackAiResponse() {
    String safeJson =
        GUIDE_JSON
            .replace("하루 4시간 이상 밝은 곳", "가능한 한 밝고 통풍이 되는 곳")
            .replace("18~25도", "갑작스러운 고온과 저온을 피하는 환경")
            .replace("씨앗을 1cm 깊이로 심습니다.", "씨앗 포장지의 권장 깊이를 확인해 심습니다.")
            .replace("2주에 한 번 액체 비료를 줍니다.", "잎 상태를 관찰하고 제품 표시사항을 확인합니다.")
            .replace("파종 후 약 5주", "잎의 크기와 상태를 보고 판단합니다.");
    return new AiResponse("resp-1", "text-model", objectMapper.readTree(safeJson));
  }
}
