package com.kiwobollae.api.ai.guide;

import com.kiwobollae.api.ai.client.AiClient;
import com.kiwobollae.api.ai.client.AiModelRole;
import com.kiwobollae.api.ai.client.AiRequest;
import com.kiwobollae.api.ai.client.AiResponse;
import com.kiwobollae.api.ai.guide.dto.PlantCareGuide;
import com.kiwobollae.api.ai.guide.dto.PlantCareGuideContent;
import com.kiwobollae.api.ai.guide.dto.PlantCareGuideSchema;
import com.kiwobollae.api.ai.policy.AiFeature;
import com.kiwobollae.api.ai.policy.AiRequestGuard;
import com.kiwobollae.api.content.dto.response.PlantSpeciesResponse;
import com.kiwobollae.api.content.service.PlantSpeciesService;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
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
      - 공식 재배 가이드가 함께 주어지면 그 내용과 모순되지 않게 작성하고, 부족한 부분만 보완합니다.
      - 확실하지 않은 수치는 단정하지 말고 범위로 표현합니다.
      - 모든 문장은 한국어 존댓말로, 초보자가 바로 실행할 수 있게 씁니다.
      - stages는 파종·새싹·성장·수확 네 단계를 모두 포함합니다.
      - pitfalls는 초보자가 가장 자주 겪는 문제 두세 개만 담습니다.
      """;

  private final PlantSpeciesService plantSpeciesService;
  private final PlantCareGuideCacheRepository cacheRepository;
  private final PlantCareGuideCacheWriter cacheWriter;
  private final AiClient aiClient;
  private final AiRequestGuard requestGuard;
  private final PlantCareGuideGenerationLockStore generationLockStore;
  private final ObjectMapper objectMapper;
  private final Clock seoulClock;

  public PlantCareGuideService(
      PlantSpeciesService plantSpeciesService,
      PlantCareGuideCacheRepository cacheRepository,
      PlantCareGuideCacheWriter cacheWriter,
      AiClient aiClient,
      AiRequestGuard requestGuard,
      PlantCareGuideGenerationLockStore generationLockStore,
      ObjectMapper objectMapper,
      Clock seoulClock) {
    this.plantSpeciesService = plantSpeciesService;
    this.cacheRepository = cacheRepository;
    this.cacheWriter = cacheWriter;
    this.aiClient = aiClient;
    this.requestGuard = requestGuard;
    this.generationLockStore = generationLockStore;
    this.objectMapper = objectMapper;
    this.seoulClock = seoulClock;
  }

  /** 등록된 종 id로 가이드를 조회한다. 저장본이 없으면 AI를 호출해 생성하고 저장한다. */
  public PlantCareGuide getGuideBySpeciesId(Long userId, Long speciesId) {
    PlantSpeciesResponse species = requireSpecies(speciesId);
    return resolveGuide(userId, species.name(), speciesId, species.category(), species.careGuide());
  }

  private PlantSpeciesResponse requireSpecies(Long speciesId) {
    if (speciesId == null || speciesId < 1) {
      throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "식물 종 ID가 필요합니다.");
    }
    return plantSpeciesService.getSpecies(speciesId);
  }

  private PlantCareGuide resolveGuide(
      Long userId, String rawName, Long sourceSpeciesId, String category, String officialGuide) {
    String speciesName = normalizeSpeciesName(rawName);
    String sourceContextHash = sourceContextHash(category, officialGuide);

    return findCachedGuide(speciesName, sourceContextHash)
        .orElseGet(
            () ->
                generateAndCache(
                    userId,
                    speciesName,
                    sourceSpeciesId,
                    category,
                    officialGuide,
                    sourceContextHash));
  }

  private PlantCareGuide generateAndCache(
      Long userId,
      String speciesName,
      Long sourceSpeciesId,
      String category,
      String officialGuide,
      String sourceContextHash) {
    PlantCareGuideGenerationKey key =
        new PlantCareGuideGenerationKey(
            speciesName, PlantCareGuideSchema.VERSION, sourceContextHash);
    Optional<PlantCareGuideGenerationLockStore.Lease> lease = generationLockStore.tryAcquire(key);
    if (lease.isEmpty()) {
      // 소유자가 캐시를 저장하고 선점을 반납하는 바로 그 순간일 수 있으므로 한 번 더 읽는다.
      // 그래도 없으면 진행 중 요청을 기다리지 않고 409로 끝내 외부 AI 호출을 절대 중복하지 않는다.
      return findCachedGuide(speciesName, sourceContextHash)
          .orElseThrow(
              () ->
                  new BusinessException(
                      ErrorCode.COMMON_DATA_CONFLICT, "재배 가이드를 생성하고 있습니다. 잠시 후 다시 시도해 주세요."));
    }

    try {
      // 선점 전 캐시 미스를 읽은 뒤 다른 요청이 저장을 끝냈을 수도 있다. 이 재조회가 있어야
      // 새로 선점한 요청도 불필요한 AI 호출을 하지 않는다.
      Optional<PlantCareGuide> cached = findCachedGuide(speciesName, sourceContextHash);
      if (cached.isPresent()) {
        return cached.get();
      }
      return generateAndStore(
          userId, speciesName, sourceSpeciesId, category, officialGuide, sourceContextHash);
    } finally {
      // 만료라는 안전망이 없으므로 어떤 경로로 빠져나가든 반드시 반납한다. 반납이 누락되면 그 종은
      // 프로세스가 살아 있는 내내 409만 돌려주게 된다.
      generationLockStore.release(lease.get());
    }
  }

  private PlantCareGuide generateAndStore(
      Long userId,
      String speciesName,
      Long sourceSpeciesId,
      String category,
      String officialGuide,
      String sourceContextHash) {
    // 저장본을 쓰지 않고 외부 호출이 확정된 지점이므로 여기서만 사용자별·전역 예산을 함께 예약한다.
    requestGuard.checkRateLimit(userId, AiFeature.PLANT_CARE_GUIDE);

    AiResponse response = aiClient.generate(buildRequest(speciesName, category, officialGuide));
    PlantCareGuideContent generated = validateGuide(deserialize(response.result().toString()));
    try {
      cacheWriter.save(
          PlantCareGuideCache.builder()
              .speciesName(speciesName)
              .sourceSpeciesId(sourceSpeciesId)
              .sourceContextHash(sourceContextHash)
              .guideVersion(PlantCareGuideSchema.VERSION)
              .model(response.model())
              .guideJson(response.result().toString())
              .createdAt(LocalDateTime.now(seoulClock))
              .build());
      return PlantCareGuide.of(speciesName, generated, false);
    } catch (DataIntegrityViolationException exception) {
      // 무결성 위반을 곧바로 "중복"이라 단정하지 않는다. 저장본이 실제로 있는지 먼저 확인해야
      // 동시 저장 경쟁(정상)과 스키마·데이터 결함(비정상)을 구분할 수 있다.
      Optional<PlantCareGuide> stored = findCachedGuide(speciesName, sourceContextHash);
      if (stored.isPresent()) {
        log.info("가이드 캐시 중복 저장 감지, 기존 저장본을 사용합니다: {}", speciesName);
        return stored.get();
      }
      // 저장도 실패했고 저장본도 없다 = 중복이 아니다. 컬럼 타입·길이 같은 결함이므로 원인을
      // 반드시 남긴다. 예외를 삼키면 매 요청이 AI를 부르고 409로 끝나는 상태를 알아채지 못한다.
      log.error("재배 가이드 캐시 저장에 실패했고 저장본도 없습니다: species={}", speciesName, exception);
      throw new BusinessException(ErrorCode.COMMON_DATA_CONFLICT);
    }
  }

  private Optional<PlantCareGuide> findCachedGuide(String speciesName, String sourceContextHash) {
    return cacheRepository
        .findBySpeciesNameAndGuideVersionAndSourceContextHash(
            speciesName, PlantCareGuideSchema.VERSION, sourceContextHash)
        .map(cache -> PlantCareGuide.of(speciesName, validateGuide(readGuide(cache)), true));
  }

  /**
   * 캐시 키로 쓸 종 이름 정규화.
   *
   * <p>앞뒤 공백을 없애고 연속 공백을 하나로 줄이는 수준만 한다. "방울 토마토"와 "방울토마토"를 같은 것으로 묶는 식의 공백 제거까지 하면 서로 다른 종이 한 캐시를
   * 공유할 위험이 있어 하지 않는다.
   */
  private String normalizeSpeciesName(String rawName) {
    if (rawName == null || rawName.isBlank()) {
      throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "식물 종 이름이 필요합니다.");
    }
    String normalized = rawName.trim().replaceAll("\\s+", " ");
    if (normalized.length() > 100) {
      throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "식물 종 이름이 너무 깁니다.");
    }
    return normalized;
  }

  private AiRequest buildRequest(String speciesName, String category, String officialGuide) {
    StringBuilder userPrompt = new StringBuilder();
    userPrompt.append("식물 종: ").append(speciesName).append('\n');
    if (category != null && !category.isBlank()) {
      userPrompt.append("분류: ").append(category).append('\n');
    }
    if (officialGuide != null && !officialGuide.isBlank()) {
      userPrompt
          .append("서비스에 등록된 공식 재배 가이드(이 내용과 모순되지 않게 작성하세요): ")
          .append(officialGuide)
          .append('\n');
    }
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

  private String sourceContextHash(String category, String officialGuide) {
    String source =
        (category == null ? "" : category) + "\n" + (officialGuide == null ? "" : officialGuide);
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
    }
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
