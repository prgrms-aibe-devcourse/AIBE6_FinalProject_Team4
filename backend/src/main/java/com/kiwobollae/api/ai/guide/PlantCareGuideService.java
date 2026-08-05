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
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
@RequiredArgsConstructor
@Transactional(readOnly = true)
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
  private final AiClient aiClient;
  private final AiRequestGuard requestGuard;
  private final ObjectMapper objectMapper;
  private final Clock seoulClock;

  /** 등록된 종 id로 가이드를 조회한다. 저장본이 없으면 AI를 호출해 생성하고 저장한다. */
  @Transactional
  public PlantCareGuide getGuideBySpeciesId(Long userId, Long speciesId) {
    if (speciesId == null || speciesId < 1) {
      throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "식물 종 ID가 필요합니다.");
    }
    PlantSpeciesResponse species = plantSpeciesService.getSpecies(speciesId);
    return resolveGuide(userId, species.name(), speciesId, species.category(), species.careGuide());
  }

  private PlantCareGuide resolveGuide(
      Long userId, String rawName, Long sourceSpeciesId, String category, String officialGuide) {
    String speciesName = normalizeSpeciesName(rawName);

    Optional<PlantCareGuideCache> cached =
        cacheRepository.findBySpeciesNameAndGuideVersion(speciesName, PlantCareGuideSchema.VERSION);
    if (cached.isPresent()) {
      return PlantCareGuide.of(speciesName, readGuide(cached.get()), true);
    }

    // 캐시 미스에서만 외부 호출이 일어나므로 이 지점에서만 제한을 센다.
    requestGuard.checkRateLimit(userId, AiFeature.PLANT_CARE_GUIDE);

    AiResponse response = aiClient.generate(buildRequest(speciesName, category, officialGuide));
    PlantCareGuideContent generated = deserialize(response.result().toString());

    return PlantCareGuide.of(
        speciesName, store(speciesName, sourceSpeciesId, response, generated), false);
  }

  private PlantCareGuideContent store(
      String speciesName,
      Long sourceSpeciesId,
      AiResponse response,
      PlantCareGuideContent generated) {
    try {
      cacheRepository.save(
          PlantCareGuideCache.builder()
              .speciesName(speciesName)
              .sourceSpeciesId(sourceSpeciesId)
              .guideVersion(PlantCareGuideSchema.VERSION)
              .model(response.model())
              .guideJson(response.result().toString())
              .createdAt(LocalDateTime.now(seoulClock))
              .build());
      return generated;
    } catch (DataIntegrityViolationException exception) {
      // 같은 종을 동시에 요청해 다른 트랜잭션이 먼저 저장했다. 두 번 호출한 비용은 이미 났지만
      // 저장본을 하나로 유지하는 게 중요하므로 먼저 저장된 쪽을 정본으로 삼는다.
      // 외부 호출 구간에 락을 걸지 않기 위해 감수하는 트레이드오프다(드물게 발생).
      log.info("가이드 캐시 중복 저장 감지, 기존 저장본을 사용합니다: {}", speciesName);
      return cacheRepository
          .findBySpeciesNameAndGuideVersion(speciesName, PlantCareGuideSchema.VERSION)
          .map(this::readGuide)
          .orElse(generated);
    }
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
