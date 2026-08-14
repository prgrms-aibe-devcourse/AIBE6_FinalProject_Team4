package com.kiwobollae.api.ai.analysis.dto;

import com.kiwobollae.api.ai.client.AiJsonSchema;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 저장된 성장일지 사진 분석의 strict structured output 스키마다. */
public final class JournalImageAnalysisSchema {

  private JournalImageAnalysisSchema() {}

  public static AiJsonSchema create() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put(
        "imageQuality", enumString(enumNames(JournalImageAnalysisResult.ImageQuality.class)));
    properties.put(
        "condition", enumString(enumNames(JournalImageAnalysisResult.PlantCondition.class)));
    properties.put("summary", string());
    properties.put("observations", array(string()));
    properties.put("possibleCauses", array(string()));
    properties.put("recommendedActions", array(string()));
    properties.put("additionalChecks", array(string()));
    return new AiJsonSchema(
        "journal_image_analysis",
        "저장된 성장일지 사진에서 관찰한 특징과 가능한 원인, 관리 행동, 추가 확인사항",
        object(properties));
  }

  private static List<String> enumNames(Class<? extends Enum<?>> enumType) {
    return Arrays.stream(enumType.getEnumConstants()).map(Enum::name).toList();
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

  private static Map<String, Object> enumString(List<String> values) {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "string");
    schema.put("enum", values);
    return schema;
  }
}
