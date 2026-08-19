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
    properties.put("scopeDecision", stringEnum("ANSWER", "OTHER_PLANT", "REFUSE", "UNCERTAIN"));
    properties.put(
        "scopeIntent",
        stringEnum(
            "CARE", "GROWTH_OBSERVATION", "JOURNAL_INTERPRETATION", "DIRECT_FOLLOW_UP", "NONE"));
    properties.put("answer", string(PlantChatResponseLimits.MAX_ANSWER_LENGTH));
    properties.put(
        "recommendedActions",
        array(
            string(PlantChatResponseLimits.MAX_LIST_ITEM_LENGTH),
            PlantChatResponseLimits.MAX_RECOMMENDED_ACTIONS));
    properties.put(
        "additionalChecks",
        array(
            string(PlantChatResponseLimits.MAX_LIST_ITEM_LENGTH),
            PlantChatResponseLimits.MAX_ADDITIONAL_CHECKS));

    return new AiJsonSchema(
        "plant_profile_chat",
        "질문의 의미 기반 허용 범위와 선택 식물 일치 여부 판정, 식물 프로필 기반 답변, 권장 행동, 추가 확인사항",
        object(properties));
  }

  private static Map<String, Object> object(Map<String, Object> properties) {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("properties", properties);
    schema.put("required", List.copyOf(properties.keySet()));
    schema.put("additionalProperties", false);
    return schema;
  }

  private static Map<String, Object> array(Map<String, Object> items, int maxItems) {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "array");
    schema.put("items", items);
    schema.put("maxItems", maxItems);
    return schema;
  }

  private static Map<String, Object> string(int maxLength) {
    return Map.of("type", "string", "maxLength", maxLength);
  }

  private static Map<String, Object> stringEnum(String... values) {
    return Map.of("type", "string", "enum", List.of(values));
  }
}
