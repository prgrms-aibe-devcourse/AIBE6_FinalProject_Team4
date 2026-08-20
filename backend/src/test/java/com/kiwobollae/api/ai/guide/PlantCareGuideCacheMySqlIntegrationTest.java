package com.kiwobollae.api.ai.guide;

import static org.assertj.core.api.Assertions.assertThat;

import com.kiwobollae.api.ai.guide.dto.PlantCareGuideSchema;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 가이드 JSON이 실제 MySQL 컬럼에 들어가는지 확인한다.
 *
 * <p>이 테스트가 없어서 캐시가 한 번도 저장되지 않는 상태를 오래 놓쳤다. {@code @Lob} + String이 MySQL에서 {@code
 * tinytext}(255바이트)로 생성돼 한국어 가이드가 매번 "Data too long"으로 실패했는데, 서비스 단위 테스트는 저장소를 mock으로 끊어서 이 경로를 아예
 * 타지 않는다. <b>컬럼 타입 문제는 실제 DB에 넣어 봐야만 드러난다.</b>
 */
@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "spring.datasource.url=jdbc:mysql://localhost:3306/kiwobollae_ai_test"
          + "?createDatabaseIfNotExist=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
class PlantCareGuideCacheMySqlIntegrationTest {

  private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 6, 10, 0);

  @Autowired private PlantCareGuideCacheRepository repository;

  @BeforeEach
  void clearCache() {
    repository.deleteAllInBatch();
  }

  // 실제 가이드는 한국어라 문자당 3바이트다. tinytext(255바이트)면 여기서 깨진다.
  @Test
  void storesAndReadsBackLongKoreanGuideJson() {
    String longKoreanJson = koreanGuideJson(4000);
    assertThat(longKoreanJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
        .isGreaterThan(10_000);

    repository.saveAndFlush(cache("청상추", longKoreanJson));

    Optional<PlantCareGuideCache> found =
        repository.findBySpeciesNameAndGuideVersionAndSourceContextHash(
            "청상추", PlantCareGuideSchema.VERSION, "a".repeat(64));
    assertThat(found).isPresent();
    // 잘려서 저장되면 길이가 줄어든다. 원문 그대로 돌아와야 역직렬화가 성립한다.
    assertThat(found.get().getGuideJson()).isEqualTo(longKoreanJson);
  }

  // 캐시 중복 방어가 이 제약이 터지는 것에 의존한다. 실제로 걸리는지 확인한다.
  @Test
  void rejectsDuplicateCacheKey() {
    repository.saveAndFlush(cache("청상추", koreanGuideJson(10)));

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> repository.saveAndFlush(cache("청상추", koreanGuideJson(20))))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  // 같은 종이라도 원본 컨텍스트가 다르면 별개 저장본이다.
  @Test
  void allowsSameSpeciesWithDifferentSourceContextHash() {
    repository.saveAndFlush(cache("청상추", koreanGuideJson(10), "a".repeat(64)));
    repository.saveAndFlush(cache("청상추", koreanGuideJson(10), "b".repeat(64)));

    assertThat(repository.count()).isEqualTo(2);
  }

  private String koreanGuideJson(int repeats) {
    return "{\"guide\":\"" + "겉흙이 마르면 물을 충분히 주세요. ".repeat(repeats) + "\"}";
  }

  private PlantCareGuideCache cache(String speciesName, String guideJson) {
    return cache(speciesName, guideJson, "a".repeat(64));
  }

  private PlantCareGuideCache cache(
      String speciesName, String guideJson, String sourceContextHash) {
    return PlantCareGuideCache.builder()
        .speciesName(speciesName)
        .sourceContextHash(sourceContextHash)
        .guideVersion(PlantCareGuideSchema.VERSION)
        .model("text-model")
        .guideJson(guideJson)
        .createdAt(CREATED_AT)
        .build();
  }
}
