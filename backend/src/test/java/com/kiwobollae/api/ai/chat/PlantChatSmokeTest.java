package com.kiwobollae.api.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.kiwobollae.api.ai.chat.dto.PlantChatRequest;
import com.kiwobollae.api.ai.chat.dto.PlantChatResponse;
import com.kiwobollae.api.ai.client.AiClient;
import com.kiwobollae.api.ai.client.OpenAiClient;
import com.kiwobollae.api.ai.client.RealOpenAiClients;
import com.kiwobollae.api.ai.config.AiConfig;
import com.kiwobollae.api.ai.config.OpenAiProperties;
import com.kiwobollae.api.ai.policy.AiRequestGuard;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.plantProfile.dto.response.PlantGrowthContextResponse;
import com.kiwobollae.api.plantProfile.dto.response.PlantGrowthContextResponse.RecentJournal;
import com.kiwobollae.api.plantProfile.entity.enums.PlantStatus;
import com.kiwobollae.api.plantProfile.service.PlantGrowthContextQuery;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.ObjectMapper;

/** 실제 OpenAI를 한 번 호출해 식물일지 챗봇의 운영 프롬프트와 응답 계약을 확인한다. */
@Tag("openai-smoke")
class PlantChatSmokeTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final Clock FIXED_KST_CLOCK =
      Clock.fixed(Instant.parse("2026-08-10T03:00:00Z"), KST);

  @Test
  void generatesUsablePlantChatResponseFromRealOpenAiApi() {
    OpenAiProperties properties = loadOpenAiProperties();
    requireConfigured(properties.apiKey(), "ai.openai.api-key");
    requireConfigured(properties.textModel(), "ai.openai.text-model");

    AtomicInteger actualCalls = new AtomicInteger();
    PlantChatService service = serviceBackedByRealOpenAi(properties, actualCalls);
    PlantChatRequest request =
        new PlantChatRequest("최근 잎 끝이 조금 갈색으로 변했습니다. 물주기와 빛 환경에서 무엇을 확인해야 하나요?", null);

    Instant startedAt = Instant.now();
    PlantChatResponse response;
    try {
      response = service.chat(7L, 21L, request);
    } catch (BusinessException exception) {
      throw new AssertionError(
          "실제 OpenAI 식물일지 챗봇 호출 실패: " + exception.getErrorCode().name(), exception);
    }
    Duration elapsed = Duration.between(startedAt, Instant.now());

    System.out.printf(
        "OpenAI 식물일지 챗봇 스모크 테스트 성공: model=%s, elapsedMs=%d, actions=%d, checks=%d%n",
        properties.textModel(),
        elapsed.toMillis(),
        response.recommendedActions().size(),
        response.additionalChecks().size());

    assertThat(actualCalls.get()).isEqualTo(1);
    assertThat(response.conversationId()).isNotNull();
    assertThat(response.answer()).isNotBlank().hasSizeLessThanOrEqualTo(2000);
    assertThat(response.recommendedActions()).hasSizeBetween(1, 3);
    assertThat(response.recommendedActions())
        .allSatisfy(action -> assertThat(action).isNotBlank().hasSizeLessThanOrEqualTo(300));
    assertThat(response.additionalChecks()).hasSizeLessThanOrEqualTo(3);
    assertThat(response.additionalChecks())
        .allSatisfy(check -> assertThat(check).isNotBlank().hasSizeLessThanOrEqualTo(300));
  }

  private PlantChatService serviceBackedByRealOpenAi(
      OpenAiProperties properties, AtomicInteger actualCalls) {
    OpenAiClient realClient = RealOpenAiClients.create(properties);
    AiClient countingClient =
        request -> {
          actualCalls.incrementAndGet();
          return realClient.generate(request);
        };

    PlantGrowthContextQuery growthContextQuery = mock(PlantGrowthContextQuery.class);
    given(growthContextQuery.getGrowthContext(7L, 21L, 5)).willReturn(growthContext());

    return new PlantChatService(
        growthContextQuery,
        countingClient,
        mock(AiRequestGuard.class),
        new PlantChatConversationStore(FIXED_KST_CLOCK),
        new ObjectMapper(),
        FIXED_KST_CLOCK);
  }

  private PlantGrowthContextResponse growthContext() {
    return new PlantGrowthContextResponse(
        21L,
        "테스트 바질",
        LocalDate.of(2026, 7, 1),
        PlantStatus.GROWING,
        3L,
        "바질",
        "허브",
        "바질은 햇빛이 잘 들고 통풍되는 곳에서 키우며, 겉흙이 마르면 물을 충분히 줍니다.",
        List.of(
            new RecentJournal(31L, LocalDate.of(2026, 8, 8), "새 잎 끝이 조금 말랐습니다."),
            new RecentJournal(30L, LocalDate.of(2026, 8, 5), "물을 충분히 주고 받침의 물을 비웠습니다.")));
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
