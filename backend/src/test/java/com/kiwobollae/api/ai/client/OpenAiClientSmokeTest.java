package com.kiwobollae.api.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.kiwobollae.api.ai.config.AiConfig;
import com.kiwobollae.api.ai.config.AiImageProperties;
import com.kiwobollae.api.ai.config.OpenAiProperties;
import com.kiwobollae.api.global.exception.BusinessException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Tag("openai-smoke")
class OpenAiClientSmokeTest {

  @Test
  void receivesStructuredTextResponseFromRealOpenAiApi() {
    OpenAiProperties properties = loadOpenAiProperties();
    requireConfigured(properties.apiKey(), "ai.openai.api-key");
    requireConfigured(properties.textModel(), "ai.openai.text-model");
    AtomicInteger providerStatus = new AtomicInteger();
    RestClient.Builder restClientBuilder =
        OpenAiClient.createRestClientBuilder(properties)
            .requestInterceptor(
                (request, body, execution) -> {
                  var response = execution.execute(request, body);
                  providerStatus.set(response.getStatusCode().value());
                  return response;
                });
    OpenAiClient client =
        new OpenAiClient(
            restClientBuilder,
            new ObjectMapper(),
            properties,
            new AiImageUrlResolver(new AiImageProperties("")));

    AiResponse response;
    try {
      response =
          client.generate(
              new AiRequest(
                  AiModelRole.TEXT,
                  "Return only a response that satisfies the supplied JSON schema.",
                  "Set status to ok.",
                  List.of(),
                  new AiJsonSchema(
                      "openai_smoke_response",
                      "Minimal response used to verify the real OpenAI integration.",
                      Map.of(
                          "type",
                          "object",
                          "properties",
                          Map.of("status", Map.of("type", "string", "enum", List.of("ok"))),
                          "required",
                          List.of("status"),
                          "additionalProperties",
                          false))));
    } catch (BusinessException exception) {
      throw new AssertionError(
          "실제 OpenAI 호출 실패: "
              + exception.getErrorCode().name()
              + " (HTTP "
              + providerStatus.get()
              + ")",
          exception);
    }

    assertThat(response.responseId()).isNotBlank();
    assertThat(response.model()).isNotBlank();
    assertThat(response.result().get("status").stringValue()).isEqualTo("ok");
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
