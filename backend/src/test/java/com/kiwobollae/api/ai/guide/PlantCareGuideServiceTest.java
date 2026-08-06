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
import com.kiwobollae.api.ai.policy.AiFeature;
import com.kiwobollae.api.ai.policy.AiRequestGuard;
import com.kiwobollae.api.content.dto.response.PlantSpeciesResponse;
import com.kiwobollae.api.content.service.PlantSpeciesService;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

  @Mock private PlantSpeciesService plantSpeciesService;
  @Mock private PlantCareGuideCacheRepository cacheRepository;
  @Mock private PlantCareGuideCacheWriter cacheWriter;
  @Mock private AiClient aiClient;
  @Mock private AiRequestGuard requestGuard;
  @Mock private PlantCareGuideGenerationLockStore generationLockStore;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void acquireGenerationLeaseByDefault() {
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
        plantSpeciesService,
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
    given(plantSpeciesService.getSpecies(21L)).willReturn(species("청상추"));
    given(
            cacheRepository.findBySpeciesNameAndGuideVersionAndSourceContextHash(
                eq("청상추"), eq(PlantCareGuideSchema.VERSION), anyString()))
        .willReturn(Optional.of(cache("청상추")));

    PlantCareGuide guide = service().getGuideBySpeciesId(7L, 21L);

    assertThat(guide.cached()).isTrue();
    assertThat(guide.speciesName()).isEqualTo("청상추");
    assertThat(guide.difficulty()).isEqualTo("초급");
    assertThat(guide.stages()).hasSize(4);
    assertThat(guide.pitfalls()).hasSize(2);
    verifyNoInteractions(aiClient);
    verifyNoInteractions(requestGuard);
    verifyNoInteractions(generationLockStore);
    verify(cacheRepository, never()).save(any());
  }

  @Test
  void generatesAndStoresGuideOnCacheMiss() {
    given(plantSpeciesService.getSpecies(21L)).willReturn(species("청상추"));
    given(
            cacheRepository.findBySpeciesNameAndGuideVersionAndSourceContextHash(
                eq("청상추"), eq(PlantCareGuideSchema.VERSION), anyString()))
        .willReturn(Optional.empty());
    given(aiClient.generate(any(AiRequest.class))).willReturn(aiResponse());

    PlantCareGuide guide = service().getGuideBySpeciesId(7L, 21L);

    assertThat(guide.cached()).isFalse();
    assertThat(guide.harvestTarget()).isEqualTo("파종 후 약 5주");
    verify(requestGuard).checkRateLimit(7L, AiFeature.PLANT_CARE_GUIDE);

    ArgumentCaptor<PlantCareGuideCache> cacheCaptor =
        ArgumentCaptor.forClass(PlantCareGuideCache.class);
    verify(cacheWriter).save(cacheCaptor.capture());
    assertThat(cacheCaptor.getValue().getSpeciesName()).isEqualTo("청상추");
    assertThat(cacheCaptor.getValue().getSourceSpeciesId()).isEqualTo(21L);
    assertThat(cacheCaptor.getValue().getGuideVersion()).isEqualTo(PlantCareGuideSchema.VERSION);
    assertThat(cacheCaptor.getValue().getModel()).isEqualTo("text-model");
    assertThat(cacheCaptor.getValue().getCreatedAt())
        .isEqualTo(LocalDateTime.of(2026, 8, 5, 10, 0));
  }

  // 공식 가이드는 환각을 줄이는 근거로 넣는다. 텍스트 모델이므로 이미지 입력은 없어야 한다.
  @Test
  void groundsPromptOnRegisteredOfficialGuide() {
    given(plantSpeciesService.getSpecies(21L)).willReturn(species("청상추"));
    given(
            cacheRepository.findBySpeciesNameAndGuideVersionAndSourceContextHash(
                anyString(), anyInt(), anyString()))
        .willReturn(Optional.empty());
    given(aiClient.generate(any(AiRequest.class))).willReturn(aiResponse());

    service().getGuideBySpeciesId(7L, 21L);

    ArgumentCaptor<AiRequest> requestCaptor = ArgumentCaptor.forClass(AiRequest.class);
    verify(aiClient).generate(requestCaptor.capture());
    AiRequest request = requestCaptor.getValue();
    assertThat(request.modelRole()).isEqualTo(AiModelRole.TEXT);
    assertThat(request.images()).isEmpty();
    assertThat(request.userPrompt()).contains("청상추").contains("서늘하고 밝은 곳");
    assertThat(request.responseSchema().name()).isEqualTo("plant_care_guide");
  }

  // 캐시 키는 정규화된 이름이다. 앞뒤·연속 공백만 정리하고 내부 공백은 남긴다.
  @Test
  void normalizesSpeciesNameForCacheKey() {
    given(plantSpeciesService.getSpecies(21L)).willReturn(species("  스위트   바질  "));
    given(
            cacheRepository.findBySpeciesNameAndGuideVersionAndSourceContextHash(
                eq("스위트 바질"), eq(PlantCareGuideSchema.VERSION), anyString()))
        .willReturn(Optional.of(cache("스위트 바질")));

    PlantCareGuide guide = service().getGuideBySpeciesId(7L, 21L);

    assertThat(guide.speciesName()).isEqualTo("스위트 바질");
  }

  // lease 만료 뒤의 극히 드문 중복 저장도 캐시 유니크 제약으로 안전하게 수습한다.
  @Test
  void fallsBackToExistingRowWhenConcurrentRequestStoredFirst() {
    given(plantSpeciesService.getSpecies(21L)).willReturn(species("청상추"));
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

    PlantCareGuide guide = service().getGuideBySpeciesId(7L, 21L);

    assertThat(guide.difficulty()).isEqualTo("초급");
    assertThat(guide.cached()).isTrue();
  }

  @Test
  void rejectsConcurrentCacheMissWithoutCallingAi() {
    given(plantSpeciesService.getSpecies(21L)).willReturn(species("청상추"));
    given(
            cacheRepository.findBySpeciesNameAndGuideVersionAndSourceContextHash(
                eq("청상추"), eq(PlantCareGuideSchema.VERSION), anyString()))
        .willReturn(Optional.empty());
    given(generationLockStore.tryAcquire(any(PlantCareGuideGenerationKey.class)))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> service().getGuideBySpeciesId(7L, 21L))
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
    given(plantSpeciesService.getSpecies(21L)).willReturn(species("청상추"));
    given(
            cacheRepository.findBySpeciesNameAndGuideVersionAndSourceContextHash(
                eq("청상추"), eq(PlantCareGuideSchema.VERSION), anyString()))
        .willReturn(Optional.empty())
        .willReturn(Optional.of(cache("청상추")));

    PlantCareGuide guide = service().getGuideBySpeciesId(7L, 21L);

    assertThat(guide.cached()).isTrue();
    verifyNoInteractions(aiClient);
    verifyNoInteractions(requestGuard);
    verify(generationLockStore).release(any(PlantCareGuideGenerationLockStore.Lease.class));
  }

  @Test
  void rejectsInvalidSpeciesId() {
    assertThatThrownBy(() -> service().getGuideBySpeciesId(7L, 0L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMON_VALIDATION_FAILED));
    verifyNoInteractions(plantSpeciesService);
    verifyNoInteractions(aiClient);
  }

  // 스키마를 바꿨는데 guide_version을 올리지 않으면 옛 저장본을 읽다 깨진다. 조용히 넘기지 않는다.
  @Test
  void rejectsStoredGuideThatNoLongerMatchesSchema() {
    given(plantSpeciesService.getSpecies(21L)).willReturn(species("청상추"));
    given(
            cacheRepository.findBySpeciesNameAndGuideVersionAndSourceContextHash(
                eq("청상추"), anyInt(), anyString()))
        .willReturn(
            Optional.of(
                PlantCareGuideCache.builder()
                    .speciesName("청상추")
                    .sourceContextHash("test-hash")
                    .guideVersion(PlantCareGuideSchema.VERSION)
                    .model("text-model")
                    .guideJson("not-json")
                    .createdAt(LocalDateTime.of(2026, 8, 5, 10, 0))
                    .build()));

    assertThatThrownBy(() -> service().getGuideBySpeciesId(7L, 21L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AI_RESPONSE_INVALID));
  }

  @Test
  void rejectsStoredGuideThatViolatesContentRules() {
    given(plantSpeciesService.getSpecies(21L)).willReturn(species("청상추"));
    given(
            cacheRepository.findBySpeciesNameAndGuideVersionAndSourceContextHash(
                eq("청상추"), anyInt(), anyString()))
        .willReturn(
            Optional.of(
                PlantCareGuideCache.builder()
                    .speciesName("청상추")
                    .sourceSpeciesId(21L)
                    .sourceContextHash("test-hash")
                    .guideVersion(PlantCareGuideSchema.VERSION)
                    .model("text-model")
                    .guideJson(
                        GUIDE_JSON.replace(
                            "\"harvestTarget\": \"파종 후 약 5주\"", "\"harvestTarget\": \"\""))
                    .createdAt(LocalDateTime.of(2026, 8, 5, 10, 0))
                    .build()));

    assertThatThrownBy(() -> service().getGuideBySpeciesId(7L, 21L))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AI_RESPONSE_INVALID));
  }

  private PlantSpeciesResponse species(String name) {
    return new PlantSpeciesResponse(
        21L,
        name,
        "LEAF_VEGETABLE",
        "서늘하고 밝은 곳에서 키우며 흙을 촉촉하게 유지하세요.",
        LocalDateTime.of(2026, 8, 1, 9, 0),
        LocalDateTime.of(2026, 8, 1, 9, 0));
  }

  private PlantCareGuideCache cache(String speciesName) {
    return PlantCareGuideCache.builder()
        .speciesName(speciesName)
        .sourceSpeciesId(21L)
        .guideVersion(PlantCareGuideSchema.VERSION)
        .sourceContextHash("test-hash")
        .model("text-model")
        .guideJson(GUIDE_JSON)
        .createdAt(LocalDateTime.of(2026, 8, 5, 10, 0))
        .build();
  }

  private AiResponse aiResponse() {
    return new AiResponse("resp-1", "text-model", objectMapper.readTree(GUIDE_JSON));
  }
}
