package com.kiwobollae.api.ai.guide.dto;

import com.kiwobollae.api.ai.client.AiJsonSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 재배 가이드 AI 응답의 JSON Schema.
 *
 * <p>OpenAI structured outputs를 strict 모드로 쓰므로 두 규칙을 지켜야 한다. 모든 객체에 {@code additionalProperties:
 * false}를 두고, 모든 속성을 {@code required}에 넣는다(선택 필드를 만들 수 없다).
 *
 * <p>배열 길이 제약({@code minItems}/{@code maxItems})은 현재 structured outputs에서 지원한다. 다만 이 가이드는 항목 개수 자체가
 * API 계약이 아니므로 "실패 원인 2~3개" 같은 요구는 프롬프트로 전달한다.
 *
 * <p>속성 순서를 {@link LinkedHashMap}으로 고정한다. {@code Map.of}는 순회 순서가 정해지지 않아 직렬화된 스키마가 실행마다 달라지고, 그러면
 * OpenAI가 같은 스키마를 매번 새로 컴파일한다.
 *
 * <p>필드 이름은 {@link PlantCareGuideContent}와 일치해야 한다 — 응답을 그 record로 바로 역직렬화한다.
 */
public final class PlantCareGuideSchema {

  /** 스키마를 바꾸면 이 값을 올린다. 옛 캐시가 자연히 무효화된다. */
  public static final int VERSION = 1;

  public static final List<String> DIFFICULTY_VALUES = List.of("초급", "중급", "고급");
  public static final List<String> STAGE_NAMES = List.of("파종", "새싹", "성장", "수확");

  private PlantCareGuideSchema() {}

  public static AiJsonSchema create() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("difficulty", enumString(DIFFICULTY_VALUES));
    properties.put("difficultyReason", string());
    properties.put("environment", environment());
    properties.put("stages", array(stage()));
    properties.put("pitfalls", array(pitfall()));
    properties.put("harvestTarget", string());

    return new AiJsonSchema(
        "plant_care_guide", "선택한 식물 종 하나를 가정에서 잘 키우기 위한 재배 가이드", object(properties));
  }

  private static Map<String, Object> environment() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("sunlight", string());
    properties.put("watering", string());
    properties.put("temperature", string());
    return object(properties);
  }

  private static Map<String, Object> stage() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("name", enumString(STAGE_NAMES));
    properties.put("guide", string());
    return object(properties);
  }

  private static Map<String, Object> pitfall() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("problem", string());
    properties.put("action", string());
    return object(properties);
  }

  /** strict 모드는 선택 필드를 허용하지 않으므로 모든 속성을 required로 넣는다. */
  private static Map<String, Object> object(Map<String, Object> properties) {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("properties", properties);
    schema.put("required", List.copyOf(properties.keySet()));
    schema.put("additionalProperties", false);
    return schema;
  }

  private static Map<String, Object> array(Map<String, Object> items) {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "array");
    schema.put("items", items);
    return schema;
  }

  private static Map<String, Object> string() {
    return Map.of("type", "string");
  }

  private static Map<String, Object> enumString(List<String> values) {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "string");
    schema.put("enum", values);
    return schema;
  }
}
