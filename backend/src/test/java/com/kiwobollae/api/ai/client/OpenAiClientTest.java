package com.kiwobollae.api.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.kiwobollae.api.ai.config.AiImageProperties;
import com.kiwobollae.api.ai.config.OpenAiProperties;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class OpenAiClientTest {

  private static final String BASE_URL = "https://api.openai.test";
  private static final String IMAGE_PUBLIC_BASE_URL = "https://api.kiwor.site";
  private static final String API_KEY = "test-openai-key";

  private MockRestServiceServer server;
  private OpenAiClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    client = new OpenAiClient(builder, new ObjectMapper(), properties(API_KEY), imageUrlResolver());
  }

  @Test
  void sendsStructuredTextRequestAndParsesJsonResult() {
    server
        .expect(requestTo(BASE_URL + "/v1/responses"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer " + API_KEY))
        .andExpect(
            content()
                .json(
                    """
						{
						  "model": "text-model",
						  "input": [
						    {"role": "system", "content": "system prompt"},
						    {"role": "user", "content": [
						      {"type": "input_text", "text": "user prompt"}
						    ]}
						  ],
						  "text": {"format": {
						    "type": "json_schema",
						    "name": "answer",
						    "description": "structured answer",
						    "strict": true,
						    "schema": {
						      "type": "object",
						      "properties": {"answer": {"type": "string"}},
						      "required": ["answer"],
						      "additionalProperties": false
						    }
						  }},
						  "max_output_tokens": 1200,
						  "store": false
						}
						""",
                    JsonCompareMode.LENIENT))
        .andRespond(
            withSuccess(
                """
						{
						  "id": "resp-1",
						  "model": "text-model-resolved",
						  "status": "completed",
						  "output": [{
						    "type": "message",
						    "content": [{
						      "type": "output_text",
                  "text": "{\\\"answer\\\":\\\"healthy\\\"}"
						    }]
						  }]
						}
						""",
                MediaType.APPLICATION_JSON));

    AiResponse response = client.generate(textRequest());

    assertThat(response.responseId()).isEqualTo("resp-1");
    assertThat(response.model()).isEqualTo("text-model-resolved");
    assertThat(response.result().get("answer").stringValue()).isEqualTo("healthy");
    server.verify();
  }

  @Test
  void selectsVisionModelAndSendsImageInput() {
    server
        .expect(requestTo(BASE_URL + "/v1/responses"))
        .andExpect(
            content()
                .json(
                    """
						{
						  "model": "vision-model",
						  "input": [
						    {"role": "system", "content": "system prompt"},
						    {"role": "user", "content": [
						      {"type": "input_text", "text": "user prompt"},
						      {
						        "type": "input_image",
						        "image_url": "https://api.kiwor.site/api/v1/journals/images/1/journal.jpg",
						        "detail": "high"
						      }
						    ]}
						  ]
						}
						""",
                    JsonCompareMode.LENIENT))
        .andRespond(withSuccess(completedResponse(), MediaType.APPLICATION_JSON));

    AiRequest request =
        new AiRequest(
            AiModelRole.VISION,
            "system prompt",
            "user prompt",
            List.of(
                new AiImageInput(
                    "/api/v1/journals/images/1/journal.jpg", AiImageInput.Detail.HIGH)),
            schema());

    client.generate(request);

    server.verify();
  }

  @Test
  void mapsGatewayTimeoutToAiTimeoutError() {
    server
        .expect(requestTo(BASE_URL + "/v1/responses"))
        .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));

    assertThatThrownBy(() -> client.generate(textRequest()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AI_REQUEST_TIMEOUT));
    server.verify();
  }

  @Test
  void mapsProviderFailureWithoutLeakingResponseBody() {
    server
        .expect(requestTo(BASE_URL + "/v1/responses"))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("sensitive provider response"));

    assertThatThrownBy(() -> client.generate(textRequest()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> {
              assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AI_PROVIDER_UNAVAILABLE);
              assertThat(exception.getMessage()).doesNotContain("sensitive provider response");
            });
    server.verify();
  }

  @Test
  void rejectsMalformedStructuredOutput() {
    server
        .expect(requestTo(BASE_URL + "/v1/responses"))
        .andRespond(
            withSuccess(
                """
						{
						  "id": "resp-1",
						  "model": "text-model",
						  "status": "completed",
						  "output": [{
						    "type": "message",
						    "content": [{"type": "output_text", "text": "not-json"}]
						  }]
						}
						""",
                MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.generate(textRequest()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AI_RESPONSE_INVALID));
    server.verify();
  }

  @Test
  void rejectsCallWhenApiKeyIsNotConfigured() {
    OpenAiClient unconfigured =
        new OpenAiClient(
            RestClient.builder(), new ObjectMapper(), properties(""), imageUrlResolver());

    assertThatThrownBy(() -> unconfigured.generate(textRequest()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AI_CONFIGURATION_INVALID));
  }

  private AiRequest textRequest() {
    return new AiRequest(AiModelRole.TEXT, "system prompt", "user prompt", List.of(), schema());
  }

  private AiJsonSchema schema() {
    return new AiJsonSchema(
        "answer",
        "structured answer",
        Map.of(
            "type",
            "object",
            "properties",
            Map.of("answer", Map.of("type", "string")),
            "required",
            List.of("answer"),
            "additionalProperties",
            false));
  }

  private OpenAiProperties properties(String apiKey) {
    return new OpenAiProperties(
        BASE_URL,
        apiKey,
        "text-model",
        "vision-model",
        Duration.ofSeconds(1),
        Duration.ofSeconds(2),
        1200);
  }

  private AiImageUrlResolver imageUrlResolver() {
    return new AiImageUrlResolver(new AiImageProperties(IMAGE_PUBLIC_BASE_URL));
  }

  // OpenAI Responses API의 input_image.detail은 auto/low/high만 허용한다. 상수가 늘어날 때
  // 허용되지 않는 값이 전송되면(→ 400) 여기서 먼저 깨지도록 와이어 값을 고정한다.
  @Test
  void everyDetailMapsToAValueOpenAiAccepts() {
    assertThat(AiImageInput.Detail.values())
        .extracting(AiImageInput.Detail::wireValue)
        .containsExactly("auto", "low", "high");
  }

  private String completedResponse() {
    return """
				{
				  "id": "resp-vision",
				  "model": "vision-model",
				  "status": "completed",
				  "output": [{
				    "type": "message",
                    "content": [{"type": "output_text", "text": "{\\\"answer\\\":\\\"ok\\\"}"}]
				  }]
				}
				""";
  }
}
