package com.kiwobollae.api.ai.chat.dto;

import com.kiwobollae.api.ai.client.AiJsonSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 식물 프로필 챗봇의 strict structured output 스키마. */
public final class PlantChatSchema {

  private PlantChatSchema() {}

  public static AiJsonSchema create() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("answer", string());
    properties.put("recommendedActions", array(string()));
    properties.put("additionalChecks", array(string()));

    return new AiJsonSchema(
        "plant_profile_chat", "식물 프로필과 최근 성장 기록을 근거로 한 질문 답변, 권장 행동, 추가 확인사항", object(properties));
  }

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
}
