package com.kiwobollae.api.ai.guide;

import com.kiwobollae.api.ai.client.AiClient;
import com.kiwobollae.api.ai.client.AiModelRole;
import com.kiwobollae.api.ai.client.AiRequest;
import com.kiwobollae.api.ai.client.AiResponse;
import com.kiwobollae.api.ai.guide.dto.PlantCareGuide;
import com.kiwobollae.api.ai.guide.dto.PlantCareGuideContent;
import com.kiwobollae.api.ai.guide.dto.PlantCareGuideSchema;
import com.kiwobollae.api.ai.knowledge.PlantCareAdviceSafetyPolicy;
import com.kiwobollae.api.ai.knowledge.PlantCareEvidenceScope;
import com.kiwobollae.api.ai.knowledge.PlantCareGrounding;
import com.kiwobollae.api.ai.knowledge.PlantCareKnowledge;
import com.kiwobollae.api.ai.knowledge.PlantCareKnowledgeMetadataCodec;
import com.kiwobollae.api.ai.knowledge.PlantCareKnowledgeQuery;
import com.kiwobollae.api.ai.knowledge.PlantCareKnowledgeRetriever;
import com.kiwobollae.api.ai.policy.AiFeature;
import com.kiwobollae.api.ai.policy.AiRequestGuard;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 사용자가 고른 종 하나에 대한 재배 가이드를 제공한다.
 *
 * <p>가이드는 사용자별 콘텐츠가 아니므로 종당 한 번만 생성해 {@link PlantCareGuideCache}에 저장하고, 이후 요청은 저장본을 돌려준다. <b>캐시
 * 히트에서는 AI를 호출하지 않으므로 호출 제한도 소모하지 않는다</b> — 제한은 실제 외부 호출을 막기 위한 것이다.
 */
@Slf4j
@Service
public class PlantCareGuideService {

  private static final String SYSTEM_PROMPT =
      """
      당신은 한국의 가정 원예(베란다·창가 화분 재배)를 돕는 원예 전문가입니다.
      주어진 식물 종 하나를 초보자가 실제로 키울 수 있도록 구체적인 재배 가이드를 작성합니다.

      규칙:
      - 한국의 기후와 아파트 베란다·실내 창가 환경을 기준으로 설명합니다.
      - 공식 재배 가이드와 검증 재배 근거가 함께 주어지면 그 내용과 모순되지 않게 작성하고, 부족한 부분만 보완합니다.
      - 재배 근거가 제공되면 이를 최우선으로 사용합니다. 근거에 없는 정확한 수치나 방법을 단정하지 마세요.
      - 재배 근거와 일반 지식이 다를 수 있으면 재배 근거를 따르세요.
      - plantCareKnowledge.evidenceStatus가 GENERAL_FALLBACK이면 공식 근거가 없는 일반 AI 지식입니다.
        이때 정확한 투입량·희석 배수·처리 주기, 농약·살충제·살균제·비료 제품이나 성분을 처방하지 말고
        관찰 기준과 제품 표시사항·공공기관·전문가 확인 방법을 우선 안내하세요.
      - GENERAL_FALLBACK에서는 출처를 확인한 사실인 것처럼 말하거나 존재하지 않는 출처를 만들지 마세요.
      - plantCareKnowledge.evidenceScope가 BASE_SPECIES이면 입력 품종 전용 근거가 아니라
        resolvedSpeciesName 기준 작물의 공통 근거입니다. 품종 고유 특성을 공식 근거처럼 단정하지 마세요.
      - 확실하지 않은 수치는 단정하지 말고 범위로 표현합니다.
      - 모든 문장은 한국어 존댓말로, 초보자가 바로 실행할 수 있게 씁니다.
      - stages는 파종·새싹·성장·수확 네 단계를 모두 포함합니다.
      - pitfalls는 초보자가 가장 자주 겪는 문제 두세 개만 담습니다.
      """;

  private final PlantCareKnowledgeRetriever knowledgeRetriever;
  private final PlantCareKnowledgeMetadataCodec knowledgeMetadataCodec;
  private final PlantCareAdviceSafetyPolicy adviceSafetyPolicy;
  private final PlantCareGuideCacheRepository cacheRepository;
  private final PlantCareGuideCacheWriter cacheWriter;
  private final AiClient aiClient;
  private final AiRequestGuard requestGuard;
  private final PlantCareGuideGenerationLockStore generationLockStore;
  private final ObjectMapper objectMapper;
  private final Clock seoulClock;

  public PlantCareGuideService(
      PlantCareKnowledgeRetriever knowledgeRetriever,
      PlantCareKnowledgeMetadataCodec knowledgeMetadataCodec,
      PlantCareAdviceSafetyPolicy adviceSafetyPolicy,
      PlantCareGuideCacheRepository cacheRepository,
      PlantCareGuideCacheWriter cacheWriter,
      AiClient aiClient,
      AiRequestGuard requestGuard,
      PlantCareGuideGenerationLockStore generationLockStore,
      ObjectMapper objectMapper,
      Clock seoulClock) {
    this.knowledgeRetriever = knowledgeRetriever;
    this.knowledgeMetadataCodec = knowledgeMetadataCodec;
    this.adviceSafetyPolicy = adviceSafetyPolicy;
    this.cacheRepository = cacheRepository;
    this.cacheWriter = cacheWriter;
    this.aiClient = aiClient;
    this.requestGuard = requestGuard;
    this.generationLockStore = generationLockStore;
    this.objectMapper = objectMapper;
    this.seoulClock = seoulClock;
  }

  /** 종 이름으로 가이드를 조회한다. 저장본이 없으면 AI를 호출해 생성하고 저장한다. */
  public PlantCareGuide getGuideBySpeciesName(Long userId, String rawSpeciesName) {
    PlantCareKnowledge knowledge = retrieveKnowledge(rawSpeciesName);

    return findCachedGuide(knowledge).orElseGet(() -> generateAndCache(userId, knowledge));
  }

  /** 이미 캐시된 가이드의 종 이름 중 입력 문자열을 포함하는 이름을 검색한다. */
  public List<String> searchSpeciesNames(String rawQuery) {
    if (rawQuery == null || rawQuery.isBlank()) {
      throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "검색어가 필요합니다.");
    }
    return cacheRepository.findDistinctSpeciesNamesContaining(rawQuery.trim());
  }

  private PlantCareGuide generateAndCache(Long userId, PlantCareKnowledge knowledge) {
    PlantCareGuideGenerationKey key =
        new PlantCareGuideGenerationKey(
            knowledge.resolvedSpeciesName(),
            PlantCareGuideSchema.VERSION,
            knowledge.sourceContextHash());
    Optional<PlantCareGuideGenerationLockStore.Lease> lease = generationLockStore.tryAcquire(key);
    if (lease.isEmpty()) {
      // 소유자가 캐시를 저장하고 선점을 반납하는 바로 그 순간일 수 있으므로 한 번 더 읽는다.
      // 그래도 없으면 진행 중 요청을 기다리지 않고 409로 끝내 외부 AI 호출을 절대 중복하지 않는다.
      return findCachedGuide(knowledge)
          .orElseThrow(
              () ->
                  new BusinessException(
                      ErrorCode.COMMON_DATA_CONFLICT, "재배 가이드를 생성하고 있습니다. 잠시 후 다시 시도해 주세요."));
    }

    try {
      // 선점 전 캐시 미스를 읽은 뒤 다른 요청이 저장을 끝냈을 수도 있다. 이 재조회가 있어야
      // 새로 선점한 요청도 불필요한 AI 호출을 하지 않는다.
      Optional<PlantCareGuide> cached = findCachedGuide(knowledge);
      if (cached.isPresent()) {
        return cached.get();
      }
      return generateAndStore(userId, knowledge);
    } finally {
      // 만료라는 안전망이 없으므로 어떤 경로로 빠져나가든 반드시 반납한다. 반납이 누락되면 그 종은
      // 프로세스가 살아 있는 내내 409만 돌려주게 된다.
      generationLockStore.release(lease.get());
    }
  }

  private PlantCareGuide generateAndStore(Long userId, PlantCareKnowledge knowledge) {
    // 저장본을 쓰지 않고 외부 호출이 확정된 지점이므로 여기서만 사용자별·전역 예산을 함께 예약한다.
    requestGuard.checkRateLimit(userId, AiFeature.PLANT_CARE_GUIDE);

    AiResponse response = aiClient.generate(buildRequest(knowledge));
    PlantCareGuideContent generated = validateGuide(deserialize(response.result().toString()));
    validateSafety(knowledge.grounding(), generated);
    try {
      cacheWriter.save(
          PlantCareGuideCache.builder()
              .speciesName(knowledge.resolvedSpeciesName())
              .sourceContextHash(knowledge.sourceContextHash())
              .evidenceStatus(knowledge.evidenceStatus())
              .evidenceSourcesJson(knowledgeMetadataCodec.serializeSources(knowledge))
              .guideVersion(PlantCareGuideSchema.VERSION)
              .model(response.model())
              .guideJson(response.result().toString())
              .createdAt(LocalDateTime.now(seoulClock))
              .build());
      return PlantCareGuide.of(
          knowledge.requestedSpeciesName(), generated, knowledge.grounding(), false);
    } catch (DataIntegrityViolationException exception) {
      // 무결성 위반을 곧바로 "중복"이라 단정하지 않는다. 저장본이 실제로 있는지 먼저 확인해야
      // 동시 저장 경쟁(정상)과 스키마·데이터 결함(비정상)을 구분할 수 있다.
      Optional<PlantCareGuide> stored = findCachedGuide(knowledge);
      if (stored.isPresent()) {
        log.info("가이드 캐시 중복 저장 감지, 기존 저장본을 사용합니다: {}", knowledge.resolvedSpeciesName());
        return stored.get();
      }
      // 저장도 실패했고 저장본도 없다 = 중복이 아니다. 컬럼 타입·길이 같은 결함이므로 원인을
      // 반드시 남긴다. 예외를 삼키면 매 요청이 AI를 부르고 409로 끝나는 상태를 알아채지 못한다.
      log.error(
          "재배 가이드 캐시 저장에 실패했고 저장본도 없습니다: species={}", knowledge.resolvedSpeciesName(), exception);
      throw new BusinessException(ErrorCode.COMMON_DATA_CONFLICT);
    }
  }

  private Optional<PlantCareGuide> findCachedGuide(PlantCareKnowledge knowledge) {
    return cacheRepository
        .findBySpeciesNameAndGuideVersionAndSourceContextHash(
            knowledge.resolvedSpeciesName(),
            PlantCareGuideSchema.VERSION,
            knowledge.sourceContextHash())
        .map(cache -> toCachedGuide(knowledge, cache));
  }

  private PlantCareGuide toCachedGuide(PlantCareKnowledge knowledge, PlantCareGuideCache cache) {
    PlantCareGuideContent content = validateGuide(readGuide(cache));
    PlantCareGrounding grounding =
        knowledgeMetadataCodec.deserializeGrounding(
            cache.getEvidenceStatus(),
            knowledge.evidenceScope(),
            knowledge.resolvedSpeciesName(),
            cache.getEvidenceSourcesJson());
    validateSafety(grounding, content);
    return PlantCareGuide.of(knowledge.requestedSpeciesName(), content, grounding, true);
  }

  private AiRequest buildRequest(PlantCareKnowledge knowledge) {
    StringBuilder userPrompt = new StringBuilder();
    String guideTargetName =
        knowledge.evidenceScope() == PlantCareEvidenceScope.BASE_SPECIES
            ? knowledge.resolvedSpeciesName()
            : knowledge.requestedSpeciesName();
    userPrompt.append("식물 종: ").append(guideTargetName).append('\n');
    userPrompt
        .append("<plantCareKnowledge>\n")
        .append(serializeKnowledge(knowledge))
        .append("\n</plantCareKnowledge>\n");
    userPrompt.append("위 종을 가정에서 키우기 위한 재배 가이드를 작성해 주세요.");

    return new AiRequest(
        AiModelRole.TEXT,
        SYSTEM_PROMPT,
        userPrompt.toString(),
        null,
        PlantCareGuideSchema.create());
  }

  private PlantCareGuideContent readGuide(PlantCareGuideCache cache) {
    return deserialize(cache.getGuideJson());
  }

  private PlantCareGuideContent validateGuide(PlantCareGuideContent guide) {
    if (guide == null
        || !contains(PlantCareGuideSchema.DIFFICULTY_VALUES, guide.difficulty())
        || blank(guide.difficultyReason())
        || guide.environment() == null
        || blank(guide.environment().sunlight())
        || blank(guide.environment().watering())
        || blank(guide.environment().temperature())
        || guide.stages() == null
        || guide.stages().size() != PlantCareGuideSchema.STAGE_NAMES.size()
        || guide.stages().stream().anyMatch(stage -> stage == null || blank(stage.guide()))
        || !stageNamesMatch(guide.stages())
        || guide.pitfalls() == null
        || guide.pitfalls().size() < 2
        || guide.pitfalls().size() > 3
        || guide.pitfalls().stream()
            .anyMatch(p -> p == null || blank(p.problem()) || blank(p.action()))
        || blank(guide.harvestTarget())) {
      throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
    }
    return guide;
  }

  private boolean stageNamesMatch(List<PlantCareGuideContent.Stage> stages) {
    for (int i = 0; i < stages.size(); i++) {
      if (!PlantCareGuideSchema.STAGE_NAMES.get(i).equals(stages.get(i).name())) {
        return false;
      }
    }
    return true;
  }

  private boolean contains(List<String> values, String value) {
    return value != null && values.contains(value);
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private PlantCareKnowledge retrieveKnowledge(String rawSpeciesName) {
    try {
      return knowledgeRetriever.retrieve(new PlantCareKnowledgeQuery(rawSpeciesName));
    } catch (IllegalArgumentException exception) {
      throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, exception.getMessage());
    }
  }

  private String serializeKnowledge(PlantCareKnowledge knowledge) {
    try {
      return objectMapper.writeValueAsString(knowledge.promptPayload());
    } catch (JacksonException exception) {
      throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
    }
  }

  private void validateSafety(PlantCareGrounding grounding, PlantCareGuideContent guide) {
    List<String> texts = new ArrayList<>();
    texts.add(guide.difficultyReason());
    texts.add(guide.environment().sunlight());
    texts.add(guide.environment().watering());
    texts.add(guide.environment().temperature());
    guide.stages().forEach(stage -> texts.add(stage.guide()));
    guide
        .pitfalls()
        .forEach(
            pitfall -> {
              texts.add(pitfall.problem());
              texts.add(pitfall.action());
            });
    texts.add(guide.harvestTarget());
    adviceSafetyPolicy.validate(grounding.status(), texts);
  }

  private PlantCareGuideContent deserialize(String json) {
    try {
      return objectMapper.readValue(json, PlantCareGuideContent.class);
    } catch (JacksonException exception) {
      // 스키마와 저장본이 어긋난 상태다. guide_version을 올려 옛 저장본을 무효화해야 한다.
      log.error("재배 가이드 JSON을 읽지 못했습니다. 스키마 버전 확인이 필요합니다.", exception);
      throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
    }
  }
}
