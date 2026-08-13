package com.kiwobollae.api.ai.guide;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.kiwobollae.api.ai.client.OpenAiClient;
import com.kiwobollae.api.ai.client.RealOpenAiClients;
import com.kiwobollae.api.ai.config.AiConfig;
import com.kiwobollae.api.ai.config.OpenAiProperties;
import com.kiwobollae.api.ai.guide.dto.PlantCareGuide;
import com.kiwobollae.api.ai.guide.dto.PlantCareGuideSchema;
import com.kiwobollae.api.ai.policy.AiRequestGuard;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.species.dto.response.PlantSpeciesResponse;
import com.kiwobollae.api.species.service.PlantSpeciesService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.ObjectMapper;

/**
 * 실제 OpenAI로 재배 가이드를 생성해 응답 계약이 성립하는지 확인한다.
 *
 * <p>Mock 테스트로는 확인할 수 없는 두 가지가 대상이다.
 *
 * <ul>
 *   <li>strict 모드가 한국어 enum 값(초급/중급/고급, 파종/새싹/성장/수확)을 받아주는가
 *   <li>모델이 stages 네 단계를 실제로 다 채우는가 — structured outputs는 배열 길이를 제약할 수 없어서 이 요구는 프롬프트에만 실려 있다
 * </ul>
 *
 * <p>클라이언트를 직접 찌르지 않고 {@link PlantCareGuideService}를 통과시킨다. 프롬프트를 이 테스트에 복사하면 정작 운영에서 쓰는 프롬프트는 검증되지
 * 않기 때문이다. 저장소·호출 제한만 mock으로 끊고 프롬프트·스키마·역직렬화는 실제 코드를 그대로 태운다.
 */
@Tag("openai-smoke")
class PlantCareGuideSmokeTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  @Test
  void generatesUsableCareGuideFromRealOpenAiApi() {
    OpenAiProperties properties = loadOpenAiProperties();
    requireConfigured(properties.apiKey(), "ai.openai.api-key");
    requireConfigured(properties.textModel(), "ai.openai.text-model");

    PlantCareGuideService service = serviceBackedByRealOpenAi(properties);

    PlantCareGuide guide;
    try {
      guide = service.getGuideBySpeciesId(7L, 21L);
    } catch (BusinessException exception) {
      throw new AssertionError(
          "실제 OpenAI 재배 가이드 생성 실패: " + exception.getErrorCode().name(), exception);
    }

    System.out.println("=== 생성된 재배 가이드 ===");
    System.out.println(guide);

    assertThat(guide.speciesName()).isEqualTo("청상추");
    assertThat(guide.cached()).isFalse();

    // strict 모드가 한국어 enum 값을 받아줬는지.
    assertThat(guide.difficulty()).isIn(PlantCareGuideSchema.DIFFICULTY_VALUES);
    assertThat(guide.difficultyReason()).isNotBlank();

    assertThat(guide.environment()).isNotNull();
    assertThat(guide.environment().sunlight()).isNotBlank();
    assertThat(guide.environment().watering()).isNotBlank();
    assertThat(guide.environment().temperature()).isNotBlank();

    // 스키마가 강제하지 못하는 부분 — 네 단계가 순서대로 다 왔는지는 프롬프트 준수 여부다.
    assertThat(guide.stages())
        .extracting(stage -> stage.name())
        .containsExactlyElementsOf(PlantCareGuideSchema.STAGE_NAMES);
    assertThat(guide.stages()).allSatisfy(stage -> assertThat(stage.guide()).isNotBlank());

    assertThat(guide.pitfalls()).isNotEmpty();
    assertThat(guide.pitfalls())
        .allSatisfy(
            pitfall -> {
              assertThat(pitfall.problem()).isNotBlank();
              assertThat(pitfall.action()).isNotBlank();
            });

    assertThat(guide.harvestTarget()).isNotBlank();
  }

  /** 실제 OpenAI 클라이언트만 진짜, 저장소와 호출 제한은 끊는다. */
  private PlantCareGuideService serviceBackedByRealOpenAi(OpenAiProperties properties) {
    OpenAiClient client = RealOpenAiClients.create(properties);

    PlantSpeciesService plantSpeciesService = mock(PlantSpeciesService.class);
    given(plantSpeciesService.getSpecies(21L))
        .willReturn(
            new PlantSpeciesResponse(
                21L,
                "청상추",
                "LEAF_VEGETABLE",
                "서늘하고 밝은 곳에서 키우며 흙을 촉촉하게 유지하세요. 바깥 잎부터 수확하면 오래 먹을 수 있습니다.",
                LocalDateTime.now(KST),
                LocalDateTime.now(KST)));

    // 저장소는 stub 없이 두면 Mockito가 Optional.empty()를 돌려주므로 항상 캐시 미스가 된다.
    PlantCareGuideGenerationLockStore generationLockStore =
        mock(PlantCareGuideGenerationLockStore.class);
    given(generationLockStore.tryAcquire(any()))
        .willAnswer(
            invocation ->
                Optional.of(
                    new PlantCareGuideGenerationLockStore.Lease(
                        invocation.getArgument(0), new Object())));
    return new PlantCareGuideService(
        plantSpeciesService,
        mock(PlantCareGuideCacheRepository.class),
        mock(PlantCareGuideCacheWriter.class),
        client,
        mock(AiRequestGuard.class),
        generationLockStore,
        new ObjectMapper(),
        Clock.fixed(Instant.parse("2026-08-05T01:00:00Z"), KST));
  }

  private OpenAiProperties loadOpenAiProperties() {
    try (ConfigurableApplicationContext context =
        new SpringApplicationBuilder(AiConfig.class)
            .web(WebApplicationType.NONE)
            .logStartupInfo(false)
            .properties("spring.main.banner-mode=off")
            .run()) {
      return context.getBean(OpenAiProperties.class);
    }
  }

  private void requireConfigured(String value, String propertyName) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          propertyName + " 설정이 application-secret.yaml 또는 환경변수에 필요합니다.");
    }
  }
}
