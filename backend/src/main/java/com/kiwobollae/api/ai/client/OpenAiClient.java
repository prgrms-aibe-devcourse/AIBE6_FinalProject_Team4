package com.kiwobollae.api.ai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.kiwobollae.api.ai.config.OpenAiProperties;
import com.kiwobollae.api.global.exception.BusinessException;
import com.kiwobollae.api.global.exception.ErrorCode;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile("!test")
public class OpenAiClient implements AiClient {

  private static final String COMPLETED_STATUS = "completed";
  private static final String MESSAGE_TYPE = "message";
  private static final String OUTPUT_TEXT_TYPE = "output_text";

  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final OpenAiProperties properties;
  private final AiImageUrlResolver imageUrlResolver;

  @Autowired
  public OpenAiClient(
      OpenAiProperties properties, ObjectMapper objectMapper, AiImageUrlResolver imageUrlResolver) {
    this(createRestClientBuilder(properties), objectMapper, properties, imageUrlResolver);
  }

  OpenAiClient(
      RestClient.Builder restClientBuilder,
      ObjectMapper objectMapper,
      OpenAiProperties properties,
      AiImageUrlResolver imageUrlResolver) {
    this.restClient =
        restClientBuilder
            .baseUrl(properties.baseUrl())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    this.objectMapper = objectMapper;
    this.properties = properties;
    this.imageUrlResolver = imageUrlResolver;
  }

  @Override
  public AiResponse generate(AiRequest request) {
    String model = resolveModel(request.modelRole());
    validateConfiguration(model);

    try {
      OpenAiResponse response =
          restClient
              .post()
              .uri("/v1/responses")
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
              .body(createRequestBody(request, model))
              .retrieve()
              .body(OpenAiResponse.class);
      return toAiResponse(response);
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().value() == 408 || exception.getStatusCode().value() == 504) {
        throw new BusinessException(ErrorCode.AI_REQUEST_TIMEOUT);
      }
      throw new BusinessException(ErrorCode.AI_PROVIDER_UNAVAILABLE);
    } catch (RestClientException exception) {
      if (hasTimeoutCause(exception)) {
        throw new BusinessException(ErrorCode.AI_REQUEST_TIMEOUT);
      }
      if (hasCause(exception, HttpMessageNotReadableException.class)) {
        throw invalidResponse();
      }
      throw new BusinessException(ErrorCode.AI_PROVIDER_UNAVAILABLE);
    }
  }

  private Map<String, Object> createRequestBody(AiRequest request, String model) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", model);
    body.put("input", createInput(request));
    body.put("text", Map.of("format", createResponseFormat(request.responseSchema())));
    body.put("max_output_tokens", resolveMaxOutputTokens(request));
    body.put("store", false);
    return body;
  }

  private int resolveMaxOutputTokens(AiRequest request) {
    return request.maxOutputTokens() == null
        ? properties.maxOutputTokens()
        : Math.min(request.maxOutputTokens(), properties.maxOutputTokens());
  }

  private List<Map<String, Object>> createInput(AiRequest request) {
    List<Map<String, Object>> input = new ArrayList<>();
    input.add(Map.of("role", "system", "content", request.systemPrompt()));

    List<Map<String, Object>> userContent = new ArrayList<>();
    userContent.add(Map.of("type", "input_text", "text", request.userPrompt()));
    for (AiImageInput image : request.images()) {
      userContent.add(
          Map.of(
              "type", "input_image",
              "image_url", imageUrlResolver.resolveJournalImageUrl(image.imageUrl()),
              "detail", image.detail().wireValue()));
    }
    input.add(Map.of("role", "user", "content", userContent));
    return input;
  }

  private Map<String, Object> createResponseFormat(AiJsonSchema responseSchema) {
    Map<String, Object> format = new LinkedHashMap<>();
    format.put("type", "json_schema");
    format.put("name", responseSchema.name());
    if (responseSchema.description() != null && !responseSchema.description().isBlank()) {
      format.put("description", responseSchema.description());
    }
    format.put("strict", true);
    format.put("schema", responseSchema.schema());
    return format;
  }

  private AiResponse toAiResponse(OpenAiResponse response) {
    if (response == null || !COMPLETED_STATUS.equals(response.status())) {
      throw invalidResponse();
    }

    String outputText =
        response.output() == null
            ? null
            : response.output().stream()
                .filter(item -> MESSAGE_TYPE.equals(item.type()))
                .filter(item -> item.content() != null)
                .flatMap(item -> item.content().stream())
                .filter(content -> OUTPUT_TEXT_TYPE.equals(content.type()))
                .map(OpenAiOutputContent::text)
                .filter(text -> text != null && !text.isBlank())
                .findFirst()
                .orElse(null);
    if (outputText == null) {
      throw invalidResponse();
    }

    try {
      JsonNode result = objectMapper.readTree(outputText);
      if (result == null || result.isNull()) {
        throw invalidResponse();
      }
      return new AiResponse(response.id(), response.model(), result);
    } catch (BusinessException exception) {
      throw exception;
    } catch (Exception exception) {
      throw invalidResponse();
    }
  }

  private String resolveModel(AiModelRole modelRole) {
    return modelRole == AiModelRole.VISION ? properties.visionModel() : properties.textModel();
  }

  private void validateConfiguration(String model) {
    if (properties.apiKey() == null
        || properties.apiKey().isBlank()
        || model == null
        || model.isBlank()) {
      throw new BusinessException(ErrorCode.AI_CONFIGURATION_INVALID);
    }
  }

  private boolean hasTimeoutCause(Throwable throwable) {
    return hasCause(throwable, HttpTimeoutException.class)
        || hasCause(throwable, SocketTimeoutException.class)
        || hasCause(throwable, TimeoutException.class);
  }

  private boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
    Throwable current = throwable;
    while (current != null) {
      if (causeType.isInstance(current)) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private BusinessException invalidResponse() {
    return new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
  }

  static RestClient.Builder createRestClientBuilder(OpenAiProperties properties) {
    HttpClient httpClient =
        HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(properties.readTimeout());
    return RestClient.builder().requestFactory(requestFactory);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record OpenAiResponse(
      String id, String model, String status, List<OpenAiOutputItem> output) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record OpenAiOutputItem(String type, List<OpenAiOutputContent> content) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record OpenAiOutputContent(String type, String text) {}
}
